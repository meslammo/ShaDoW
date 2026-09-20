import express from 'express';
import helmet from 'helmet';
import cors from 'cors';
import { applyFiles, createPullRequest, status as developmentStatus } from './development-agent.mjs';
import { startDeviceAuthorization, pollDeviceAuthorization, status as githubOAuthStatus } from './github-oauth.mjs';
import { voiceprintStatus, verifyVoiceprint } from './voiceprint.mjs';
import { runAgent, streamAgent, providerStatus, memoryStatus } from './ai-router.mjs';

const app = express();
const port = Number(process.env.PORT || 8787);
const apiKey = String(process.env.OPENAI_API_KEY || '').trim();
const imageModel = String(process.env.OPENAI_IMAGE_MODEL || 'gpt-image-1').trim();
const ttsModel = String(process.env.SHADOW_TTS_MODEL || 'gpt-4o-mini-tts').trim();
const ttsVoice = String(process.env.SHADOW_TTS_VOICE || 'onyx').trim();
const ttsVoiceId = String(process.env.SHADOW_TTS_VOICE_ID || '').trim();
const ttsInstructions = String(process.env.SHADOW_TTS_INSTRUCTIONS || 'Speak with a refined, cinematic, futuristic British AI-assistant character: deep adult male voice, calm authority, precise diction, restrained emotion, intelligent and composed, slightly warm, measured pacing, subtle dry confidence. This is an original Jarvis-inspired delivery, not an imitation of any actor or copyrighted character performance. When speaking Egyptian Arabic (ar-EG), keep the same deep, polished, controlled delivery while using natural Egyptian pronunciation and vocabulary.').trim();

app.disable('x-powered-by');
app.use(helmet());
app.use(cors({ origin: true, methods: ['GET', 'POST', 'OPTIONS'], allowedHeaders: ['Content-Type', 'Authorization'] }));
app.use(express.json({ limit: '20mb' }));

const buckets = new Map();
function rateLimit(req, res, next) {
  const key = req.ip || 'unknown';
  const now = Date.now();
  const state = buckets.get(key);
  if (!state || now - state.started >= 60000) { buckets.set(key, { started: now, count: 1 }); return next(); }
  state.count++;
  if (state.count > 40) return res.status(429).json({ ok: false, error: 'rate_limited' });
  next();
}

app.get('/health', async (_req, res) => res.json({
  ok: true,
  service: 'shadow-cloud',
  agent: 'unified-multi-ai',
  providers: providerStatus(),
  memory: await memoryStatus(),
  capabilities: {
    web_search: true,
    web_fetch: true,
    streaming_chat: true,
    streaming_provider: 'openai',
    github_read: true,
    github_write_gateway: githubOAuthStatus().configured,
    files: true,
    android_action: true,
    agent_loop: true,
    voiceprint_required: false,
  },
  image_generation: Boolean(apiKey),
  tts: {
    model: ttsModel,
    voice: ttsVoice,
    voice_id_configured: Boolean(ttsVoiceId),
    mode: ttsVoiceId ? 'custom' : 'built_in',
    style: 'jarvis-inspired-original',
    locale: 'ar-EG',
    gender: 'male',
  },
  development_agent: developmentStatus(),
  github_authorization: githubOAuthStatus(),
  voiceprint: { required: false, status: voiceprintStatus() },
}));

app.post('/v1/chat', rateLimit, async (req, res) => {
  const message = typeof req.body?.message === 'string' ? req.body.message.trim() : '';
  if (!message) return res.status(400).json({ ok: false, error: 'message_required' });
  if (message.length > 12000) return res.status(413).json({ ok: false, error: 'message_too_large' });
  const previous = typeof req.body?.previous_response_id === 'string' ? req.body.previous_response_id.trim() : '';
  const device = typeof req.body?.device === 'string' ? req.body.device.slice(0, 16000) : '';
  const providerInput = String(req.body?.provider || '').toLowerCase();
  const reasoningInput = String(req.body?.reasoning_effort || 'none').toLowerCase();
  const reasoningEffort = ['none','minimal','low','medium','high','xhigh'].includes(reasoningInput) ? reasoningInput : 'none';
  const allowedProviders = ['openai', 'xai', 'grok', 'deepseek', 'mistral', 'anthropic', 'gemini'];
  const preferred = allowedProviders.includes(providerInput) ? providerInput.replace('grok', 'xai') : 'auto';
  try {
    const result = await runAgent({ message, previousResponseId: previous, device, preferredProvider: preferred, reasoningEffort });
    return res.json({ ok: true, answer: result.answer, response_id: result.responseId || null, provider: result.provider, model: result.model, reasoning_effort: result.reasoningEffort || reasoningEffort, used_web_search: Boolean(result.usedWeb), pending_action: result.pendingAction || null, attempts: result.attempts || [] });
  } catch (error) {
    console.error('Unified agent failed', String(error?.message || error));
    return res.status(503).json({ ok: false, error: 'agent_failed' });
  }
});

