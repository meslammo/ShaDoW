// MOD-79: task-aware provider selection for the unified SHADOW agent.
export function classifyTask(message = '') {
  const x = String(message).toLowerCase();
  if (/(صورة|image|photo|vision|screenshot|كاميرا|شوف)/i.test(x)) return 'vision';
  if (/(كود|code|coding|github|git|repo|repository|build|apk|test|برمجة|تطبيق|project|تطوير)/i.test(x)) return 'code';
  if (/(فيديو|video|movie|clip)/i.test(x)) return 'video';
  if (/(ترجمة|translate|translation)/i.test(x)) return 'language';
  if (/(بحث|ابحث|اخر|آخر|today|latest|news|current|update)/i.test(x)) return 'research';
  return 'chat';
}

export const TASK_PROVIDER_ORDER = {
  chat: ['local','gemini','mistral','deepseek','openai','xai','anthropic'],
  research: ['gemini','xai','openai','deepseek','mistral','anthropic','local'],
  code: ['xai','openai','anthropic','mistral','gemini','deepseek','local'],
  vision: ['gemini','openai','mistral','anthropic','xai','local'],
  language: ['gemini','mistral','anthropic','openai','deepseek','xai','local'],
  video: ['gemini','xai','openai','local'],
};

export function providerOrderFor(message, freeFirst = true) {
  const task = classifyTask(message);
  const order = [...(TASK_PROVIDER_ORDER[task] || TASK_PROVIDER_ORDER.chat)];
  if (freeFirst) {
    // Keep configured self-hosted/local models first, then prefer capable providers.
    const local = order.filter(x => x === 'local');
    const rest = order.filter(x => x !== 'local');
    return [...local, ...rest];
  }
  return order.filter(x => x !== 'local');
}

export function normalizeEffortForProvider(provider, effort) {
  const x = String(effort || 'none').toLowerCase();
  if (provider === 'gemini') return ['low','medium','high'].includes(x) ? x : 'medium';
  if (provider === 'anthropic') {
    if (x === 'max' || x === 'xhigh') return 'max';
    if (x === 'high') return 'high';
    if (x === 'low' || x === 'minimal') return 'low';
    return 'medium';
  }
  if (provider === 'mistral') {
    if (x === 'minimal') return 'low';
    if (['low','medium','high'].includes(x)) return x;
    return 'high';
  }
  return x;
}

export function providerCapabilitySnapshot(providerStatus = {}) {
  return Object.entries(providerStatus).map(([name, status]) => ({
    provider: name,
    configured: Boolean(status?.configured),
    model: status?.model || null,
    capabilities: status?.capabilities || [],
  }));
}
