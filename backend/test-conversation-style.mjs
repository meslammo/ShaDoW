import test from 'node:test';
import assert from 'node:assert/strict';
import { buildSystemPrompt, DEFAULT_SHADOW_SYSTEM_PROMPT } from './conversation-style.mjs';

test('default contract tells SHADOW to answer greetings naturally and briefly', () => {
  const prompt = buildSystemPrompt();
  assert.match(prompt, /For greetings, thanks, acknowledgements, and casual conversation, reply naturally and briefly/);
  assert.match(prompt, /do not start an execution workflow/);
});

test('default contract hides internal reasoning and unsolicited plans', () => {
  const prompt = buildSystemPrompt();
  assert.match(prompt, /Do not expose internal reasoning/);
  assert.match(prompt, /Give a plan only when the user asks for one/);
});

test('custom deployment prompt is preserved while the stable contract remains appended', () => {
  const prompt = buildSystemPrompt('CUSTOM DEPLOYMENT POLICY');
  assert.ok(prompt.startsWith('CUSTOM DEPLOYMENT POLICY\n\n'));
  assert.ok(prompt.includes(DEFAULT_SHADOW_SYSTEM_PROMPT));
});

test('blank custom prompt still returns the default contract', () => {
  assert.equal(buildSystemPrompt('  '), DEFAULT_SHADOW_SYSTEM_PROMPT);
});
