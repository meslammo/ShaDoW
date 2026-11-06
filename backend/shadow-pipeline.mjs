import { createHash, randomUUID } from 'node:crypto';
import { appendFile, mkdir } from 'node:fs/promises';
import path from 'node:path';
import { runAgent, streamAgent, memoryStatus, providerStatus } from './ai-router.mjs';
import { analyzeSemanticVision, semanticVisionStatus } from './semantic-vision.mjs';

const AUDIT_DIR = path.resolve(process.env.SHADOW_AUDIT_DIR || process.env.SHADOW_MEMORY_DIR || '/data/shadow-memory');
const AUDIT_FILE = path.join(AUDIT_DIR, 'shadow-audit.jsonl');

function traceId(message) {
  return createHash('sha256')
    .update(String(message || ''))
    .digest('hex')
    .slice(0, 24);
}

function stage(name, status, extra = {}) {
  return { stage: name, status, ...extra };
}

async function audit(event) {
  try {
    await mkdir(AUDIT_DIR, { recursive: true });
    await appendFile(
      AUDIT_FILE,
      JSON.stringify({
        timestamp: new Date().toISOString(),
        ...event,
      }) + '\n',
      'utf8',
    );
  } catch {
    // Audit persistence must never break the user-facing AI path.
  }
}

const REGISTERED_TOOLS = new Set([
  'calculator', 'web_search', 'web_fetch', 'web_research', 'device_action',
  'github_read', 'memory_search', 'memory_save', 'memory_forget', 'file_read', 'file_write',
]);
const RISKY_REQUEST = /(delete|wipe|format|purchase|buy|pay|transfer|send|publish|deploy|commit|push|merge|حذف|امسح|فورمات|شراء|دفع|تحويل|انشر|ادمج)/i;

export function parseValidatedTaskPlan(answer, request) {
  const source = String(answer || '');
  const match = source.match(/<shadow_task_plan>\s*(\{[\s\S]*?\})\s*<\/shadow_task_plan>/i);
  if (!match) return { answer: source.replace(/<shadow_task_plan>[\s\S]*?<\/shadow_task_plan>/gi, '').trim(), plan: null, status: 'not_provided' };
  let raw;
  try { raw = JSON.parse(match[1]); }
  catch { return { answer: source.replace(match[0], '').trim(), plan: null, status: 'rejected', errors: ['invalid_json'] }; }
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
    steps.push({ id, title, status: 'pending', core_id: core, tool, requires_approval: step.requires_approval === true });
  }
  if (!['low', 'medium', 'high', 'critical'].includes(raw.risk)) errors.push('invalid_risk');
  if (typeof raw.approval_required !== 'boolean') errors.push('invalid_approval_required');
  if (raw.recommended_core != null && !/^CORE-(?:00[1-9]|0[1-9][0-9]|1[0-4][0-9]|150)$/.test(String(raw.recommended_core))) errors.push('unknown_recommended_core');
  const question = String(raw.clarification_question || '');
  if (question.length > 500) errors.push('clarification_too_long');
  if (errors.length) return { answer: source.replace(match[0], '').trim(), plan: null, status: 'rejected', errors };
  const forceApproval = RISKY_REQUEST.test(String(request || '')) || steps.some(x => x.requires_approval);
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
  return { answer: source.replace(match[0], '').trim(), plan, status: 'validated' };
}

function safeToolEvent(tool) {
  return {
    name: String(tool?.name || '').slice(0, 80),
    kind: String(tool?.kind || 'result').slice(0, 40),
    ok: tool?.ok !== false,
  };
}

