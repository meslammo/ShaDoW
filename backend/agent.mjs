import fs from 'node:fs/promises';
import path from 'node:path';

const apiKey = String(process.env.OPENAI_API_KEY || '').trim();
const model = String(process.env.OPENAI_MODEL || 'gpt-5-mini').trim();
const memoryFile = process.env.SHADOW_MEMORY_FILE || '/tmp/shadow-memory.json';

const tools = [
  { type: 'web_search_preview' },
  {
    type: 'function', name: 'github_read', description: 'Read public GitHub repository/file information. Use for factual public GitHub lookups.',
    parameters: { type: 'object', properties: { url: { type: 'string', description: 'Public github.com or raw.githubusercontent.com URL' } }, required: ['url'], additionalProperties: false }
  },
  {
    type: 'function', name: 'memory_search', description: 'Search SHADOW long-term memory for user-approved facts/preferences. Never invent memories.',
    parameters: { type: 'object', properties: { query: { type: 'string' } }, required: ['query'], additionalProperties: false }
  },
  {
    type: 'function', name: 'memory_write', description: 'Persist a non-sensitive user-approved preference or project fact for future conversations.',
    parameters: { type: 'object', properties: { key: { type: 'string' }, value: { type: 'string' }, source: { type: 'string' } }, required: ['key','value'], additionalProperties: false }
  },
  {
    type: 'function', name: 'calculator', description: 'Calculate a mathematical expression safely.',
    parameters: { type: 'object', properties: { expression: { type: 'string' } }, required: ['expression'], additionalProperties: false }
  }
];

const instructions = [
  'You are SHADOW, a personal unified AI assistant.',
  'The user interacts with one command surface. Never expose a Local Chat mode and never ask the user to switch modes.',
  'Use the agent loop: understand -> plan -> tool -> observe -> verify -> continue until the task is complete or a required approval/credential is genuinely missing.',
  'For current/fresh information, use web search. For public GitHub facts, use github_read. For remembered user/project facts, use memory_search.',
  'Use memory_write only for durable, non-sensitive facts or preferences that are clearly useful. Never store passwords, API keys, tokens, authentication secrets, or sensitive personal data.',
  'Never claim a device action, message, GitHub write, file edit, purchase, or other external side effect happened unless a tool result actually confirms it.',
  'Answer Egyptian Arabic naturally when the user writes Arabic; use English when the user uses English.',
  'Be concise, direct, and transparent about limitations.'
].join(' ');

function extractText(body) {
  if (typeof body?.output_text === 'string' && body.output_text.trim()) return body.output_text.trim();
  const out = Array.isArray(body?.output) ? body.output : [];
  return out.flatMap(x => Array.isArray(x?.content) ? x.content : []).filter(x => x?.type === 'output_text' && typeof x.text === 'string').map(x => x.text).join('\n').trim();
}

async function readMemory() {
  try { const raw = await fs.readFile(memoryFile, 'utf8'); const data = JSON.parse(raw); return Array.isArray(data) ? data : []; }
  catch { return []; }
}
async function writeMemory(data) { await fs.mkdir(path.dirname(memoryFile), { recursive: true }); await fs.writeFile(memoryFile, JSON.stringify(data.slice(-500), null, 2), 'utf8'); }

function safeCalc(expression) {
  const x = String(expression || '').trim();
  if (!x || x.length > 200 || !/^[0-9+\-*/().%\s]+$/.test(x)) throw new Error('invalid_expression');
  const value = Function(`"use strict"; return (${x})`)();
  if (!Number.isFinite(value)) throw new Error('non_finite_result');
  return { expression: x, result: value };
}

