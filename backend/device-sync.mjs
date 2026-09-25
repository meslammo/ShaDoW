import crypto from 'node:crypto';
import fs from 'node:fs/promises';
import path from 'node:path';
import pg from 'pg';

const { Pool } = pg;

const SYNC_DIR = String(process.env.SHADOW_DEVICE_SYNC_DIR || process.env.SHADOW_MEMORY_DIR || '/data/shadow-memory').trim();
const FILE = path.join(SYNC_DIR, 'device-sync.json');
const databaseUrl = String(process.env.DATABASE_URL || '').trim();
const pool = databaseUrl ? new Pool({
  connectionString: databaseUrl,
  ssl: databaseUrl.includes('railway') ? { rejectUnauthorized: false } : undefined,
  max: 3,
  idleTimeoutMillis: 10000,
  connectionTimeoutMillis: 5000,
}) : null;

const LINK_TTL_SECONDS = 10 * 60;
const TOKEN_BYTES = 32;
const CODE_BYTES = 5;
const MAX_GATES = 100;
const MAX_MEMORY_FACTS = 100;
let dbReady = false;

const EXTERNAL_GATES = Object.freeze([
  'live_online_provider',
  'real_android_e2e',
  'wake_word_barge_in',
  'real_device_adapters',
  'trusted_companions',
  'cross_device_sync',
  'production_backup_restore',
]);

function nowIso() { return new Date().toISOString(); }

function hash(value) {
  return crypto.createHash('sha256').update(String(value || '')).digest('hex');
}

function makeToken() {
  return crypto.randomBytes(TOKEN_BYTES).toString('base64url');
}

function makeCode() {
  return crypto.randomBytes(CODE_BYTES).toString('hex').toUpperCase().slice(0, 8);
}

function isSecretLike(value) {
  return /(password|passphrase|api[_ -]?key|access[_ -]?token|secret|private[_ -]?key|credential|github[_ -]?token|bearer)/i.test(String(value || ''));
}

function safeString(value, max = 5000) {
  const text = String(value ?? '').trim();
  if (!text || text.length > max || isSecretLike(text)) return '';
  return text;
}

function sanitizeGates(input) {
  if (!input || typeof input !== 'object') return {};
  const out = {};
  for (const [rawKey, rawValue] of Object.entries(input).slice(0, MAX_GATES)) {
    const key = String(rawKey);
    const value = rawValue && typeof rawValue === 'object' ? rawValue : {};
    const status = ['pending', 'passed', 'failed', 'not_applicable'].includes(String(value.status || '').toLowerCase())
      ? String(value.status).toLowerCase() : 'pending';
    out[key] = {
      status,
      evidence_ref: safeString(value.evidence_ref, 500),
      tested_at: safeString(value.tested_at, 80) || nowIso(),
      notes: safeString(value.notes, 1000),
    };
  }
  return out;
}

function sanitizeMemoryFacts(input) {
  if (!Array.isArray(input)) return [];
  return input.slice(0, MAX_MEMORY_FACTS).map(item => ({
    id: safeString(item?.id, 100),
    text: safeString(item?.text, 3000),
    kind: safeString(item?.kind, 80) || 'fact',
    tags: Array.isArray(item?.tags) ? item.tags.slice(0, 12).map(x => safeString(x, 80)).filter(Boolean) : [],
  })).filter(x => x.text);
}

async function ensureDb() {
  if (!pool) return false;
  if (dbReady) return true;
  try {
    await pool.query(`CREATE TABLE IF NOT EXISTS shadow_device_registry (
      device_id TEXT PRIMARY KEY,
      instance_id TEXT NOT NULL,
      platform TEXT NOT NULL DEFAULT 'unknown',
      device_label TEXT NOT NULL DEFAULT '',
      app_version TEXT NOT NULL DEFAULT '',
      activation_token_hash TEXT NOT NULL,
      capabilities JSONB NOT NULL DEFAULT '[]'::jsonb,
      gate_state JSONB NOT NULL DEFAULT '{}'::jsonb,
      memory_facts JSONB NOT NULL DEFAULT '[]'::jsonb,
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      last_seen TIMESTAMPTZ NOT NULL DEFAULT NOW()
    )`);
    await pool.query(`CREATE INDEX IF NOT EXISTS shadow_device_instance_idx ON shadow_device_registry(instance_id)`);
    await pool.query(`CREATE TABLE IF NOT EXISTS shadow_device_links (
      code_hash TEXT PRIMARY KEY,
      instance_id TEXT NOT NULL,
      source_device_id TEXT NOT NULL,
      expires_at TIMESTAMPTZ NOT NULL,
      used_at TIMESTAMPTZ
    )`);
    dbReady = true;
    return true;
  } catch {
    dbReady = false;
    return false;
  }
}

