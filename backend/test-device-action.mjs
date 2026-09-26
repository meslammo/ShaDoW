import test from 'node:test';
import assert from 'node:assert/strict';
import { TOOL_DEFINITIONS, executeTool, registryStatus } from './tool-registry.mjs';

test('device_action is registered as a client-side action tool', async () => {
  const tool = TOOL_DEFINITIONS.find((x) => x.name === 'device_action');
  assert.ok(tool, 'device_action must be present');
  const result = await executeTool('device_action', { command: 'افتح الإعدادات' });
  assert.equal(result.kind, 'client_action');
  assert.equal(result.action, 'افتح الإعدادات');
  assert.equal(result.requires_confirmation, false);
  assert.equal(registryStatus().device_action, true);
});

test('device_action marks sensitive actions for explicit confirmation', async () => {
  const result = await executeTool('device_action', { command: 'اتصل بأحمد' });
  assert.equal(result.kind, 'client_action');
  assert.equal(result.requires_confirmation, true);
});
