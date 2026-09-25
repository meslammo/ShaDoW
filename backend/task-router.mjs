// MOD-79: task-aware provider selection for the unified SHADOW agent.
export function classifyTask(message = '') {
  const x = String(message || '').toLowerCase();
  if (/(فيديو|video|movie|clip)/i.test(x)) return 'video';
  if (/(صورة|image|photo|vision|screenshot|كاميرا|لقطة|حلل الصورة|شوف)/i.test(x)) return 'vision';
  if (/(كود|code|coding|github|git\b|repo|repository|build|apk|test|debug|compile|برمجة|تطبيق|project|تطوير|جيت هب)/i.test(x)) return 'code';
  if (/(ترجمة|ترجم|translate|translation|لغة|language)/i.test(x)) return 'language';
  if (/(بحث|ابحث|مصادر|research|آخر|اخر|today|latest|news|current|update|النهارده|دلوقتي)/i.test(x)) return 'research';
  return 'chat';
}

export const TASK_PROVIDER_ORDER = Object.freeze({
  chat: ['gemini','mistral','deepseek','openai','xai','anthropic'],
  research: ['gemini','xai','openai','deepseek','mistral','anthropic'],
  code: ['xai','openai','anthropic','mistral','gemini','deepseek'],
  vision: ['gemini','openai','mistral','anthropic','xai'],
  language: ['gemini','mistral','anthropic','openai','deepseek','xai'],
  video: ['gemini','xai','openai'],
});

export function providerOrderFor(message, freeFirst = true) {
  const task = classifyTask(message);
  const order = [...(TASK_PROVIDER_ORDER[task] || TASK_PROVIDER_ORDER.chat)];
  if (freeFirst) {
    return order.includes('local') ? [ ...order.filter(x => x !== 'local')] : order;
  }
  return order.filter(x => x !== 'local');
}

export function normalizeEffortForProvider(provider, effort = 'none') {
  const x = String(effort || 'none').toLowerCase();
  if (provider === 'gemini') return ['low','medium','high'].includes(x) ? x : 'medium';
  if (provider === 'anthropic') {
    if (x === 'max' || x === 'xhigh') return 'max';
    if (x === 'high') return 'high';
    if (x === 'medium') return 'medium';
    if (x === 'low' || x === 'minimal') return 'low';
    return 'medium';
  }
  if (provider === 'mistral') {
    if (x === 'xhigh' || x === 'max') return 'xhigh';
    if (['none','minimal','low','medium','high'].includes(x)) return x;
    return 'medium';
  }
  if (provider === 'xai') return ['low','medium','high','xhigh'].includes(x) ? x : 'high';
  if (provider === 'openai') return ['none','minimal','low','medium','high','xhigh','max'].includes(x) ? x : 'medium';
  if (provider === 'deepseek') {
    if (x === 'max') return 'max';
    if (x === 'xhigh' || x === 'high' || x === 'medium') return x === 'xhigh' || x === 'medium' ? 'high' : 'high';
    if (x === 'low' || x === 'minimal') return 'low';
    return 'high';
  }
  return 'none';
}

export function providerCapabilitySnapshot(providerStatus = {}) {
  return Object.entries(providerStatus).map(([name, status]) => ({
    provider: name,
    configured: Boolean(status?.configured),
    model: status?.model || null,
    capabilities: status?.capabilities || [],
  }));
}
