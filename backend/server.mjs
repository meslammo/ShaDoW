import express from 'express';
import helmet from 'helmet';
import cors from 'cors';

const app = express();
const port = Number(process.env.PORT || 8787);
const apiKey = String(process.env.OPENAI_API_KEY || '').trim();
const model = String(process.env.OPENAI_MODEL || 'gpt-5.6-luna').trim();
const systemPrompt = String(process.env.SHADOW_SYSTEM_PROMPT || [
  'You are SHADOW, a personal Android AI assistant.',
  'Answer in the language the user uses. Prefer concise, practical Egyptian Arabic when the user writes Arabic.',
  'Do not claim that a device, home, car, file, message, or external action happened unless the backend has actually confirmed it.',
  'When an action is unavailable, say clearly what is unavailable and why.',
  'Never reveal server secrets, API keys, or internal environment variables.'
].join(' '));

app.disable('x-powered-by');
app.use(helmet());
app.use(cors({ origin: true, methods: ['GET', 'POST', 'OPTIONS'], allowedHeaders: ['Content-Type', 'Authorization'] }));
app.use(express.json({ limit: '256kb' }));

const buckets = new Map();
function rateLimit(req, res, next) {
  const key = req.ip || 'unknown';
  const now = Date.now();
  const windowMs = 60_000;
  const max = 30;
  const old = buckets.get(key);
  if (!old || now - old.started >= windowMs) {
    buckets.set(key, { started: now, count: 1 });
    return next();
  }
  old.count += 1;
  if (old.count > max) return res.status(429).json({ ok: false, error: 'rate_limited' });
  return next();
}

app.get('/health', (_req, res) => {
  res.json({ ok: true, service: 'shadow-cloud', online: Boolean(apiKey), model });
});

app.post('/v1/chat', rateLimit, async (req, res) => {
  if (!apiKey) return res.status(503).json({ ok: false, error: 'backend_not_configured' });
  const message = typeof req.body?.message === 'string' ? req.body.message.trim() : '';
  if (!message) return res.status(400).json({ ok: false, error: 'message_required' });
  if (message.length > 12000) return res.status(413).json({ ok: false, error: 'message_too_large' });

  const previousResponseId = typeof req.body?.previous_response_id === 'string' && req.body.previous_response_id.trim()
    ? req.body.previous_response_id.trim() : undefined;

  const payload = {
    model,
    instructions: systemPrompt,
    input: message,
    store: true
  };
  if (previousResponseId) payload.previous_response_id = previousResponseId;

  try {
    const upstream = await fetch('https://api.openai.com/v1/responses', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${apiKey}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(payload),
      signal: AbortSignal.timeout(45_000)
    });

    const body = await upstream.json().catch(() => ({}));
    if (!upstream.ok) {
      console.error('OpenAI error', upstream.status, JSON.stringify(body).slice(0, 2000));
      return res.status(502).json({ ok: false, error: 'upstream_ai_error' });
    }

    const answer = typeof body.output_text === 'string' ? body.output_text.trim() : extractOutputText(body);
    if (!answer) return res.status(502).json({ ok: false, error: 'empty_ai_response' });

    return res.json({ ok: true, answer, response_id: body.id || null, model: body.model || model });
  } catch (error) {
    console.error('AI request failed', error?.message || error);
    return res.status(502).json({ ok: false, error: 'ai_unreachable' });
  }
});

function extractOutputText(body) {
  const output = Array.isArray(body?.output) ? body.output : [];
  return output.flatMap(item => Array.isArray(item?.content) ? item.content : [])
    .filter(part => part?.type === 'output_text' && typeof part?.text === 'string')
    .map(part => part.text)
    .join('\n')
    .trim();
}

app.listen(port, '0.0.0.0', () => {
  console.log(`SHADOW cloud backend listening on ${port}; model=${model}; configured=${Boolean(apiKey)}`);
});
