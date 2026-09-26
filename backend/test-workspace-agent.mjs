import test from 'node:test';
import assert from 'node:assert/strict';
import { workspaceWrite, workspaceExec } from './workspace-agent.mjs';

test('workspace agent can write and execute a safe source file', async () => {
  const file = 'tests/runtime-smoke.mjs';
  const write = await workspaceWrite(file, "console.log('workspace-ok')\n");
  assert.equal(write.verified, true);
  const run = await workspaceExec({ command: 'node', args: [file], cwd: '.', timeoutMs: 15000 });
  assert.equal(run.ok, true);
  assert.match(run.stdout, /workspace-ok/);
});

test('workspace agent blocks shell injection and destructive git commands', async () => {
  await assert.rejects(
    () => workspaceExec({ command: 'sh', args: ['-c', 'echo unsafe'] }),
    /command_not_allowed/
  );
  await assert.rejects(
    () => workspaceExec({ command: 'git', args: ['push'] }),
    /git_mutation_blocked/
  );
});
