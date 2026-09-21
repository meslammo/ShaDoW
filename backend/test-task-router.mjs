import test from 'node:test';
import assert from 'node:assert/strict';
import { classifyTask, normalizeEffortForProvider, providerOrderFor } from './task-router.mjs';

test('task classifier separates code, vision, research and chat', () => {
  assert.equal(classifyTask('حلل كود المشروع').toString(), 'code');
  assert.equal(classifyTask('حلل الصورة').toString(), 'vision');
  assert.equal(classifyTask('ابحث عن آخر أخبار الذكاء الاصطناعي').toString(), 'research');
  assert.equal(classifyTask('عاملني كصديق').toString(), 'chat');
});

test('provider order is task aware', () => {
  assert.equal(providerOrderFor('راجع كود GitHub', false)[0], 'xai');
  assert.equal(providerOrderFor('حلل الصورة', false)[0], 'gemini');
});

test('reasoning effort is normalized per provider', () => {
  assert.equal(normalizeEffortForProvider('gemini', 'xhigh'), 'medium');
  assert.equal(normalizeEffortForProvider('anthropic', 'xhigh'), 'max');
  assert.equal(normalizeEffortForProvider('mistral', 'minimal'), 'low');
});
