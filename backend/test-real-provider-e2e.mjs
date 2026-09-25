import assert from 'node:assert/strict';
import { runAgent } from './ai-router.mjs';

const provider = String(process.env.SHADOW_E2E_PROVIDER || 'openai').toLowerCase();
const keys = {
  openai: process.env.OPENAI_API_KEY,
  xai: process.env.XAI_API_KEY,
  deepseek: process.env.DEEPSEEK_API_KEY,
  mistral: process.env.MISTRAL_API_KEY,
  anthropic: process.env.ANTHROPIC_API_KEY,
  gemini: process.env.GEMINI_API_KEY,
};
const key = String(keys[provider] || '').trim();
if (!key) {
  console.error('REAL_PROVIDER_E2E_NOT_RUN: no credential exposed for ' + provider);
  process.exitCode = 2;
} else {
  const result = await runAgent({
    message: 'Return the exact marker SHADOW_REAL_E2E_OK and nothing else.',
    preferredProvider: provider,
    reasoningEffort: 'none',
  });
  assert.equal(result.provider, provider);
  assert.ok(String(result.answer || '').includes('SHADOW_REAL_E2E_OK'));
  console.log('REAL_PROVIDER_E2E_OK provider=' + provider + ' model=' + String(result.model || 'unknown'));
}
