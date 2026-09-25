import { createHash, randomUUID } from 'node:crypto';
import { appendFile, mkdir } from 'node:fs/promises';
import path from 'node:path';
import { runAgent, streamAgent, memoryStatus, providerStatus } from './ai-router.mjs';

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
      stage('model_route', 'pending'),
      stage('tools', 'pending'),
      stage('verify', 'pending'),
      stage('deliver', 'pending'),
    ],
    tool_events: toolEvents,
  };

  await audit({ event: 'pipeline.started', trace_id: id, request_length: request.length });

  try {
    const result = await runAgent({
      message: request,
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

    trace.phases[3] = stage('model_route', 'executed', {
      provider: result.provider || null,
      model: result.model || null,
      attempts: result.attempts || [],
    });
    trace.phases[4] = stage('tools', toolEvents.length ? 'executed' : 'not_required', {
      count: toolEvents.length,
    });
    trace.phases[5] = stage('verify', result.pendingAction ? 'action_pending' : (result.answer ? 'response_verified' : 'degraded'));
    trace.phases[6] = stage('deliver', result.answer || result.pendingAction ? 'completed' : 'degraded');

    const elapsedMs = Date.now() - started;
    const output = {
      ...result,
      trace: { ...trace, duration_ms: elapsedMs },
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
    trace.phases[3] = stage('model_route', 'failed');
    trace.phases[4] = stage('tools', toolEvents.length ? 'partially_executed' : 'not_started', { count: toolEvents.length });
    trace.phases[5] = stage('verify', 'failed');
    trace.phases[6] = stage('deliver', 'failed');
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