async function readFile() {
  await fs.mkdir(SYNC_DIR, { recursive: true });
  try {
    const data = JSON.parse(await fs.readFile(FILE, 'utf8'));
    return data && typeof data === 'object' ? data : { devices: {}, links: {} };
  } catch {
    return { devices: {}, links: {} };
  }
}

async function writeFile(data) {
  await fs.mkdir(SYNC_DIR, { recursive: true });
  const tmp = FILE + '.tmp';
  await fs.writeFile(tmp, JSON.stringify(data, null, 2), 'utf8');
  await fs.rename(tmp, FILE);
}

function defaultGateState() {
  return Object.fromEntries(EXTERNAL_GATES.map(id => [id, {
    status: 'pending',
    evidence_ref: '',
    tested_at: '',
    notes: '',
  }]));
}

async function findDevice(deviceId, token) {
  const id = safeString(deviceId, 160);
  if (!id || !token) throw new Error('device_credentials_required');
  const tokenHash = hash(token);
  if (await ensureDb()) {
    const r = await pool.query('SELECT * FROM shadow_device_registry WHERE device_id=$1 AND activation_token_hash=$2 LIMIT 1', [id, tokenHash]);
    if (!r.rows.length) throw new Error('device_not_authorized');
    return r.rows[0];
  }
  const data = await readFile();
  const row = data.devices[id];
  if (!row || row.activation_token_hash !== tokenHash) throw new Error('device_not_authorized');
  return row;
}

export async function registerDevice({
  instanceId = '',
  deviceId,
  platform = 'android',
  deviceLabel = '',
  appVersion = '',
  capabilities = [],
  linkCode = '',
} = {}) {
  const cleanDeviceId = safeString(deviceId, 160);
  if (!cleanDeviceId) throw new Error('device_id_required');
  const cleanPlatform = safeString(platform, 50) || 'unknown';
  const cleanLabel = safeString(deviceLabel, 160);
  const cleanVersion = safeString(appVersion, 100);
  const caps = Array.isArray(capabilities) ? capabilities.slice(0, 80).map(x => safeString(x, 100)).filter(Boolean) : [];

  if (linkCode) {
    const linked = await completeLink({ linkCode, deviceId: cleanDeviceId, platform: cleanPlatform, deviceLabel: cleanLabel, appVersion: cleanVersion, capabilities: caps });
    return linked;
  }

  if (await ensureDb()) {
    const existing = await pool.query('SELECT * FROM shadow_device_registry WHERE device_id=$1 LIMIT 1', [cleanDeviceId]);
    if (existing.rows.length) {
      const row = existing.rows[0];
      await pool.query(
        'UPDATE shadow_device_registry SET platform=$2,device_label=$3,app_version=$4,capabilities=$5,last_seen=NOW() WHERE device_id=$1',
        [cleanDeviceId, cleanPlatform, cleanLabel, cleanVersion, JSON.stringify(caps)]
      );
      return {
        activated: true,
        existing: true,
        device_id: cleanDeviceId,
        instance_id: row.instance_id,
        activation_token: '',
        sync_available: true,
      };
    }
    const instance = 'shadow-' + crypto.randomUUID();
    const token = makeToken();
    await pool.query(
      'INSERT INTO shadow_device_registry(device_id,instance_id,platform,device_label,app_version,activation_token_hash,capabilities,gate_state) VALUES($1,$2,$3,$4,$5,$6,$7,$8)',
      [cleanDeviceId, instance, cleanPlatform, cleanLabel, cleanVersion, hash(token), JSON.stringify(caps), JSON.stringify(defaultGateState())]
    );
    return { activated: true, existing: false, device_id: cleanDeviceId, instance_id: instance, activation_token: token, sync_available: true };
  }

  const data = await readFile();
  const existing = data.devices[cleanDeviceId];
  if (existing) {
    existing.platform = cleanPlatform;
    existing.device_label = cleanLabel;
    existing.app_version = cleanVersion;
    existing.capabilities = caps;
    existing.last_seen = nowIso();
    await writeFile(data);
    return { activated: true, existing: true, device_id: cleanDeviceId, instance_id: existing.instance_id, activation_token: '', sync_available: true };
  }
  const instance = safeString(instanceId, 160) || ('shadow-' + crypto.randomUUID());
  const token = makeToken();
  data.devices[cleanDeviceId] = {
    device_id: cleanDeviceId,
    instance_id: instance,
    platform: cleanPlatform,
    device_label: cleanLabel,
    app_version: cleanVersion,
    activation_token_hash: hash(token),
    capabilities: caps,
    gate_state: defaultGateState(),
    memory_facts: [],
    created_at: nowIso(),
    last_seen: nowIso(),
  };
  await writeFile(data);
  return { activated: true, existing: false, device_id: cleanDeviceId, instance_id: instance, activation_token: token, sync_available: true };
}

