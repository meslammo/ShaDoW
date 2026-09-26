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
  chat: ['openai','xai','anthropic','gemini','deepseek','mistral','pollinations'],
  research: ['openai','xai','gemini','deepseek','anthropic','mistral'],
  code: ['openai','xai','anthropic','deepseek','mistral','gemini'],
  vision: ['openai','gemini','anthropic','xai','mistral'],
  language: ['openai','anthropic','gemini','mistral','deepseek','xai','pollinations'],
  video: ['openai','gemini','xai','pollinations'],
});

export function providerOrderFor(message, _freeFirst = false) {
  const task = classifyTask(message);
  const ordered = [...(TASK_PROVIDER_ORDER[task] || TASK_PROVIDER_ORDER.chat)];
  const primary = String(process.env.SHADOW_PRIMARY_PROVIDER || 'openai').toLowerCase();
  return [primary, ...ordered.filter(provider => provider !== primary)];
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
