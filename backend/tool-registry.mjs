import fs from 'node:fs/promises';
import path from 'node:path';
import { applyFiles, createPullRequest } from './development-agent.mjs';
import { workspaceExec, workspaceWrite, workspaceClonePublicRepo } from './workspace-agent.mjs';

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

async function webResearch(query, limit = 5) {
  const search = await webSearch(query);
  const chosen = search.results.slice(0, Math.max(1, Math.min(Number(limit) || 5, 5)));
  const sources = [];
  for (const item of chosen) {
    try {
      const page = await webFetch(item.url);
      const title = item.title || item.url;
      const text = String(page.body || '').replace(/\s+/g, ' ').trim().slice(0, 12000);
      const authority = /\.(gov|edu)(\.|\/)/i.test(new URL(item.url).hostname) ? 4 : /(^|\.)github\.com$|(^|\.)microsoft\.com$|(^|\.)google\.com$|(^|\.)openai\.com$|(^|\.)anthropic\.com$/i.test(new URL(item.url).hostname) ? 3 : 1;
      sources.push({ title, url: item.url, http_status: page.status, authority_score: authority, excerpt: text.slice(0, 2500) });
    } catch (error) {
      sources.push({ title: item.title || item.url, url: item.url, fetch_error: String(error?.message || error) });
    }
  }
  sources.sort((a,b) => (b.authority_score || 0) - (a.authority_score || 0));
  return { query, sources, source_count: sources.length, fetched_count: sources.filter(x => x.http_status === 200).length };
}

async function fileRead(filePath) {
  const full = safeWorkspacePath(filePath);
  const stat = await fs.stat(full);
  if (!stat.isFile()) throw new Error('not_a_file');
  if (stat.size > 2_000_000) throw new Error('file_too_large');
  return { path: path.relative(WORKSPACE, full), size: stat.size, content: (await fs.readFile(full, 'utf8')).slice(0, 200000) };
}

function mutationDenied(action, helpers) {
  if (typeof helpers.authorizeMutation === 'function' && helpers.authorizeMutation(action) === true) return false;
  return true;
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
  { type: 'function', name: 'web_research', description: 'Search, fetch and rank a small set of public sources for research.', parameters: { type: 'object', properties: { query: { type: 'string' }, limit: { type: 'integer', minimum: 1, maximum: 5 } }, required: ['query'], additionalProperties: false } },
  { type: 'function', name: 'github_read', description: 'Read public GitHub repository metadata or a file.', parameters: { type: 'object', properties: { repo: { type: 'string' }, path: { type: 'string' } }, required: ['repo'], additionalProperties: false } },
  { type: 'function', name: 'memory_search', description: 'Search durable non-secret memory.', parameters: { type: 'object', properties: { query: { type: 'string' } }, required: ['query'], additionalProperties: false } },
  { type: 'function', name: 'memory_save', description: 'Save a useful non-secret fact or preference with provenance.', parameters: { type: 'object', properties: { fact: { type: 'string' }, reason: { type: 'string' }, evidence_level: { type: 'string', enum: ['fact','evidence','interpretation','conclusion'] }, source: { type: 'string' } }, required: ['fact'], additionalProperties: false } },
  { type: 'function', name: 'memory_forget', description: 'Forget a matching durable memory fact.', parameters: { type: 'object', properties: { query: { type: 'string' } }, required: ['query'], additionalProperties: false } },
  { type: 'function', name: 'file_read', description: 'Read a file from the SHADOW workspace only.', parameters: { type: 'object', properties: { path: { type: 'string' } }, required: ['path'], additionalProperties: false } },
  { type: 'function', name: 'file_write', description: 'Write a file into the SHADOW workspace. Use only after explicit authorization for mutations.', parameters: { type: 'object', properties: { path: { type: 'string' }, content: { type: 'string' } }, required: ['path', 'content'], additionalProperties: false } },
  { type: 'function', name: 'workspace_write', description: 'Create or replace source files in the private SHADOW workspace for an approved software task.', parameters: { type: 'object', properties: { path: { type: 'string' }, content: { type: 'string' } }, required: ['path', 'content'], additionalProperties: false } },
  { type: 'function', name: 'workspace_exec', description: 'Run a safe allowlisted build/test command in the private SHADOW workspace. No shell is used and destructive commands are blocked.', parameters: { type: 'object', properties: { command: { type: 'string' }, args: { type: 'array', items: { type: 'string' } }, cwd: { type: 'string' }, timeout_ms: { type: 'integer', minimum: 1000, maximum: 300000 } }, required: ['command'], additionalProperties: false } },
  { type: 'function', name: 'workspace_clone_public_repo', description: 'Clone a public GitHub repository into the private SHADOW workspace using a shallow clone.', parameters: { type: 'object', properties: { repo: { type: 'string' }, destination: { type: 'string' } }, required: ['repo'], additionalProperties: false } },
  { type: 'function', name: 'github_write_files', description: 'Apply generated files to the configured SHADOW GitHub repository. Requires explicit confirmation because it creates or updates repository content.', parameters: { type: 'object', properties: { branch: { type: 'string' }, commit_message: { type: 'string' }, files: { type: 'array', items: { type: 'object', properties: { path: { type: 'string' }, content: { type: 'string' } }, required: ['path','content'], additionalProperties: false } } }, required: ['files'], additionalProperties: false } },
  { type: 'function', name: 'github_open_pr', description: 'Open a GitHub pull request for an existing development branch after changes have been verified. Requires explicit confirmation.', parameters: { type: 'object', properties: { branch: { type: 'string' }, title: { type: 'string' }, body: { type: 'string' }, draft: { type: 'boolean' } }, required: ['branch','title'], additionalProperties: false } },
];

