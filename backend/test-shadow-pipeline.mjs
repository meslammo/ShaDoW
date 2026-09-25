import assert from 'node:assert/strict';
import { tmpdir } from 'node:os';
import path from 'node:path';
import test from 'node:test';

process.env.OPENAI_API_KEY = 'test-provider-key';
process.env.OPENAI_MODEL = 'e2e-test-model';
process.env.SHADOW_MEMORY_DIR = path.join(tmpdir(), 'shadow-pipeline-test');
process.env.SHADOW_AUDIT_DIR = path.join(tmpdir(), 'shadow-pipeline-audit');

const originalFetch = globalThis.fetch;
let providerCalls = 0;
globalThis.fetch = async (url, options = {}) => {
  if (String(url) === 'https://api.openai.com/v1/responses') {
    providerCalls += 1;
    const body = providerCalls === 1
      ? {
          id: 'resp-tool-1',
          output: [{
            type: 'function_call',
            name: 'calculator',
            call_id: 'calc-1',
            arguments: JSON.stringify({ expression: '7*6' }),
          }],
        }
      : {
          id: 'resp-final-1',
          output_text: 'SHADOW_PIPELINE_OK: 42',
          output: [],
        };
    return new Response(JSON.stringify(body), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    });
  }
  return originalFetch(url, options);
};

test.after(() => {
  globalThis.fetch = originalFetch;
});

test('unified pipeline runs online brain -> memory -> governed tool -> verify -> deliver', async () => {
  const { runUnifiedPipeline } = await import('./shadow-pipeline.mjs');
  const result = await runUnifiedPipeline({
    message: 'احسب 7*6 واكتب النتيجة',
    preferredProvider: 'openai',
  });

  assert.equal(result.answer, 'SHADOW_PIPELINE_OK: 42');
  assert.equal(result.provider, 'openai');
  assert.equal(result.trace.trace_id.length, 24);
  assert.equal(result.trace.phases[0].status, 'executed');
  assert.equal(result.trace.phases[1].status, 'loaded');
  assert.equal(result.trace.phases[2].status, 'policy_checked');
  assert.equal(result.trace.phases[3].status, 'executed');
  assert.equal(result.trace.phases[4].status, 'executed');
  assert.equal(result.trace.phases[4].count, 1);
  assert.equal(result.trace.phases[5].status, 'response_verified');
  assert.equal(result.trace.phases[6].status, 'completed');
  assert.deepEqual(result.trace.tool_events[0], { name: 'calculator', kind: 'result', ok: true });
  assert.equal(providerCalls, 2);
});
