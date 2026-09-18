import fs from 'node:fs/promises';
import path from 'node:path';
import pg from 'pg';
import { TOOL_DEFINITIONS, executeTool } from './tool-registry.mjs';
import { runOffline, offlineStatus } from './offline-engine.mjs';

const { Pool } = pg;
const OPENAI_URL = 'https://api.openai.com/v1/responses';
const XAI_URL = 'https://api.x.ai/v1/responses';
const DEEPSEEK_URL = 'https://api.deepseek.com/chat/completions';
const cfg = {
  openaiKey: (process.env.OPENAI_API_KEY || '').trim(),
  openaiModel: (process.env.OPENAI_MODEL || 'gpt-5.6-luna').trim(),
  xaiKey: (process.env.XAI_API_KEY || '').trim(),
  xaiModel: (process.env.XAI_MODEL || 'grok-4.6').trim(),
  deepseekKey: (process.env.DEEPSEEK_API_KEY || '').trim(),
  deepseekModel: (process.env.DEEPSEEK_MODEL || 'deepseek-chat').trim(),
  memoryDir: (process.env.SHADOW_MEMORY_DIR || '/data/shadow-memory').trim(),
  databaseUrl: (process.env.DATABASE_URL || '').trim(),
  workspaceDir: (process.env.SHADOW_WORKSPACE_DIR || '/data/shadow-workspace').trim(),
};
const pool = cfg.databaseUrl ? new Pool({
  connectionString: cfg.databaseUrl,
  ssl: cfg.databaseUrl.includes('railway') ? { rejectUnauthorized: false } : undefined,
  max: 5,
  idleTimeoutMillis: 10000,
  connectionTimeoutMillis: 5000,
}) : null;
let dbReady = false;
let lastDbError = '';

const systemPrompt = String(process.env.SHADOW_SYSTEM_PROMPT || [
  'You are SHADOW, a personal unified AI assistant for one owner.',
  'Use natural Egyptian Arabic when the user uses Arabic unless formal Arabic is requested.',
  'Operate as the SHADOW master agent: understand -> classify -> plan -> route -> choose tools -> execute -> observe -> verify -> continue.',
  'Use one conversation path for typed and spoken requests; voice is only a transport layer, not a separate intelligence mode.',
  'Never route a GitHub/development request as ordinary chat when a development or GitHub tool is required.' ,
  'Never claim an action happened unless a tool or device result confirms it.',
  'Use web tools for current/public information and verify important claims from sources.',
  'Use GitHub tools only for public repository reading unless an explicitly authorized write gateway is available.',
  'Use file tools only inside the SHADOW workspace and never expose secrets.',
  'Never store or reveal passwords, API keys, access tokens, private keys, or authentication secrets.',
  'For risky or irreversible device/file actions, request explicit confirmation before execution.',
  'When all online AI providers fail, continue with the internal offline fallback; never expose a separate Local Chat mode.',
].join(' '));

async function ensureDb() {
  if (!pool) return false;
  if (dbReady) return true;
  try {
    await pool.query(`CREATE TABLE IF NOT EXISTS shadow_memory (id BIGSERIAL PRIMARY KEY,fact TEXT NOT NULL,reason TEXT NOT NULL DEFAULT '',saved_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),owner TEXT NOT NULL DEFAULT 'master')`);
    await pool.query('CREATE INDEX IF NOT EXISTS shadow_memory_saved_at_idx ON shadow_memory(saved_at DESC)');
    dbReady = true;
    lastDbError = '';
    return true;
  } catch (e) {
    dbReady = false;
    lastDbError = String(e?.message || e).slice(0, 300);
    return false;
  }
}

