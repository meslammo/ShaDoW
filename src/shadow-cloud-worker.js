const SYSTEM_PROMPT = [
  'You are SHADOW, a personal Android AI assistant.',
  'Answer in the language the user uses. Prefer concise, practical Egyptian Arabic when the user writes Arabic.',
  'Do not claim that a device, home, car, file, message, or external action happened unless it was actually confirmed.',
  'When an action is unavailable, say clearly what is unavailable and why.',
  'Never reveal server secrets, API keys, or internal environment variables.'
].join(' ');

const RATE_WINDOW_MS = 60_000;
const RATE_MAX = 30;
const buckets = new Map();

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (request.method === 'OPTIONS') {
      return new Response(null, { status: 204, headers: corsHeaders() });
    }

    if (url.pathname === '/health' && request.method === 'GET') {
      return json({
        ok: true,
        service: 'shadow-cloud',
        online: Boolean(env.OPENAI_API_KEY),
        model: env.OPENAI_MODEL || 'gpt-5.6-luna'
      });
    }

    if (url.pathname !== '/v1/chat' || request.method !== 'POST') {
      return json({ ok: false, error: 'not_found' }, 404);
    }

    if (!allowRequest(request)) {
      return json({ ok: false, error: 'rate_limited' }, 429);
    }

    if (!env.OPENAI_API_KEY) {
      return json({ ok: false, error: 'backend_not_configured' }, 503);
    }

    let body;
    try {
      body = await request.json();
    } catch {
      return json({ ok: false, error: 'invalid_json' }, 400);
    }

    const message = typeof body?.message === 'string' ? body.message.trim() : '';
    if (!message) return json({ ok: false, error: 'message_required' }, 400);
    if (message.length > 12000) return json({ ok: false, error: 'message_too_large' }, 413);

    const previousResponseId = typeof body?.previous_response_id === 'string' && body.previous_response_id.trim()
      ? body.previous_response_id.trim()
      : undefined;

    const payload = {
      model: env.OPENAI_MODEL || 'gpt-5.6-luna',
      instructions: env.SHADOW_SYSTEM_PROMPT || SYSTEM_PROMPT,
      input: message,
      store: true
    };
    if (previousResponseId) payload.previous_response_id = previousResponseId;

    try {
      const upstream = await fetch('https://api.openai.com/v1/responses', {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${env.OPENAI_API_KEY}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(payload),
        signal: AbortSignal.timeout(45_000)
      });

      const responseBody = await upstream.json().catch(() => ({}));
      if (!upstream.ok) {
        console.error('OpenAI error', upstream.status);
        return json({ ok: false, error: 'upstream_ai_error' }, 502);
      }

      const answer = typeof responseBody.output_text === 'string'
        ? responseBody.output_text.trim()
        : extractOutputText(responseBody);

      if (!answer) return json({ ok: false, error: 'empty_ai_response' }, 502);

      return json({
        ok: true,
        answer,
        response_id: responseBody.id || null,
        model: responseBody.model || env.OPENAI_MODEL || 'gpt-5.6-luna'
      });
    } catch (error) {
      console.error('AI request failed', error?.message || error);
      return json({ ok: false, error: 'ai_unreachable' }, 502);
    }
  }
};

function allowRequest(request) {
  const forwarded = request.headers.get('CF-Connecting-IP') || request.headers.get('X-Forwarded-For') || 'unknown';
  const key = forwarded.split(',')[0].trim();
  const now = Date.now();
  const current = buckets.get(key);

  if (!current || now - current.started >= RATE_WINDOW_MS) {
    buckets.set(key, { started: now, count: 1 });
    return true;
  }

  current.count += 1;
  return current.count <= RATE_MAX;
}

function extractOutputText(body) {
  const output = Array.isArray(body?.output) ? body.output : [];
  return output.flatMap(item => Array.isArray(item?.content) ? item.content : [])
    .filter(part => part?.type === 'output_text' && typeof part?.text === 'string')
    .map(part => part.text)
    .join('\n')
    .trim();
}

function corsHeaders() {
  return {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type, Authorization',
    'Cache-Control': 'no-store'
  };
}

function json(value, status = 200) {
  return new Response(JSON.stringify(value), {
    status,
    headers: {
      'Content-Type': 'application/json; charset=utf-8',
      ...corsHeaders()
    }
  });
}