app.post('/v1/chat/stream', rateLimit, async (req, res) => {
  const message = typeof req.body?.message === 'string' ? req.body.message.trim() : '';
  if (!message) return res.status(400).json({ ok: false, error: 'message_required' });
  if (message.length > 12000) return res.status(413).json({ ok: false, error: 'message_too_large' });
  const previous = typeof req.body?.previous_response_id === 'string' ? req.body.previous_response_id.trim() : '';
  const device = typeof req.body?.device === 'string' ? req.body.device.slice(0, 16000) : '';
  const reasoningInput = String(req.body?.reasoning_effort || 'none').toLowerCase();
  const reasoningEffort = ['none','minimal','low','medium','high','xhigh'].includes(reasoningInput) ? reasoningInput : 'none';
  res.statusCode = 200;
  res.set({
    'Content-Type': 'text/event-stream; charset=utf-8',
    'Cache-Control': 'no-cache, no-transform',
    'Connection': 'keep-alive',
    'X-Accel-Buffering': 'no',
  });
  if (typeof res.flushHeaders === 'function') res.flushHeaders();
  const send = (payload) => { if (!res.writableEnded) res.write('data: ' + JSON.stringify(payload) + '\n\n'); };
  try {
    const result = await streamAgent({
      message,
      previousResponseId: previous,
      device,
      reasoningEffort,
      onDelta: (text) => send({ type: 'delta', text }),
      onPending: (data) => send({ type: 'pending_action', ...data }),
      onDone: (data) => send({ type: 'done', ...data }),
    });
    if (!res.writableEnded) {
      if (!result.pendingAction && result.responseId && !result.provider) send({ type: 'done', ...result });
      send({ type: 'eof' });
      res.end();
    }
  } catch (error) {
    console.error('Streaming agent failed', String(error?.message || error));
    send({ type: 'error', error: String(error?.message || 'agent_failed') });
    send({ type: 'eof' });
    if (!res.writableEnded) res.end();
  }
});

app.post('/v1/agent/continue', rateLimit, async (req, res) => {
  const provider = String(req.body?.provider || '').toLowerCase();
  const responseId = String(req.body?.response_id || '').trim();
  const toolCallId = String(req.body?.tool_call_id || '').trim();
  const output = typeof req.body?.output === 'string' ? req.body.output.slice(0, 20000) : JSON.stringify(req.body?.output ?? '');
  const reasoningInput = String(req.body?.reasoning_effort || 'none').toLowerCase();
  const reasoningEffort = ['none','minimal','low','medium','high','xhigh'].includes(reasoningInput) ? reasoningInput : 'none';
  if (!provider || !toolCallId) return res.status(400).json({ ok: false, error: 'tool_context_required' });
  const original = String(req.body?.original_message || 'نفّذ الإجراء المطلوب واستكمل.');
  try {
    const result = await runAgent({ message: `${original}\n[DEVICE_TOOL_RESULT]\n${output}`, previousResponseId: responseId, preferredProvider: provider, reasoningEffort });
    return res.json({ ok: true, answer: result.answer, response_id: result.responseId || null, provider: result.provider, model: result.model, reasoning_effort: result.reasoningEffort || reasoningEffort, used_web_search: Boolean(result.usedWeb), pending_action: result.pendingAction || null });
  } catch { return res.status(503).json({ ok: false, error: 'agent_continue_failed' }); }
});

app.post('/v1/github/device/start', rateLimit, async (_req, res) => {
  try { res.json({ ok: true, ...await startDeviceAuthorization() }); }
  catch (e) { const message = String(e?.message || 'github_oauth_failed'); res.status(message === 'github_oauth_not_configured' ? 503 : 502).json({ ok: false, error: message }); }
});
app.post('/v1/github/device/poll', rateLimit, async (req, res) => {
  try {
    const code = typeof req.body?.device_code === 'string' ? req.body.device_code.trim() : '';
    if (!code) return res.status(400).json({ ok: false, error: 'device_code_required' });
    res.json({ ok: true, ...await pollDeviceAuthorization(code) });
  } catch (e) { res.status(502).json({ ok: false, error: String(e?.message || 'github_oauth_failed') }); }
});

app.post('/v1/voiceprint/verify', rateLimit, async (req, res) => {
  try {
    const audio = typeof req.body?.audio_base64 === 'string' ? req.body.audio_base64.trim() : '';
    const contentType = typeof req.body?.content_type === 'string' ? req.body.content_type.slice(0, 80) : 'audio/wav';
    const result = await verifyVoiceprint(audio, contentType);
    if (result.verified !== true) return res.status(401).json({ ok: false, ...result });
    res.json({ ok: true, ...result });
  } catch (e) {
    const message = String(e?.message || 'voiceprint_failed');
    res.status(message === 'voiceprint_provider_not_configured' ? 503 : 400).json({ ok: false, error: message });
  }
});

app.post('/v1/development/plan', rateLimit, async (req, res) => {
  const request = typeof req.body?.request === 'string' ? req.body.request.trim() : '';
  const project = typeof req.body?.project === 'string' ? req.body.project.slice(0, 12000) : '';
  if (!request) return res.status(400).json({ ok: false, error: 'request_required' });
  try {
    const result = await runAgent({ message: `Create a safe software implementation plan only. Do not claim edits executed. Request: ${request}\nProject: ${project}`, preferredProvider: 'auto' });
    res.json({ ok: true, plan: result.answer, approval_required: true, execution_available: developmentStatus().configured });
  } catch { res.status(503).json({ ok: false, error: 'development_ai_unavailable' }); }
});


