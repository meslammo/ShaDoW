const REGISTERED_TOOLS = new Set([
  'calculator', 'web_search', 'web_fetch', 'web_research', 'device_action',
  'github_read', 'memory_search', 'memory_save', 'memory_forget', 'file_read', 'file_write',
]);
const APPROVAL_TOOLS = new Set(['file_write', 'memory_save', 'memory_forget', 'device_action']);
const RISKY_REQUEST = /(delete|wipe|format|purchase|buy|pay|transfer|send|publish|deploy|commit|push|merge|حذف|امسح|فورمات|شراء|دفع|تحويل|انشر|ادمج)/i;

export function parseValidatedTaskPlan(answer, request) {
  const source = String(answer || '');
  const open = /<shadow_task_plan>/i.exec(source);
  if (!open) return { answer: source, plan: null, status: 'not_provided' };
  const jsonStart = source.indexOf('{', open.index + open[0].length);
  let depth = 0, inString = false, escaped = false, jsonEnd = -1;
  if (jsonStart >= 0) {
    for (let i = jsonStart; i < source.length; i += 1) {
      const ch = source[i];
      if (inString) {
        if (escaped) escaped = false;
        else if (ch.charCodeAt(0) === 92) escaped = true;
        else if (ch === '"') inString = false;
      } else if (ch === '"') inString = true;
      else if (ch === '{') depth += 1;
      else if (ch === '}') { depth -= 1; if (depth === 0) { jsonEnd = i + 1; break; } }
    }
  }
  const closeTag = jsonEnd >= 0 ? source.indexOf('</shadow_task_plan>', jsonEnd) : -1;
  const removeEnvelope = () => closeTag >= 0
    ? (source.slice(0, open.index) + source.slice(closeTag + '</shadow_task_plan>'.length)).trim()
    : source.slice(0, open.index).trim();
  if (jsonEnd < 0 || closeTag < 0) return { answer: removeEnvelope(), plan: null, status: 'rejected', errors: ['invalid_json_envelope'] };
  let raw;
  try { raw = JSON.parse(source.slice(jsonStart, jsonEnd)); }
  catch { return { answer: removeEnvelope(), plan: null, status: 'rejected', errors: ['invalid_json'] }; }
  const errors = [];
  if (!raw || raw.schema !== 'shadow.task-plan.v1') errors.push('unsupported_schema');
  if (typeof raw.goal !== 'string' || !raw.goal.trim() || raw.goal.length > 1000) errors.push('invalid_goal');
  if (!Array.isArray(raw.assumptions) || raw.assumptions.length > 12 || raw.assumptions.some(x => typeof x !== 'string' || x.length > 500)) errors.push('invalid_assumptions');
  if (!Array.isArray(raw.steps) || raw.steps.length < 1 || raw.steps.length > 12) errors.push('invalid_steps');
  const seen = new Set();
  const steps = [];
  for (const [index, step] of (Array.isArray(raw.steps) ? raw.steps : []).entries()) {
    if (!step || typeof step !== 'object') { errors.push('step_' + index + '_not_object'); continue; }
    const id = String(step.id || '');
    const title = String(step.title || '').trim();
    if (!/^[A-Za-z0-9_-]{1,40}$/.test(id) || seen.has(id)) errors.push('step_' + index + '_invalid_id');
    seen.add(id);
    if (!title || title.length > 240) errors.push('step_' + index + '_invalid_title');
    if (step.status !== undefined && !['pending', 'blocked'].includes(step.status)) errors.push('step_' + index + '_forged_status');
    const core = step.core_id == null ? null : String(step.core_id);
    if (core !== null && !/^CORE-(?:00[1-9]|0[1-9][0-9]|1[0-4][0-9]|150)$/.test(core)) errors.push('step_' + index + '_unknown_core');
    const tool = step.tool == null || step.tool === '' ? null : String(step.tool);
    if (tool !== null && !REGISTERED_TOOLS.has(tool)) errors.push('step_' + index + '_unknown_tool');
    if (step.requires_approval !== undefined && typeof step.requires_approval !== 'boolean') errors.push('step_' + index + '_invalid_approval');
    steps.push({ id, title, status: 'pending', core_id: core, tool, requires_approval: step.requires_approval === true || APPROVAL_TOOLS.has(tool) });
  }
  if (!['low', 'medium', 'high', 'critical'].includes(raw.risk)) errors.push('invalid_risk');
  if (typeof raw.approval_required !== 'boolean') errors.push('invalid_approval_required');
  if (raw.recommended_core != null && !/^CORE-(?:00[1-9]|0[1-9][0-9]|1[0-4][0-9]|150)$/.test(String(raw.recommended_core))) errors.push('unknown_recommended_core');
  const question = String(raw.clarification_question || '');
  if (question.length > 500) errors.push('clarification_too_long');
  if (errors.length) return { answer: removeEnvelope(), plan: null, status: 'rejected', errors };
  const forceApproval = ['high', 'critical'].includes(raw.risk) || RISKY_REQUEST.test(String(request || '')) || steps.some(x => x.requires_approval);
  const risk = forceApproval && ['low', 'medium'].includes(raw.risk) ? 'high' : raw.risk;
  const plan = {
    schema: 'shadow.task-plan.v1',
    goal: raw.goal.trim(),
    assumptions: raw.assumptions,
    steps,
    clarification_question: question.trim(),
    risk,
    approval_required: raw.approval_required || forceApproval,
    recommended_core: raw.recommended_core || steps.find(x => x.core_id)?.core_id || null,
    source: 'model_validated',
  };
  return { answer: removeEnvelope(), plan, status: 'validated' };
}
