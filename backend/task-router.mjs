const TASKS = Object.freeze({
  chat: ['local','gemini','mistral','deepseek','openai','xai','anthropic'],
  research: ['gemini','xai','openai','deepseek','mistral','anthropic','local'],
  code: ['xai','openai','anthropic','mistral','gemini','deepseek','local'],
  vision: ['gemini','openai','mistral','anthropic','xai','local'],
  language: ['gemini','mistral','anthropic','openai','deepseek','xai','local'],
  video: ['gemini','xai','openai','local'],
});

const CAPABILITIES = Object.freeze({
  local: ['chat','reasoning','tools'],
  gemini: ['chat','reasoning','vision','audio','video','pdf','image-generation','video-generation','tools'],
  mistral: ['chat','reasoning','vision','tools'],
  deepseek: ['chat','reasoning','tools'],
  openai: ['chat','reasoning','vision','audio','tools','image-generation'],
  xai: ['chat','reasoning','vision','tools','web-search'],
  anthropic: ['chat','reasoning','vision','tools'],
});

export function classifyTask(message='') {
  const s=String(message||'').toLowerCase();
  if (/(صورة|صور|image|vision|كاميرا|لقطة|مقطع فيديو|فيديو|video)/i.test(s)) return /(فيديو|video)/i.test(s) ? 'video' : 'vision';
  if (/(github|git\b|كود|برمج|code|debug|compile|build|apk|pull request|repo|repository)/i.test(s)) return 'code';
  if (/(ابحث|بحث|مصادر|source|sources|research|آخر|اليوم|دلوقتي|news|خبر)/i.test(s)) return 'research';
  if (/(ترجم|ترجمة|translate|translation|لغة|language)/i.test(s)) return 'language';
  return 'chat';
}

export function providerOrderFor(message='', freeFirst=true) {
  const task=classifyTask(message);
  const base=[...(TASKS[task]||TASKS.chat)];
  if (!freeFirst) return base;
  return [...base].sort((a,b)=>{
    const freeA=a==='local'?0:1, freeB=b==='local'?0:1;
    return freeA-freeB;
  });
}

export function normalizeEffortForProvider(provider, effort='none') {
  const e=String(effort||'none').toLowerCase();
  if (provider==='gemini') return ['minimal','low','medium','high'].includes(e)?e:'medium';
  if (provider==='mistral') return ['none','minimal','low','medium','high','xhigh'].includes(e)?e:'none';
  if (provider==='xai') return ['low','medium','high','xhigh'].includes(e)?e:'high';
  if (provider==='openai') return ['none','minimal','low','medium','high','xhigh','max'].includes(e)?e:'none';
  if (provider==='deepseek') return ['none','low','medium','high'].includes(e)?e:'medium';
  if (provider==='anthropic') return ['none','low','medium','high'].includes(e)?e:'medium';
  return 'none';
}

export function providerCapabilitySnapshot() {
  return Object.fromEntries(Object.entries(CAPABILITIES).map(([provider, capabilities])=>({
    [provider]: [...capabilities]
  }).[provider] ? [provider, [...capabilities]] : [provider, []]));
}
