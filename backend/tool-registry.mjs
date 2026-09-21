import fs from 'node:fs/promises';
import path from 'node:path';

const WORKSPACE = path.resolve(process.env.SHADOW_WORKSPACE_DIR || '/data/shadow-workspace');
const REPO_RE = /^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/;
const URL_RE = /^https?:\/\//i;

function safeWorkspacePath(p) {
  const requested = String(p || '').replace(/^\/+/, '');
  if (!requested || requested.includes('..') || requested.includes('\\')) throw new Error('unsafe_path');
  const full = path.resolve(WORKSPACE, requested);
  if (full !== WORKSPACE && !full.startsWith(WORKSPACE + path.sep)) throw new Error('unsafe_path');
  return full;
}

function safeUrl(value) {
  if (!URL_RE.test(value)) throw new Error('invalid_url');
  const u = new URL(value);
  const host = u.hostname.toLowerCase();
  if (['localhost', '127.0.0.1', '0.0.0.0', '::1'].includes(host) || host.endsWith('.local')) throw new Error('private_host_blocked');
  if (/^(10|127)\./.test(host) || /^192\.168\./.test(host) || /^169\.254\./.test(host) || /^172\.(1[6-9]|2\d|3[0-1])\./.test(host)) throw new Error('private_host_blocked');
  return u;
}

async function webFetch(url) {
  const u = safeUrl(url);
  const r = await fetch(u, { headers: { 'User-Agent': 'SHADOW-WebAgent/1.0', Accept: 'text/html,application/json,text/plain;q=0.9,*/*;q=0.1' }, signal: AbortSignal.timeout(20000) });
  const text = await r.text();
  if (!r.ok) throw new Error(`web_${r.status}`);
  return { url: u.toString(), status: r.status, content_type: r.headers.get('content-type') || '', body: text.slice(0, 50000) };
}

async function webSearch(query) {
  const q = String(query || '').trim();
  if (!q || q.length > 500) throw new Error('query_required');
  const endpoint = new URL('https://html.duckduckgo.com/html/');
  endpoint.searchParams.set('q', q);
  const r = await fetch(endpoint, { headers: { 'User-Agent': 'SHADOW-WebAgent/1.0' }, signal: AbortSignal.timeout(20000) });
  const html = await r.text();
  if (!r.ok) throw new Error(`search_${r.status}`);
  const results = [];
  const re = /<a[^>]+class="result__a"[^>]+href="([^"]+)"[^>]*>(.*?)<\/a>/gi;
  let m;
  while ((m = re.exec(html)) && results.length < 10) {
    const title = m[2].replace(/<[^>]+>/g, '').replace(/&amp;/g, '&').trim();
    const url = m[1];
    if (title && URL_RE.test(url)) results.push({ title, url });
  }
  return { query: q, results };
}

async function fileRead(filePath) {
  const full = safeWorkspacePath(filePath);
  const stat = await fs.stat(full);
  if (!stat.isFile()) throw new Error('not_a_file');
  if (stat.size > 2_000_000) throw new Error('file_too_large');
  return { path: path.relative(WORKSPACE, full), size: stat.size, content: (await fs.readFile(full, 'utf8')).slice(0, 200000) };
}

async function fileWrite(filePath, content) {
  const full = safeWorkspacePath(filePath);
  const text = String(content || '');
  if (text.length > 500_000) throw new Error('file_too_large');
  await fs.mkdir(path.dirname(full), { recursive: true });
  await fs.writeFile(full, text, 'utf8');
  return { path: path.relative(WORKSPACE, full), bytes: Buffer.byteLength(text), verified: true };
}

