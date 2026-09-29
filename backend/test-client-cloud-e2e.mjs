import assert from 'node:assert/strict';
import http from 'node:http';

process.env.SHADOW_START_SERVER = 'false';
process.env.OPENAI_API_KEY = 'client-e2e-test-key';
process.env.OPENAI_MODEL = 'client-e2e-model';
process.env.SHADOW_MEMORY_DIR = '/tmp/shadow-client-e2e-memory';
process.env.SHADOW_AUDIT_DIR = '/tmp/shadow-client-e2e-audit';

const originalFetch = globalThis.fetch;
globalThis.fetch = async (url, options = {}) => {
  if (String(url) === 'https://api.openai.com/v1/responses') {
    const payload = options?.body ? JSON.parse(String(options.body)) : {};
    if (payload.stream) {
      const stream = new ReadableStream({
        start(controller) {
          const events = [
            { type: 'response.output_text.delta', delta: 'SHADOW_CLIENT_STREAM_OK' },
            { type: 'response.completed', response: { id: 'stream-e2e-1' } },
            '[DONE]',
          ];
          for (const item of events) controller.enqueue(new TextEncoder().encode('data: ' + JSON.stringify(item) + '\n\n'));
          controller.close();
        },
      });
      return new Response(stream, { status: 200, headers: { 'Content-Type': 'text/event-stream' } });
    }
    return new Response(JSON.stringify({
      id: 'client-e2e-1',
      output_text: 'SHADOW_CLIENT_E2E_OK',
      output: [],
    }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    });
  }
  return originalFetch(url, options);
};

const { app } = await import('./server.mjs');
const server = http.createServer(app);
await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
const address = server.address();
const base = 'http://127.0.0.1:' + address.port;

async function jsonPost(path, payload) {
  const response = await originalFetch(base + path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
  return { response, body: await response.json() };
}

try {
  const health = await originalFetch(base + '/health');
  assert.equal(health.status, 200);
  assert.equal((await health.json()).capabilities.unified_cloud_pipeline, true);

  const chat = await jsonPost('/v1/chat', { message: 'ping' });
  assert.equal(chat.response.status, 200);
  assert.equal(chat.body.answer, 'SHADOW_CLIENT_E2E_OK');
  assert.equal(chat.body.trace.phases.at(-1).status, 'completed');

  const master = await jsonPost('/v1/master/run', { message: 'ping' });
  assert.equal(master.response.status, 200);
  assert.equal(master.body.ok, true);
  assert.equal(master.body.pipeline.external_device_control, false);

  const stream = await originalFetch(base + '/v1/chat/stream', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
    body: JSON.stringify({ message: 'stream' }),
  });
  assert.equal(stream.status, 200);
  const streamText = await stream.text();
  assert.match(streamText, /SHADOW_CLIENT_STREAM_OK/);
  assert.match(streamText, /"type":"done"/);

  const status = await originalFetch(base + '/v1/pipeline/status');
  assert.equal(status.status, 200);
  const statusBody = await status.json();
  assert.equal(statusBody.online_only_brain, true);
  assert.equal(statusBody.external_device_control, false);
} finally {
  await new Promise(resolve => server.close(resolve));
  globalThis.fetch = originalFetch;
}
