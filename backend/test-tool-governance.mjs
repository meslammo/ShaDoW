import assert from 'node:assert/strict';
import { executeTool } from './tool-registry.mjs';

const memory = [];
const helpers = {
  calc: (x) => String(x),
  memorySearch: async () => [],
  memorySave: async (...args) => { memory.push(args); return { saved: true }; },
  memoryForget: async () => ({ forgotten: 1 }),
  githubRead: async () => ({}),
  requireWriteApproval: true,
};

await assert.rejects(
  () => executeTool('memory_save', { fact: 'approved fact' }, {
    ...helpers,
    authorizeMutation: () => false,
  }),
  /explicit_confirmation_required/,
);

const saved = await executeTool('memory_save', { fact: 'approved fact' }, {
  ...helpers,
  authorizeMutation: () => true,
});
assert.equal(saved.value.saved, true);
assert.equal(memory.length, 1);

await assert.rejects(
  () => executeTool('memory_forget', { query: 'fact' }, {
    ...helpers,
    authorizeMutation: () => false,
  }),
  /explicit_confirmation_required/,
);

const forget = await executeTool('memory_forget', { query: 'fact' }, {
  ...helpers,
  authorizeMutation: () => true,
});
assert.equal(forget.value.forgotten, 1);

await assert.rejects(
  () => executeTool('android_action', { action: 'open_settings' }, {
    ...helpers,
    authorizeMutation: () => true,
  }),
  /unknown_tool/,
);

console.log('device-control: disabled');