async function executeTool(name, args) {
  if (name === 'github_read') {
    const url = String(args?.url || '').trim();
    if (!/^https:\/\/(github\.com|raw\.githubusercontent\.com)\//.test(url)) throw new Error('github_public_url_required');
    const response = await fetch(url, { headers: { 'Accept': 'application/vnd.github+json', 'User-Agent': 'SHADOW-Agent/1.0' }, signal: AbortSignal.timeout(15000) });
    const text = await response.text();
    if (!response.ok) throw new Error(`github_http_${response.status}`);
    return { status: response.status, content: text.slice(0, 16000) };
  }
  if (name === 'memory_search') {
    const q = String(args?.query || '').toLowerCase().trim();
    const memory = await readMemory();
    return { matches: memory.filter(m => `${m.key} ${m.value}`.toLowerCase().includes(q)).slice(-20) };
  }
  if (name === 'memory_write') {
    const key = String(args?.key || '').trim().slice(0, 200);
    const value = String(args?.value || '').trim().slice(0, 2000);
    const source = String(args?.source || 'user-approved').trim().slice(0, 200);
    if (!key || !value) throw new Error('memory_fields_required');
    if (/(password|passphrase|api[_ -]?key|token|secret|credential)/i.test(`${key} ${value}`)) throw new Error('sensitive_memory_blocked');
    const memory = await readMemory();
    const filtered = memory.filter(m => m.key !== key);
    filtered.push({ key, value, source, updated_at: new Date().toISOString() });
    await writeMemory(filtered);
    return { saved: true, key };
  }
  if (name === 'calculator') return safeCalc(args?.expression);
  throw new Error(`unknown_tool:${name}`);
}

export function agentStatus() { return { configured: Boolean(apiKey), model, tools: ['web_search', 'github_read', 'memory_search', 'memory_write', 'calculator'], memory_file: memoryFile }; }

export async function runAgent({ message, previousResponseId, deviceProfile = '', maxTurns = 8 }) {
  if (!apiKey) throw new Error('backend_not_configured');
  const input = deviceProfile ? `${message}\n\n[DEVICE_PROFILE]\n${deviceProfile}` : message;
  let responseId = previousResponseId || undefined;
  let requestInput = input;
  const actions = [];
  let lastBody = null;
  for (let turn = 0; turn < Math.max(1, Math.min(maxTurns, 8)); turn += 1) {
    const payload = { model, instructions, input: requestInput, tools, store: true };
    if (responseId) payload.previous_response_id = responseId;
    const upstream = await fetch('https://api.openai.com/v1/responses', {
      method: 'POST', headers: { Authorization: `Bearer ${apiKey}`, 'Content-Type': 'application/json' },
      body: JSON.stringify(payload), signal: AbortSignal.timeout(55000)
    });
    const body = await upstream.json().catch(() => ({}));
    lastBody = body;
    if (!upstream.ok) {
      const code = body?.error?.code || '';
      if (upstream.status === 429 && code === 'credit_balance_exhausted') throw new Error('ai_credits_exhausted');
      throw new Error(`upstream_ai_error_${upstream.status}`);
    }
    responseId = body.id || responseId;
    const calls = (Array.isArray(body.output) ? body.output : []).filter(x => x?.type === 'function_call');
    if (!calls.length) break;
    const outputs = [];
    for (const call of calls) {
      let args = {};
      try { args = JSON.parse(call.arguments || '{}'); } catch { args = {}; }
      try {
        const result = await executeTool(call.name, args);
        actions.push({ tool: call.name, ok: true });
        outputs.push({ type: 'function_call_output', call_id: call.call_id, output: JSON.stringify(result) });
      } catch (error) {
        const reason = String(error?.message || 'tool_failed').slice(0, 500);
        actions.push({ tool: call.name, ok: false, error: reason });
        outputs.push({ type: 'function_call_output', call_id: call.call_id, output: JSON.stringify({ ok: false, error: reason }) });
      }
    }
    requestInput = outputs;
  }
  const answer = extractText(lastBody);
  if (!answer) throw new Error('empty_ai_response');
  return { answer, response_id: responseId || null, model: lastBody?.model || model, actions, used_web_search: (Array.isArray(lastBody?.output) ? lastBody.output : []).some(x => x?.type === 'web_search_call') };
}
