import express from 'express';
import helmet from 'helmet';
import cors from 'cors';

const app = express();
const port = Number(process.env.PORT || 8787);
const apiKey = String(process.env.OPENAI_API_KEY || '').trim();
const model = String(process.env.OPENAI_MODEL || 'gpt-5.6-luna').trim();
const imageModel = String(process.env.OPENAI_IMAGE_MODEL || 'gpt-image-2').trim();
const ttsModel = String(process.env.SHADOW_TTS_MODEL || 'gpt-4o-mini-tts').trim();
const ttsVoice = String(process.env.SHADOW_TTS_VOICE || 'onyx').trim();
const systemPrompt = String(process.env.SHADOW_SYSTEM_PROMPT || [
  'You are SHADOW, a personal Android AI assistant. Your short name is Z when the user asks for your name.',
  'Answer in the language the user uses. Prefer concise, practical Egyptian Arabic when the user writes Arabic.',
  'The Android client may send a device profile. Use it only to tailor compatible instructions and installed-app actions.',
  'When the user asks for current information, websites, images, videos, prices, news, or other fresh facts, use the web search tool before answering.',
  'When the user asks to create or design an image, use the image-generation endpoint rather than pretending an image exists.',
  'Do not claim that a device, home, car, file, message, call, or external action happened unless the client/backend has actually confirmed it.',
  'Dangerous actions and irreversible communications require explicit confirmation. Never reveal server secrets or API keys.'
].join(' '));

app.disable('x-powered-by');
app.use(helmet());
app.use(cors({ origin: true, methods: ['GET', 'POST', 'OPTIONS'], allowedHeaders: ['Content-Type', 'Authorization'] }));
app.use(express.json({ limit: '20mb' }));

const buckets = new Map();
function rateLimit(req, res, next) {
  const key = req.ip || 'unknown'; const now = Date.now(); const windowMs = 60_000; const max = 30; const old = buckets.get(key);
  if (!old || now - old.started >= windowMs) { buckets.set(key, { started: now, count: 1 }); return next(); }
  old.count += 1; if (old.count > max) return res.status(429).json({ ok: false, error: 'rate_limited' }); return next();
}

app.get('/health', (_req, res) => res.json({ ok: true, service: 'shadow-cloud', online: Boolean(apiKey), model, image_model: imageModel, tts_model: ttsModel, tts_voice: ttsVoice, web_search: true }));

app.post('/v1/chat', rateLimit, async (req, res) => {
  if (!apiKey) return res.status(503).json({ ok: false, error: 'backend_not_configured' });
  const message = typeof req.body?.message === 'string' ? req.body.message.trim() : '';
  if (!message) return res.status(400).json({ ok: false, error: 'message_required' });
  if (message.length > 12000) return res.status(413).json({ ok: false, error: 'message_too_large' });
  const previousResponseId = typeof req.body?.previous_response_id === 'string' && req.body.previous_response_id.trim() ? req.body.previous_response_id.trim() : undefined;
  const device = typeof req.body?.device === 'string' ? req.body.device.slice(0, 16000) : '';
  const payload = { model, instructions: systemPrompt, input: device ? `${message}\n\n[DEVICE_PROFILE]\n${device}` : message, tools: [{ type: 'web_search_preview' }], store: true };
  if (previousResponseId) payload.previous_response_id = previousResponseId;
  try {
    const upstream = await fetch('https://api.openai.com/v1/responses', { method: 'POST', headers: { 'Authorization': `Bearer ${apiKey}`, 'Content-Type': 'application/json' }, body: JSON.stringify(payload), signal: AbortSignal.timeout(55_000) });
    const body = await upstream.json().catch(() => ({}));
    if (!upstream.ok) { console.error('OpenAI error', upstream.status, JSON.stringify(body).slice(0, 3000)); return res.status(502).json({ ok: false, error: 'upstream_ai_error' }); }
    const answer = typeof body.output_text === 'string' ? body.output_text.trim() : extractOutputText(body);
    if (!answer) return res.status(502).json({ ok: false, error: 'empty_ai_response' });
    return res.json({ ok: true, answer, response_id: body.id || null, model: body.model || model, used_web_search: hasWebSearch(body) });
  } catch (error) { console.error('AI request failed', error?.message || error); return res.status(502).json({ ok: false, error: 'ai_unreachable' }); }
});