export async function startLink({ deviceId, activationToken, ttlSeconds = LINK_TTL_SECONDS } = {}) {
  const source = await findDevice(deviceId, activationToken);
  const code = makeCode();
  const ttl = Math.max(60, Math.min(Number(ttlSeconds) || LINK_TTL_SECONDS, LINK_TTL_SECONDS));
  const expires = new Date(Date.now() + ttl * 1000).toISOString();

  if (await ensureDb()) {
    await pool.query('DELETE FROM shadow_device_links WHERE expires_at < NOW()');
    await pool.query('INSERT INTO shadow_device_links(code_hash,instance_id,source_device_id,expires_at) VALUES($1,$2,$3,$4)', [hash(code), source.instance_id, source.device_id, expires]);
  } else {
    const data = await readFile();
    data.links[hash(code)] = { instance_id: source.instance_id, source_device_id: source.device_id, expires_at: expires, used_at: '' };
    await writeFile(data);
  }
  return { ok: true, code, expires_at: expires, instance_id: source.instance_id, one_time: true };
}

export async function completeLink({ linkCode, deviceId, platform = 'android', deviceLabel = '', appVersion = '', capabilities = [] } = {}) {
  const cleanCode = safeString(linkCode, 32).toUpperCase();
  const cleanDeviceId = safeString(deviceId, 160);
  if (!cleanCode || !cleanDeviceId) throw new Error('link_credentials_required');

  let link;
  if (await ensureDb()) {
    const r = await pool.query('SELECT * FROM shadow_device_links WHERE code_hash=$1 AND used_at IS NULL AND expires_at > NOW() LIMIT 1', [hash(cleanCode)]);
    if (!r.rows.length) throw new Error('link_code_invalid_or_expired');
    link = r.rows[0];
  } else {
    const data = await readFile();
    const row = data.links[hash(cleanCode)];
    if (!row || row.used_at || new Date(row.expires_at).getTime() <= Date.now()) throw new Error('link_code_invalid_or_expired');
    link = row;
  }

  const token = makeToken();
  const gateState = defaultGateState();
  if (await ensureDb()) {
    await pool.query(
      'INSERT INTO shadow_device_registry(device_id,instance_id,platform,device_label,app_version,activation_token_hash,capabilities,gate_state) VALUES($1,$2,$3,$4,$5,$6,$7,$8) ON CONFLICT(device_id) DO UPDATE SET instance_id=EXCLUDED.instance_id,platform=EXCLUDED.platform,device_label=EXCLUDED.device_label,app_version=EXCLUDED.app_version,activation_token_hash=EXCLUDED.activation_token_hash,capabilities=EXCLUDED.capabilities,last_seen=NOW()',
      [cleanDeviceId, link.instance_id, safeString(platform, 50) || 'android', safeString(deviceLabel, 160), safeString(appVersion, 100), hash(token), JSON.stringify(Array.isArray(capabilities) ? capabilities.slice(0, 80) : []), JSON.stringify(gateState)]
    );
    await pool.query('UPDATE shadow_device_links SET used_at=NOW() WHERE code_hash=$1', [hash(cleanCode)]);
  } else {
    const data = await readFile();
    data.devices[cleanDeviceId] = {
      device_id: cleanDeviceId,
      instance_id: link.instance_id,
      platform: safeString(platform, 50) || 'android',
      device_label: safeString(deviceLabel, 160),
      app_version: safeString(appVersion, 100),
      activation_token_hash: hash(token),
      capabilities: Array.isArray(capabilities) ? capabilities.slice(0, 80) : [],
      gate_state: gateState,
      memory_facts: [],
      created_at: nowIso(),
      last_seen: nowIso(),
    };
    data.links[hash(cleanCode)].used_at = nowIso();
    await writeFile(data);
  }

  return { ok: true, linked: true, device_id: cleanDeviceId, instance_id: link.instance_id, activation_token: token, sync_available: true };
}