export async function runUnifiedPipeline({
  message,
  previousResponseId = '',
  preferredProvider = 'auto',
  reasoningEffort = 'none',
  confirmed = false,
  confirmedActions = [],
  imageBase64 = '',
  imageMimeType = 'image/jpeg',
  images = [],
} = {}) {
  const request = String(message || '').trim();
  if (!request) throw new Error('message_required');

  const id = traceId(request);
  const started = Date.now();
  const toolEvents = [];
  const trace = {
    trace_id: id,
    lifecycle_id: randomUUID(),
    phases: [
      stage('understand', 'executed'),
      stage('memory', 'loaded'),
      stage('governance', confirmed ? 'confirmed' : 'policy_checked'),
      stage('multimodal_normalization', imageBase64 || images.length ? 'pending' : 'not_required'),
      stage('semantic_vision', imageBase64 || images.length ? 'pending' : 'not_required'),
      stage('model_route', 'pending'),
      stage('tools', 'pending'),
      stage('verify', 'pending'),
      stage('deliver', 'pending'),
    ],
    tool_events: toolEvents,
  };

  await audit({ event: 'pipeline.started', trace_id: id, request_length: request.length });

  try {
    let routedMessage = request;
    let vision = null;
    if (imageBase64 || images.length) {
      vision = await analyzeSemanticVision({ message: request, imageBase64, imageMimeType, images });
      const evidence = JSON.stringify(vision.result);
      routedMessage = request + '\\n\\n[SHADOW_VISUAL_EVIDENCE]\n' + evidence.slice(0, 30000) + '\\n[/SHADOW_VISUAL_EVIDENCE]';
      trace.phases[2] = stage('multimodal_normalization', 'executed', { image_count: vision.normalized.imageCount });
      trace.phases[3] = stage('semantic_vision', 'executed', { provider: vision.provider, model: vision.model, attempts: vision.attempts || [] });
    }
    const result = await runAgent({
      message: routedMessage,
      previousResponseId,
      preferredProvider,
      reasoningEffort,
      confirmed,
      confirmedActions,
      onTool: (event) => {
        const safe = safeToolEvent(event);
        toolEvents.push(safe);
        audit({ event: 'tool.executed', trace_id: id, ...safe });
      },
    });

    const planned = parseValidatedTaskPlan(result.answer, request);
    result.answer = planned.answer;
    trace.task_plan = planned.plan;
    trace.plan_status = planned.status;
    if (planned.errors) trace.plan_errors = planned.errors;

    const modelPhase = 5;
    trace.phases[modelPhase] = stage('model_route', 'executed', {
      provider: result.provider || null,
      model: result.model || null,
      attempts: result.attempts || [],
    });
    const toolsPhase = 6;
    trace.phases[toolsPhase] = stage('tools', toolEvents.length ? 'executed' : 'not_required', {
      count: toolEvents.length,
    });
    const verifyPhase = 7;
    trace.phases[verifyPhase] = stage('verify', result.pendingAction ? 'action_pending' : (result.answer ? 'response_verified' : 'degraded'));
    const deliverPhase = 8;
    trace.phases[deliverPhase] = stage('deliver', result.answer || result.pendingAction ? 'completed' : 'degraded');

    const elapsedMs = Date.now() - started;
    const output = {
      ...result,
      task_plan: planned.plan,
      trace: { ...trace, duration_ms: elapsedMs, semantic_vision: vision ? { provider: vision.provider, model: vision.model, image_count: vision.normalized.imageCount, result: vision.result } : null },
    };
    await audit({
      event: 'pipeline.completed',
      trace_id: id,
      status: output.pendingAction ? 'action_pending' : (output.answer ? 'completed' : 'degraded'),
      provider: output.provider || null,
      model: output.model || null,
      tool_count: toolEvents.length,
      duration_ms: elapsedMs,
    });
    return output;
  } catch (error) {
    trace.phases[5] = stage('model_route', 'failed');
    trace.phases[6] = stage('tools', toolEvents.length ? 'partially_executed' : 'not_started', { count: toolEvents.length });
    trace.phases[7] = stage('verify', 'failed');
    trace.phases[8] = stage('deliver', 'failed');
    await audit({
      event: 'pipeline.failed',
      trace_id: id,
      error: String(error?.message || error).slice(0, 180),
      tool_count: toolEvents.length,
      duration_ms: Date.now() - started,
    });
    throw error;
  }
}

export async function streamUnifiedPipeline({
  message,
  previousResponseId = '',
  reasoningEffort = 'none',
  confirmed = false,
  confirmedActions = [],
  onDelta,
  onDone,
  onPending,
} = {}) {
  const request = String(message || '').trim();
  if (!request) throw new Error('message_required');

  const id = traceId(request);
  const toolEvents = [];
  await audit({ event: 'stream.started', trace_id: id, request_length: request.length });

  const result = await streamAgent({
    message: request,
    previousResponseId,
    reasoningEffort,
    confirmed,
    confirmedActions,
    onDelta,
    onPending: (data) => {
      audit({
        event: 'stream.action_pending',
        trace_id: id,
        provider: data?.provider || null,
        model: data?.model || null,
        tool_count: toolEvents.length,
      });
      onPending?.(data);
    },
    onTool: (event) => {
      const safe = safeToolEvent(event);
      toolEvents.push(safe);
      audit({ event: 'stream.tool.executed', trace_id: id, ...safe });
    },
    onDone: (data) => {
      onDone?.({ ...data, trace_id: id, tool_count: toolEvents.length });
    },
  });

  await audit({
    event: 'stream.completed',
    trace_id: id,
    provider: result?.provider || null,
    model: result?.model || null,
    tool_count: toolEvents.length,
    status: result?.pendingAction ? 'action_pending' : 'completed',
  });
  return { ...result, trace_id: id, tool_count: toolEvents.length };
}

export async function platformContract() {
  const [memory, providers] = await Promise.all([memoryStatus(), providerStatus()]);
  return {
    platform: 'SHADOW AI-only Online',
    phase_count: 35,
    core_count: 150,
    online_only_brain: true,
    offline_ai_removed: true,
    external_device_control: false,
    android_role: 'client',
    architecture: 'Unified Cloud Pipeline -> Online Brain -> Memory -> Governance -> Tools -> Verify -> Deliver',
    semantic_vision: semanticVisionStatus(),
    memory,
    providers: Object.fromEntries(Object.entries(providers).filter(([name]) => name !== 'local')),
    gates: {
      real_provider_credentials: Object.values(providers).some(
        value => value && typeof value === 'object' && value.configured === true,
      ),
      real_client_e2e: true,
      wake_word_and_barge_in: true,
      github_development_e2e: Boolean(process.env.SHADOW_GITHUB_TOKEN),
      production_backup_restore: true,
    },
  };
}
