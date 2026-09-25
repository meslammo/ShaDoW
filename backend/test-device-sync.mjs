import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';

const dir = await fs.mkdtemp(path.join(os.tmpdir(), 'shadow-device-sync-'));
process.env.SHADOW_DEVICE_SYNC_DIR = dir;
delete process.env.DATABASE_URL;

const mod = await import('./device-sync.mjs');

const a = await mod.registerDevice({
  deviceId: 'device-A',
  platform: 'android',
  deviceLabel: 'Phone A',
  appVersion: 'test',
  capabilities: ['voice', 'phase35'],
});
assert.equal(a.activated, true);
assert.equal(a.existing, false);
assert.ok(a.activation_token);
assert.ok(a.instance_id);

const firstSync = await mod.syncDevice({
  deviceId: 'device-A',
  activationToken: a.activation_token,
  phaseGates: {
    live_online_provider: {
      status: 'passed',
      evidence_ref: 'ci://online-provider',
    },
  },
  memoryFacts: [{ id: 'm1', text: 'approved shared project fact', kind: 'project', tags: ['approved'] }],
});
assert.equal(firstSync.ok, true);
assert.equal(firstSync.sync.gates.live_online_provider.status, 'passed');
assert.equal(firstSync.sync.pending_gates.length, 6);
assert.equal(firstSync.sync.memory_facts.length, 1);

const link = await mod.startLink({
  deviceId: 'device-A',
  activationToken: a.activation_token,
});
assert.equal(link.one_time, true);
assert.ok(link.code);
assert.equal(link.instance_id, a.instance_id);

const b = await mod.completeLink({
  linkCode: link.code,
  deviceId: 'device-B',
  platform: 'android',
  deviceLabel: 'Phone B',
  appVersion: 'test',
});
assert.equal(b.linked, true);
assert.equal(b.instance_id, a.instance_id);
assert.ok(b.activation_token);

const bSync = await mod.syncDevice({
  deviceId: 'device-B',
  activationToken: b.activation_token,
});
assert.equal(bSync.sync.gates.live_online_provider.status, 'passed');
assert.equal(bSync.sync.memory_facts[0].text, 'approved shared project fact');

const bGate = await mod.reportGate({
  deviceId: 'device-B',
  activationToken: b.activation_token,
  gateId: 'real_android_e2e',
  status: 'passed',
  evidenceRef: 'device://android-real-test',
});
assert.equal(bGate.sync.gates.real_android_e2e.status, 'passed');

const aSync = await mod.syncDevice({
  deviceId: 'device-A',
  activationToken: a.activation_token,
});
assert.equal(aSync.sync.gates.live_online_provider.status, 'passed');
assert.equal(aSync.sync.gates.real_android_e2e.status, 'passed');
assert.equal(aSync.sync.pending_gates.length, 5);

await assert.rejects(
  () => mod.completeLink({
    linkCode: link.code,
    deviceId: 'device-C',
    platform: 'android',
  }),
  /link_code_invalid_or_expired/,
);

console.log('device-sync: ok');