async function ensureMemoryFile() {
  await fs.mkdir(cfg.memoryDir, { recursive: true });
  const file = path.join(cfg.memoryDir, 'memory.json');
  try { await fs.access(file); } catch { await fs.writeFile(file, '[]', 'utf8'); }
  return file;
}
async function loadFileMemory() {
  const file = await ensureMemoryFile();
  try {
    const data = JSON.parse(await fs.readFile(file, 'utf8'));
    return Array.isArray(data) ? data : [];
  } catch { return []; }
}
async function saveFileMemory(items) {
  const file = await ensureMemoryFile();
  await fs.writeFile(file, JSON.stringify(items.slice(-500), null, 2), 'utf8');
}
async function loadMemory() {
  if (await ensureDb()) {
    try {
      const result = await pool.query('SELECT id, fact, reason, saved_at FROM shadow_memory ORDER BY saved_at DESC LIMIT 500');
      return result.rows;
    } catch (e) {
      dbReady = false;
      lastDbError = String(e?.message || e).slice(0, 300);
    }
  }
  return loadFileMemory();
}
function secretLike(text) {
  return /(password|passphrase|api[_ -]?key|access[_ -]?token|secret|private key|كلمة السر|باسورد|توكن|مفتاح سري)/i.test(String(text || ''));
}
async function saveMemory(fact, reason) {
  const value = String(fact || '').trim();
  if (!value) return { saved: false, reason: 'empty' };
  if (secretLike(value) || secretLike(reason)) return { saved: false, reason: 'secret_or_credential_blocked' };
  if (await ensureDb()) {
    try {
      const existing = await pool.query('SELECT id FROM shadow_memory WHERE lower(fact)=lower($1) LIMIT 1', [value]);
      if (existing.rows.length) return { saved: false, reason: 'duplicate', storage: 'postgres' };
      await pool.query('INSERT INTO shadow_memory(fact,reason) VALUES($1,$2)', [value, String(reason || '')]);
      return { saved: true, storage: 'postgres' };
    } catch (e) {
      dbReady = false;
      lastDbError = String(e?.message || e).slice(0, 300);
    }
  }
  const all = await loadFileMemory();
  if (all.some(x => String(x.fact || '').toLowerCase() === value.toLowerCase())) return { saved: false, reason: 'duplicate', storage: 'file-fallback' };
  all.push({ fact: value, reason: String(reason || ''), saved_at: new Date().toISOString() });
  await saveFileMemory(all);
  return { saved: true, storage: 'file-fallback' };
}
async function searchMemory(query) {
  const q = String(query || '').trim().toLowerCase();
  if (!q) return [];
  const rows = await loadMemory();
  const terms = q.split(/\s+/).filter(Boolean);
  return rows.filter(x => {
    const hay = `${String(x.fact || '')} ${String(x.reason || '')}`.toLowerCase();
    return terms.every(t => hay.includes(t));
  }).slice(0, 20);
}
async function forgetMemory(query) {
  const q = String(query || '').trim().toLowerCase();
  if (!q) return { forgotten: 0 };
  if (await ensureDb()) {
    try {
      const result = await pool.query('DELETE FROM shadow_memory WHERE lower(fact) LIKE $1', [`%${q}%`]);
      return { forgotten: result.rowCount || 0, storage: 'postgres' };
    } catch (e) {
      dbReady = false;
      lastDbError = String(e?.message || e).slice(0, 300);
    }
  }
  const all = await loadFileMemory();
  const kept = all.filter(x => !String(x.fact || '').toLowerCase().includes(q));
  await saveFileMemory(kept);
  return { forgotten: all.length - kept.length, storage: 'file-fallback' };
}
function calc(expr) {
  const x = String(expr || '').trim();
  if (!/^[0-9+\-*/().%\s]+$/.test(x) || x.length > 200) throw new Error('invalid_expression');
  const value = Function('"use strict";return(' + x + ')')();
  if (typeof value !== 'number' || !Number.isFinite(value)) throw new Error('invalid_result');
  return String(value);
}
async function githubRead(repo, filePath) {
  const ownerRepo = String(repo || '').trim();
  if (!/^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/.test(ownerRepo)) throw new Error('invalid_repo');
  const clean = String(filePath || '').replace(/^\/+/, '');
  const url = 'https://api.github.com/repos/' + ownerRepo + (clean ? '/contents/' + clean : '');
  const response = await fetch(url, { headers: { Accept: 'application/vnd.github+json', 'User-Agent': 'SHADOW-AI/1.0', 'X-GitHub-Api-Version': '2022-11-28' }, signal: AbortSignal.timeout(15000) });
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error('github_' + response.status);
  if (Array.isArray(body)) return body.map(x => ({ name: x.name, path: x.path, type: x.type, size: x.size, url: x.html_url }));
  if (body?.encoding === 'base64' && typeof body.content === 'string') {
    return { name: body.name, path: body.path, size: body.size, html_url: body.html_url, content: Buffer.from(body.content.replace(/\s/g, ''), 'base64').toString('utf8').slice(0, 60000) };
  }
  return body;
}
async function localFilesList() {
  await fs.mkdir(cfg.workspaceDir, { recursive: true });
  const entries = await fs.readdir(cfg.workspaceDir, { withFileTypes: true });
  return entries.map(e => ({ name: e.name, type: e.isDirectory() ? 'directory' : 'file' })).slice(0, 200);
}