app.post('/v1/images', rateLimit, async (req, res) => {
  if (!apiKey) return res.status(503).json({ ok: false, error: 'backend_not_configured' });
  const prompt = typeof req.body?.prompt === 'string' ? req.body.prompt.trim() : '';
  if (!prompt) return res.status(400).json({ ok: false, error: 'prompt_required' });
  if (prompt.length > 8000) return res.status(413).json({ ok: false, error: 'prompt_too_large' });
  try {
    const upstream = await fetch('https://api.openai.com/v1/images/generations', { method: 'POST', headers: { 'Authorization': `Bearer ${apiKey}`, 'Content-Type': 'application/json' }, body: JSON.stringify({ model: imageModel, prompt, size: '1024x1024', output_format: 'png' }), signal: AbortSignal.timeout(120_000) });
    const body = await upstream.json().catch(() => ({}));
    if (!upstream.ok) { console.error('Image API error', upstream.status, JSON.stringify(body).slice(0, 3000)); return res.status(502).json({ ok: false, error: 'image_upstream_error' }); }
    const b64 = body?.data?.[0]?.b64_json;
    if (typeof b64 !== 'string' || !b64) return res.status(502).json({ ok: false, error: 'empty_image' });
    return res.json({ ok: true, image_base64: b64, model: imageModel });
  } catch (error) { console.error('Image request failed', error?.message || error); return res.status(502).json({ ok: false, error: 'image_unreachable' }); }
});

app.post('/v1/speech', rateLimit, async (req, res) => {
  if (!apiKey) return res.status(503).json({ ok: false, error: 'backend_not_configured' });
  const input = typeof req.body?.input === 'string' ? req.body.input.trim() : '';
  if (!input) return res.status(400).json({ ok: false, error: 'input_required' });
  if (input.length > 4096) return res.status(413).json({ ok: false, error: 'input_too_large' });
  try {
    const upstream = await fetch('https://api.openai.com/v1/audio/speech', { method: 'POST', headers: { 'Authorization': `Bearer ${apiKey}`, 'Content-Type': 'application/json' }, body: JSON.stringify({ model: ttsModel, voice: ttsVoice, input, instructions: 'Speak Arabic Egyptian naturally with a confident deep adult male assistant voice. Clear, calm, direct, not feminine.', response_format: 'mp3' }), signal: AbortSignal.timeout(60_000) });
    if (!upstream.ok) { const body = await upstream.text().catch(() => ''); console.error('Speech API error', upstream.status, body.slice(0, 2000)); return res.status(502).json({ ok: false, error: 'speech_upstream_error' }); }
    const buffer = Buffer.from(await upstream.arrayBuffer());
    res.set('Content-Type', 'audio/mpeg'); res.set('Cache-Control', 'no-store'); return res.send(buffer);
  } catch (error) { console.error('Speech request failed', error?.message || error); return res.status(502).json({ ok: false, error: 'speech_unreachable' }); }
});

function hasWebSearch(body) { const output = Array.isArray(body?.output) ? body.output : []; return output.some(item => item?.type === 'web_search_call'); }
function extractOutputText(body) { const output = Array.isArray(body?.output) ? body.output : []; return output.flatMap(item => Array.isArray(item?.content) ? item.content : []).filter(part => part?.type === 'output_text' && typeof part?.text === 'string').map(part => part.text).join('\n').trim(); }
app.listen(port, '0.0.0.0', () => console.log(`SHADOW cloud backend listening on ${port}; model=${model}; image=${imageModel}; tts=${ttsModel}/${ttsVoice}; configured=${Boolean(apiKey)}`));
