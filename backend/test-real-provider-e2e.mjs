import assert from 'node:assert/strict';
import { runAgent } from './ai-router.mjs';

const preferred = String(process.env.SHADOW_E2E_PROVIDER || 'pollinations').toLowerCase();
const result = await runAgent({
  message: 'Return the exact marker SHADOW_REAL_PROVIDER_OK and nothing else.',
  preferredProvider: preferred,
  reasoningEffort: 'none',
});

assert.ok(result.provider);
assert.ok(String(result.answer || '').includes('SHADOW_REAL_PROVIDER_OK'));
console.log('REAL_PROVIDER_E2E_OK provider=' + result.provider + ' model=' + String(result.model || 'unknown'));
