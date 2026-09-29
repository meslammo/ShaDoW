import test from 'node:test';
import assert from 'node:assert/strict';
import { externalProviderConfig, externalProviderStatus, localProviderStatus } from './external-providers.mjs';

test('external provider registry is present', () => {
  assert.deepEqual(Object.keys(externalProviderConfig).sort(), ['anthropic','gemini','mistral']);
});

test('provider status exposes configuration without secret values', () => {
  const status = externalProviderStatus({
    MISTRAL_API_KEY:'secret-a',
    ANTHROPIC_API_KEY:'',
    GEMINI_API_KEY:'secret-b',
    MISTRAL_MODEL:'m',
    GEMINI_MODEL:'g',
  });
  assert.equal(status.mistral.configured, true);
  assert.equal(status.anthropic.configured, false);
  assert.equal(status.gemini.configured, true);
  assert.equal(status.mistral.model, 'm');
  assert.equal(status.gemini.model, 'g');
  assert.equal(JSON.stringify(status).includes('secret-a'), false);
  assert.equal(JSON.stringify(status).includes('secret-b'), false);
});

test('local provider becomes configured only when a base URL exists', () => {
  assert.equal(localProviderStatus({}).configured, false);
  assert.equal(localProviderStatus({SHADOW_LOCAL_AI_BASE_URL:'http://example.test/v1'}).configured, true);
});
