import assert from 'node:assert/strict';
import { classifyTask, providerOrderFor, normalizeEffortForProvider, providerCapabilitySnapshot } from './task-router.mjs';

assert.equal(classifyTask('ابحث عن آخر أخبار التقنية'), 'research');
assert.equal(classifyTask('ابني APK بعد إصلاح الكود'), 'code');
assert.equal(classifyTask('حلل الصورة دي'), 'vision');
assert.equal(classifyTask('اعمل فيديو قصير'), 'video');
assert.equal(classifyTask('ترجم الكلام للإنجليزية'), 'language');
assert.equal(classifyTask('ازيك يا Shadow'), 'chat');

assert.equal(providerOrderFor('ازيك', true)[0], 'local');
assert.equal(normalizeEffortForProvider('gemini', 'xhigh'), 'medium');
assert.equal(normalizeEffortForProvider('xai', 'low'), 'low');
assert.equal(normalizeEffortForProvider('openai', 'max'), 'max');

const caps=providerCapabilitySnapshot({
  gemini:{configured:true,model:'gemini-3.8-flash',capabilities:['chat','vision']},
});
assert.ok(Array.isArray(caps));
assert.ok(caps.some(x=>x.provider==='gemini' && x.capabilities.includes('vision')));

console.log('task-router: ok');