export async function syncDevice({
  deviceId,
  activationToken,
  phaseGates = {},
  memoryFacts = [],
  platformManifest = {},
} = {}) {
  const device = await findDevice(deviceId, activationToken);
  const incomingGates = sanitizeGates(phaseGates);
  const incomingMemory = sanitizeMemoryFacts(memoryFacts);
  const manifest = {
    phase_count: 35,
    core_count: 150,
    app_version: safeString(platformManifest.app_version, 100),
    platform: safeString(platformManifest.platform, 50),
    updated_at: nowIso(),
  };

  if (await ensureDb()) {
    const all = await pool.query('SELECT device_id,instance_id,platform,device_label,app_version,capabilities,gate_state,memory_facts,last_seen FROM shadow_device_registry WHERE instance_id=$1 ORDER BY last_seen DESC', [device.instance_id]);
    const mergedGates = mergeGateStates(
      defaultGateState(),
      ...all.rows.map(row => row.gate_state || {}),
      incomingGates,
    );
    const mergedMemory = mergeMemoryFacts(
      ...all.rows.map(row => row.memory_facts || []),
      incomingMemory,
    );
    await pool.query(
      'UPDATE shadow_device_registry SET gate_state=$2,memory_facts=$3,last_seen=NOW() WHERE device_id=$1',
      [deviceId, JSON.stringify(mergedGates), JSON.stringify(mergedMemory)]
    );
    return makeSyncResponse(device.instance_id, deviceId, mergedGates, mergedMemory, all.rows, manifest);
  }

  const data = await readFile();
  const current = data.devices[deviceId];
  const peers = Object.values(data.devices).filter(x => x.instance_id === device.instance_id);
  const mergedGates = mergeGateStates(
    defaultGateState(),
    ...peers.map(row => row.gate_state || {}),
    incomingGates,
  );
  const mergedMemory = mergeMemoryFacts(
    ...peers.map(row => row.memory_facts || []),
    incomingMemory,
  );
  current.gate_state = mergedGates;
  current.memory_facts = mergedMemory;
  current.last_seen = nowIso();
  await writeFile(data);
  return makeSyncResponse(device.instance_id, deviceId, mergedGates, mergedMemory, peers, manifest);
}

function mergeGateStates(base, ...sources) {
  const out = { ...base };
  for (const source of sources) {
    for (const [gateId, candidate] of Object.entries(source || {})) {
      if (!candidate || typeof candidate !== 'object') continue;
      const existing = out[gateId];
      if (!existing || existing.status !== 'passed' || candidate.status === 'passed') {
        out[gateId] = { ...existing, ...candidate };
      }
    }
  }
  return out;
}

function mergeMemoryFacts(...sources) {
  const out = [];
  for (const source of sources) {
    if (!Array.isArray(source)) continue;
    for (const item of source) {
      if (!item?.text) continue;
      if (out.some(existing => existing.text === item.text)) continue;
      out.push(item);
    }
  }
  return out.slice(-MAX_MEMORY_FACTS);
}

function makeSyncResponse(instanceId, deviceId, gates, memoryFacts, peers, manifest) {
  const peerSummary = peers.map(x => ({
    device_id: x.device_id,
    platform: x.platform,
    device_label: x.device_label,
    app_version: x.app_version,
    last_seen: x.last_seen,
    capabilities: x.capabilities || [],
  }));
  const missing = Object.entries(gates)
    .filter(([, value]) => value?.status !== 'passed')
    .map(([gateId, value]) => ({ gate_id: gateId, ...value }));
  return {
    ok: true,
    instance_id: instanceId,
    device_id: deviceId,
    sync: {
      phase_count: 35,
      core_count: 150,
      online_only_brain: true,
      gates,
      pending_gates: missing,
      memory_facts: memoryFacts,
      peers: peerSummary,
      manifest,
    },
  };
}

export async function reportGate({
  deviceId,
  activationToken,
  gateId,
  status,
  evidenceRef = '',
  notes = '',
} = {}) {
  const device = await findDevice(deviceId, activationToken);
  const gate = sanitizeGates({ [safeString(gateId, 120)]: { status, evidence_ref: evidenceRef, notes } });
  return syncDevice({ deviceId, activationToken, phaseGates: gate });
}

export function externalGateCatalog() {
  return EXTERNAL_GATES.map(id => ({ gate_id: id, status: 'pending', owner: 'device-validation', syncable: true }));
}