export async function executeTool(name, args, helpers = {}) {
  if (name === 'calculator') return { kind: 'result', value: helpers.calc(args.expression) };
  if (name === 'web_search') return { kind: 'result', value: await webSearch(args.query) };
  if (name === 'web_fetch') return { kind: 'result', value: await webFetch(args.url) };
  if (name === 'web_research') return { kind: 'result', value: await webResearch(args.query, args.limit) };
  if (name === 'github_read') return { kind: 'result', value: await helpers.githubRead(args.repo, args.path) };
  if (name === 'memory_search') return { kind: 'result', value: await helpers.memorySearch(args.query) };
  if (name === 'memory_save') {
    if (mutationDenied('memory_save', helpers)) throw new Error('explicit_confirmation_required');
    return { kind: 'result', value: await helpers.memorySave(args.fact, args.reason || '', args.evidence_level || 'fact', args.source || 'user') };
  }
  if (name === 'memory_forget') {
    if (mutationDenied('memory_forget', helpers)) throw new Error('explicit_confirmation_required');
    return { kind: 'result', value: await helpers.memoryForget(args.query) };
  }
  if (name === 'file_read') return { kind: 'result', value: await fileRead(args.path) };
  if (name === 'workspace_write') return { kind: 'result', value: await workspaceWrite(args.path, args.content) };
  if (name === 'workspace_exec') return { kind: 'result', value: await workspaceExec({ command: args.command, args: args.args || [], cwd: args.cwd || '.', timeoutMs: args.timeout_ms || 120000 }) };
  if (name === 'workspace_clone_public_repo') return { kind: 'result', value: await workspaceClonePublicRepo(args.repo, args.destination) };
  if (name === 'github_write_files') {
    if (mutationDenied('github_write_files', helpers)) throw new Error('explicit_confirmation_required');
    return { kind: 'result', value: await applyFiles({ branch: args.branch || 'shadow-agent-work', message: args.commit_message || 'SHADOW agent change', files: args.files || [], token: String(helpers.githubWriteToken || '').trim() }) };
  }
  if (name === 'github_open_pr') {
    if (mutationDenied('github_open_pr', helpers)) throw new Error('explicit_confirmation_required');
    return { kind: 'result', value: await createPullRequest({ branch: args.branch, title: args.title, body: args.body || '', draft: args.draft !== false, token: String(helpers.githubWriteToken || '').trim() }) };
  }
  if (name === 'file_write') {
    if (mutationDenied('file_write', helpers) || (args.requires_confirmation !== true && helpers.requireWriteApproval)) throw new Error('explicit_confirmation_required');
    return { kind: 'result', value: await fileWrite(args.path, args.content) };
  }
  throw new Error('unknown_tool');
}

export function registryStatus() {
  return {
    tools: TOOL_DEFINITIONS.map(x => x.name),
    workspace: WORKSPACE,
    web_search: true,
    web_fetch: true,
    web_research: true,
    github_read: true,
    files: true,
    memory: true,
    device_control: false,
    workspace_execution: true,
    github_write: true,
    github_pull_request: true,
  };
}