app.post('/v1/development/pull-request', rateLimit, async (req, res) => {
  if (req.body?.approved !== true) return res.status(403).json({ ok: false, error: 'explicit_approval_required' });
  const branch = typeof req.body?.branch === 'string' && /^[A-Za-z0-9._/-]{1,80}$/.test(req.body.branch) ? req.body.branch : '';
  const title = typeof req.body?.title === 'string' ? req.body.title.trim().slice(0, 200) : '';
  const body = typeof req.body?.body === 'string' ? req.body.body.slice(0, 10000) : '';
  if (!branch || !title) return res.status(400).json({ ok: false, error: 'branch_and_title_required' });
  try {
    const result = await createPullRequest({ branch, title, body, draft: req.body?.draft !== false, token: typeof req.body?.github_token === 'string' ? req.body.github_token.trim() : '' });
    res.json({ ok: true, executed: true, result });
  } catch (e) {
    const message = String(e?.message || 'pull_request_failed');
    res.status(message === 'github_write_not_configured' ? 503 : 400).json({ ok: false, error: message });
  }
});
app.post('/v1/development/apply', rateLimit, async (req, res) => {
  if (req.body?.approved !== true) return res.status(403).json({ ok: false, error: 'explicit_approval_required' });
  const branch = typeof req.body?.branch === 'string' && /^[A-Za-z0-9._/-]{1,80}$/.test(req.body.branch) ? req.body.branch : 'shadow-agent-work';
  const message = typeof req.body?.commit_message === 'string' && req.body.commit_message.trim() ? req.body.commit_message.trim().slice(0, 160) : 'SHADOW Development Agent change';
  try {
    const result = await applyFiles({ branch, message, files: req.body?.files, token: typeof req.body?.github_token === 'string' ? req.body.github_token.trim() : '' });
    res.json({ ok: true, executed: true, result });
  } catch (e) {
    const messageOut = String(e?.message || 'github_write_failed');
    res.status(messageOut === 'github_write_not_configured' ? 503 : 400).json({ ok: false, error: messageOut });
  }
});

app.post('/v1/images', rateLimit, async (req, res) => {
  if (!apiKey) return res.status(503).json({ ok: false, error: 'image_provider_not_configured' });
  const prompt = typeof req.body?.prompt === 'string' ? req.body.prompt.trim() : '';
  if (!prompt) return res.status(400).json({ ok: false, error: 'prompt_required' });
  if (prompt.length > 8000) return res.status(413).json({ ok: false, error: 'prompt_too_large' });
  try {
    const response = await fetch('https://api.openai.com/v1/images/generations', { method: 'POST', headers: { Authorization: `Bearer ${apiKey}`, 'Content-Type': 'application/json' }, body: JSON.stringify({ model: imageModel, prompt, size: '1024x1024' }), signal: AbortSignal.timeout(120000) });
    const body = await response.json().catch(() => ({}));
    if (!response.ok) return res.status(502).json({ ok: false, error: 'image_upstream_error' });
    const data = body?.data?.[0]?.b64_json;
    if (typeof data !== 'string') return res.status(502).json({ ok: false, error: 'empty_image' });
    res.json({ ok: true, image_base64: data, model: imageModel });
  } catch { res.status(502).json({ ok: false, error: 'image_unreachable' }); }
});

app.post('/v1/speech', rateLimit, async (req, res) => {
  if (!apiKey) return res.status(503).json({ ok: false, error: 'speech_provider_not_configured' });
  const input = typeof req.body?.input === 'string' ? req.body.input.trim() : '';
  if (!input) return res.status(400).json({ ok: false, error: 'input_required' });
  if (input.length > 4096) return res.status(413).json({ ok: false, error: 'input_too_large' });
  try {
    const response = await fetch('https://api.openai.com/v1/audio/speech', { method: 'POST', headers: { Authorization: `Bearer ${apiKey}`, 'Content-Type': 'application/json' }, body: JSON.stringify({ model: ttsModel, voice: ttsVoiceId ? { id: ttsVoiceId } : ttsVoice, input, instructions: ttsInstructions, response_format: 'mp3' }), signal: AbortSignal.timeout(60000) });
    if (!response.ok) return res.status(502).json({ ok: false, error: 'speech_upstream_error' });
    res.set('Content-Type', 'audio/mpeg').set('Cache-Control', 'no-store').set('X-SHADOW-TTS-Mode', ttsVoiceId ? 'custom' : 'built-in').set('X-SHADOW-TTS-Style', 'jarvis-inspired-original');
    res.send(Buffer.from(await response.arrayBuffer()));
  } catch { res.status(502).json({ ok: false, error: 'speech_unreachable' }); }
});

app.listen(port, '0.0.0.0', () => console.log(`SHADOW cloud backend listening on ${port}; agent=unified-multi-ai; providers=${JSON.stringify(providerStatus())}`));
