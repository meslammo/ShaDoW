import assert from 'node:assert/strict';
import test from 'node:test';
import { parseValidatedTaskPlan } from './task-plan.mjs';

function envelope(overrides = {}) {
  const plan = {
    schema: 'shadow.task-plan.v1',
    goal: 'Review repository safely',
    assumptions: [],
    steps: [{ id: 'inspect', title: 'Inspect repository', status: 'pending', core_id: 'CORE-081', tool: 'github_read', requires_approval: false }],
    clarification_question: '',
    risk: 'low',
    approval_required: false,
    recommended_core: 'CORE-081',
    ...overrides,
  };
  return '<shadow_task_plan>' + JSON.stringify(plan) + '</shadow_task_plan>النتيجة النهائية';
}

test('validates a model-produced plan and removes its machine envelope from the answer', () => {
  const result = parseValidatedTaskPlan(envelope(), 'راجع المستودع');
  assert.equal(result.status, 'validated');
  assert.equal(result.plan.recommended_core, 'CORE-081');
  assert.equal(result.plan.steps[0].status, 'pending');
  assert.equal(result.answer, 'النتيجة النهائية');
});

test('rejects unknown cores and unregistered tools', () => {
  const result = parseValidatedTaskPlan(envelope({
    steps: [{ id: 'bad', title: 'Bad route', status: 'pending', core_id: 'CORE-999', tool: 'shell_exec' }],
  }), 'راجع المشروع');
  assert.equal(result.status, 'rejected');
  assert.ok(result.errors.includes('step_0_unknown_core'));
  assert.ok(result.errors.includes('step_0_unknown_tool'));
});

test('rejects model claims that steps already completed', () => {
  const result = parseValidatedTaskPlan(envelope({
    steps: [{ id: 'done', title: 'Pretend complete', status: 'completed' }],
  }), 'نفذ المهمة');
  assert.equal(result.status, 'rejected');
  assert.ok(result.errors.includes('step_0_forged_status'));
});

test('forces approval for destructive user requests even when model marks low risk', () => {
  const result = parseValidatedTaskPlan(envelope(), 'احذف الملفات القديمة');
  assert.equal(result.status, 'validated');
  assert.equal(result.plan.approval_required, true);
  assert.equal(result.plan.risk, 'high');
});

test('does not fabricate a plan when model omits the explicit envelope', () => {
  const result = parseValidatedTaskPlan('إليك النتيجة', 'اشرح الفكرة');
  assert.equal(result.status, 'not_provided');
  assert.equal(result.plan, null);
});


test('high and critical risk always require approval', () => {
  const result = parseValidatedTaskPlan(envelope({ risk: 'critical' }), 'راجع الكود');
  assert.equal(result.status, 'validated');
  assert.equal(result.plan.approval_required, true);
});

test('sensitive tools cannot be marked as no-approval', () => {
  const result = parseValidatedTaskPlan(envelope({
    steps: [{ id: 'write', title: 'Write file', status: 'pending', core_id: 'CORE-081', tool: 'file_write', requires_approval: false }],
  }), 'جهز الملف');
  assert.equal(result.status, 'validated');
  assert.equal(result.plan.approval_required, true);
  assert.equal(result.plan.steps[0].requires_approval, true);
});
