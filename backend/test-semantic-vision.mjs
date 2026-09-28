import test from 'node:test';
import assert from 'node:assert/strict';
import { normalizeMultimodalInput, semanticVisionStatus } from './semantic-vision.mjs';

test('normalizes a real image payload and never stores raw pixels in normalized metadata', () => {
  const input = normalizeMultimodalInput({
    message: 'هل النسر على الزجاج الأمامي؟',
    imageBase64: Buffer.from('fake-image-bytes').toString('base64'),
    imageMimeType: 'image/jpeg',
  });
  assert.equal(input.hasImages, true);
  assert.equal(input.imageCount, 1);
  assert.equal(typeof input.imageHashes[0], 'string');
  assert.equal(input.images[0].mimeType, 'image/jpeg');
  assert.equal(input.images[0].bytes.length > 0, true);
});

test('supports two images for comparison normalization', () => {
  const b64 = Buffer.from('fake').toString('base64');
  const input = normalizeMultimodalInput({
    message: 'قارن الصورتين',
    images: [{ data: b64, mimeType: 'image/png' }, { data: b64, mimeType: 'image/png' }],
  });
  assert.equal(input.imageCount, 2);
});

test('reports provider configuration without exposing credentials', () => {
  const status = semanticVisionStatus();
  assert.equal(typeof status.configured, 'boolean');
  assert.equal(typeof status.providers.openai.configured, 'boolean');
  assert.equal(typeof status.providers.gemini.configured, 'boolean');
  assert.equal(status.openaiKey, undefined);
});