const helperSet = {
  calc,
  githubRead,
  memorySearch: searchMemory,
  memorySave: saveMemory,
  memoryForget: forgetMemory,
  requireWriteApproval: true,
};
async function runTool(name, args) {
  return executeTool(name, args, helperSet);
}

function textOf(body) {
  if (typeof body?.output_text === 'string') return body.output_text.trim();
  const output = Array.isArray(body?.output) ? body.output : [];
  return output.flatMap(x => Array.isArray(x.content) ? x.content : [])
    .filter(x => x?.type === 'output_text' && typeof x.text === 'string')
    .map(x => x.text).join('\n').trim();
}
function callsOf(body) {
  return (Array.isArray(body?.output) ? body.output : []).filter(x => x?.type === 'function_call' && typeof x.name === 'string');
}
function webUsed(body) {
  return (Array.isArray(body?.output) ? body.output : []).some(x => x?.type === 'web_search_call');
}
async function callResponses(provider, payload) {
  const isXai = provider === 'xai';
  const key = isXai ? cfg.xaiKey : cfg.openaiKey;
  const url = isXai ? XAI_URL : OPENAI_URL;
  const response = await fetch(url, {
    method: 'POST',
    headers: { Authorization: `Bearer ${key}`, 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
    signal: AbortSignal.timeout(65000),
  });
  const body = await response.json().catch(() => ({}));
  if (!response.ok) {
    const error = new Error(body?.error?.code || body?.error?.message || `upstream_${response.status}`);
    error.http = response.status;
    throw error;
  }
  return body;
}
async function responsesAgent(provider, message, previousResponseId, device, reasoningEffort = 'none') {
  let previous = previousResponseId || undefined;
  let input = device ? `${message}\n\n[DEVICE_PROFILE]\n${device}` : message;
  let usedWeb = false;
  const model = provider === 'xai' ? cfg.xaiModel : cfg.openaiModel;
  for (let i = 0; i < 8; i++) {
    const builtInWeb = provider === 'xai' ? [{ type: 'web_search' }, { type: 'x_search' }] : [{ type: 'web_search_preview' }];
    const payload = { model, instructions: systemPrompt, input, tools: [...builtInWeb, ...TOOL_DEFINITIONS.filter(x => x.name !== 'file_write')], store: true };
    if (provider === 'openai' && reasoningEffort !== 'none') payload.reasoning = { effort: reasoningEffort };
    if (previous) payload.previous_response_id = previous;
    const body = await callResponses(provider, payload);
    usedWeb ||= webUsed(body);
    const calls = callsOf(body);
    if (!calls.length) return { provider, model, answer: textOf(body), responseId: body.id || previous || null, usedWeb, pendingAction: null };
    const outputs = [];
    for (const call of calls) {
      const args = typeof call.arguments === 'string' ? JSON.parse(call.arguments || '{}') : (call.arguments || {});
      const result = await runTool(call.name, args);
      if (result.kind === 'client_action') {
        return { provider, model, answer: textOf(body) || 'هحتاج تنفيذ الإجراء على الموبايل.', responseId: body.id || null, usedWeb, pendingAction: { ...result, toolCallId: call.call_id || call.id || '' } };
      }
      outputs.push({ type: 'function_call_output', call_id: call.call_id || call.id, output: JSON.stringify(result.value) });
    }
    previous = body.id;
    input = outputs;
  }
  throw new Error('agent_loop_limit');
}
async function deepseekAgent(message, device, reasoningEffort = 'none') {
  const messages = [{ role: 'system', content: systemPrompt }, { role: 'user', content: device ? `${message}\n\n[DEVICE_PROFILE]\n${device}` : message }];
  const tools = TOOL_DEFINITIONS.map(x => ({ type: 'function', function: { name: x.name, description: x.description, parameters: x.parameters } }));
  for (let i = 0; i < 8; i++) {
    const deepBody = { model: cfg.deepseekModel, messages, tools, tool_choice: 'auto', temperature: 0.2 };
    const response = await fetch(DEEPSEEK_URL, { method: 'POST', headers: { Authorization: `Bearer ${cfg.deepseekKey}`, 'Content-Type': 'application/json' }, body: JSON.stringify(deepBody), signal: AbortSignal.timeout(65000) });
    const body = await response.json().catch(() => ({}));
    if (!response.ok) { const e = new Error(body?.error?.message || `upstream_${response.status}`); e.http = response.status; throw e; }
    const messageOut = body?.choices?.[0]?.message;
    if (!messageOut) throw new Error('empty_response');
    if (!Array.isArray(messageOut.tool_calls) || !messageOut.tool_calls.length) return { provider: 'deepseek', model: cfg.deepseekModel, answer: String(messageOut.content || '').trim(), responseId: null, usedWeb: false, pendingAction: null };
    messages.push(messageOut);
    for (const toolCall of messageOut.tool_calls) {
      const args = JSON.parse(toolCall.function?.arguments || '{}');
      const result = await runTool(toolCall.function?.name, args);
      if (result.kind === 'client_action') return { provider: 'deepseek', model: cfg.deepseekModel, answer: 'هحتاج أنفذ الإجراء ده على الجهاز.', responseId: null, usedWeb: false, pendingAction: { ...result, toolCallId: toolCall.id } };
      messages.push({ role: 'tool', tool_call_id: toolCall.id, content: JSON.stringify(result.value) });
    }
  }
  throw new Error('agent_loop_limit');
}

export async function runAgent({ message, previousResponseId = '', device = '', preferredProvider = 'auto', reasoningEffort = 'none' }) {
  const normalizedEffort = ['none','minimal','low','medium','high','xhigh'].includes(String(reasoningEffort)) ? String(reasoningEffort) : 'none';
  const normalOrder = preferredProvider === 'openai' ? ['openai'] : preferredProvider === 'xai' ? ['xai'] : preferredProvider === 'deepseek' ? ['deepseek'] : ['openai', 'xai', 'deepseek'];
  const order = normalizedEffort !== 'none' && !preferredProvider && cfg.openaiKey ? ['openai', 'xai', 'deepseek'] : normalOrder;
  const attempts = [];
  for (const provider of order) {
    const key = provider === 'openai' ? cfg.openaiKey : provider === 'xai' ? cfg.xaiKey : cfg.deepseekKey;
    if (!key) { attempts.push({ provider, reason: 'not_configured' }); continue; }
    try {
      const output = provider === 'deepseek' ? await deepseekAgent(message, device, normalizedEffort) : await responsesAgent(provider, message, previousResponseId, device, normalizedEffort);
      if (!output.answer) throw new Error('empty_ai_response');
      return { ...output, reasoningEffort: normalizedEffort, attempts };
    } catch (e) {
      attempts.push({ provider, reason: String(e?.message || e), http: e?.http || null });
    }
  }
  const offline = runOffline(message);
  return {
    provider: 'offline',
    model: 'shadow-offline-engine',
    answer: offline.answer,
    responseId: null,
    usedWeb: false,
    pendingAction: null,
    offline: true,
    offlineCapability: offline.capability,
    reasoningEffort: normalizedEffort,
    attempts,
  };
}

export async function memoryStatus() {
  const ready = await ensureDb();
  return { database_configured: Boolean(cfg.databaseUrl), database_ready: ready, fallback_file: cfg.memoryDir, error: lastDbError || null };
}
export function providerStatus() {
  return {
    openai: { configured: Boolean(cfg.openaiKey), model: cfg.openaiModel },
    xai: { configured: Boolean(cfg.xaiKey), model: cfg.xaiModel },
    deepseek: { configured: Boolean(cfg.deepseekKey), model: cfg.deepseekModel },
    routing: 'openai -> xAI/Grok -> DeepSeek -> offline',
    offline: offlineStatus(),
    tools: TOOL_DEFINITIONS.map(x => x.name),
    workspace: cfg.workspaceDir,
    memory: { database_configured: Boolean(cfg.databaseUrl), database_ready: dbReady, fallback_file: cfg.memoryDir },
  };
}