export const TOOL_DEFINITIONS = [
  { type: 'function', name: 'calculator', description: 'Calculate safe arithmetic.', parameters: { type: 'object', properties: { expression: { type: 'string' } }, required: ['expression'], additionalProperties: false } },
  { type: 'function', name: 'web_search', description: 'Search the public web for current information.', parameters: { type: 'object', properties: { query: { type: 'string' } }, required: ['query'], additionalProperties: false } },
  { type: 'function', name: 'web_fetch', description: 'Fetch a public HTTP(S) web page without accessing private hosts.', parameters: { type: 'object', properties: { url: { type: 'string' } }, required: ['url'], additionalProperties: false } },
  { type: 'function', name: 'github_read', description: 'Read public GitHub repository metadata or a file.', parameters: { type: 'object', properties: { repo: { type: 'string' }, path: { type: 'string' } }, required: ['repo'], additionalProperties: false } },
  { type: 'function', name: 'memory_search', description: 'Search durable non-secret memory.', parameters: { type: 'object', properties: { query: { type: 'string' } }, required: ['query'], additionalProperties: false } },
  { type: 'function', name: 'memory_save', description: 'Save a useful non-secret fact or preference with provenance.', parameters: { type: 'object', properties: { fact: { type: 'string' }, reason: { type: 'string' }, evidence_level: { type: 'string', enum: ['fact','evidence','interpretation','conclusion'] }, source: { type: 'string' } }, required: ['fact'], additionalProperties: false } },
  { type: 'function', name: 'memory_forget', description: 'Forget a matching durable memory fact.', parameters: { type: 'object', properties: { query: { type: 'string' } }, required: ['query'], additionalProperties: false } },
  { type: 'function', name: 'file_read', description: 'Read a file from the SHADOW workspace only.', parameters: { type: 'object', properties: { path: { type: 'string' } }, required: ['path'], additionalProperties: false } },
  { type: 'function', name: 'file_write', description: 'Write a file into the SHADOW workspace. Use only after explicit authorization for mutations.', parameters: { type: 'object', properties: { path: { type: 'string' }, content: { type: 'string' } }, required: ['path', 'content'], additionalProperties: false } },
  { type: 'function', name: 'android_action', description: 'Request an Android action that the client can execute and verify.', parameters: { type: 'object', properties: { action: { type: 'string' }, argument: { type: 'string' }, reason: { type: 'string' }, requires_confirmation: { type: 'boolean' } }, required: ['action'], additionalProperties: false } },
];

export async function executeTool(name, args, helpers = {}) {
  if (name === 'calculator') return { kind: 'result', value: helpers.calc(args.expression) };
  if (name === 'web_search') return { kind: 'result', value: await webSearch(args.query) };
  if (name === 'web_fetch') return { kind: 'result', value: await webFetch(args.url) };
  if (name === 'github_read') return { kind: 'result', value: await helpers.githubRead(args.repo, args.path) };
  if (name === 'memory_search') return { kind: 'result', value: await helpers.memorySearch(args.query) };
  if (name === 'memory_save') return { kind: 'result', value: await helpers.memorySave(args.fact, args.reason || '', args.evidence_level || 'fact', args.source || 'user') };
  if (name === 'memory_forget') return { kind: 'result', value: await helpers.memoryForget(args.query) };
  if (name === 'file_read') return { kind: 'result', value: await fileRead(args.path) };
  if (name === 'file_write') {
    if (args.requires_confirmation !== true && helpers.requireWriteApproval) throw new Error('explicit_confirmation_required');
    return { kind: 'result', value: await fileWrite(args.path, args.content) };
  }
  if (name === 'android_action') return { kind: 'client_action', action: String(args.action || ''), argument: String(args.argument || ''), reason: String(args.reason || ''), requires_confirmation: Boolean(args.requires_confirmation) };
  throw new Error('unknown_tool');
}

export function registryStatus() {
  return {
    tools: TOOL_DEFINITIONS.map(x => x.name),
    workspace: WORKSPACE,
    web_search: true,
    web_fetch: true,
    github_read: true,
    files: true,
    memory: true,
    android_action: true,
  };
}
