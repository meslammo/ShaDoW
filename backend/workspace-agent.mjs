import fs from 'node:fs/promises';
import path from 'node:path';
import { spawn } from 'node:child_process';

const WORKSPACE = path.resolve(process.env.SHADOW_WORKSPACE_DIR || path.join(process.cwd(), '.shadow-workspace'));
const MAX_OUTPUT = 120_000;
const DEFAULT_TIMEOUT = 120_000;
const MAX_TIMEOUT = 300_000;

function safeRelative(p) {
  const requested = String(p || '').replace(/^\/+/, '');
  if (!requested || requested.includes('..') || requested.includes('\\')) throw new Error('unsafe_path');
  const full = path.resolve(WORKSPACE, requested);
  if (full !== WORKSPACE && !full.startsWith(WORKSPACE + path.sep)) throw new Error('unsafe_path');
  return { requested, full };
}

function normalizeArgs(args) {
  if (!Array.isArray(args) || args.some(x => typeof x !== 'string' || x.length > 500)) throw new Error('invalid_command_args');
  return args;
}

function validateGitArgs(args) {
  const action = args[0] || '';
  if (['status', 'diff', 'log', 'show'].includes(action)) return;
  if (action === 'branch' && (args[1] === '--show-current' || args.length === 1)) return;
  if (action === 'rev-parse' && ['--show-toplevel', 'HEAD'].includes(args[1])) return;
  if (action === 'clone') {
    const url = String(args.at(-2) || '');
    const dest = String(args.at(-1) || '');
    if (!/^https:\/\/github\.com\/[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+(?:\.git)?$/.test(url)) throw new Error('github_clone_only');
    safeRelative(dest);
    return;
  }
  throw new Error('git_mutation_blocked');
}

function validateCommand(command, args) {
  const base = path.basename(command);
  if (base === 'git') { validateGitArgs(args); return; }
  if (base === 'gradle' || base === 'gradlew') {
    if (!args.length) throw new Error('build_command_required');
    if (!args.every(x => !/[;&|$<>]/.test(x))) throw new Error('unsafe_command');
    const allowed = /^(assemble|test|check|lint|bundle|dependencies|projects|tasks)(?::[A-Za-z0-9_.-]+)*$/;
    if (!args.every(x => allowed.test(x) || /^--(?:no-daemon|stacktrace|info|warning-mode(?:=|$)[A-Za-z-]+)$/.test(x))) throw new Error('gradle_action_blocked');
    return;
  }
  if (base === 'flutter') {
    if (!args.length || !args.every(x => !/[;&|$<>]/.test(x))) throw new Error('unsafe_command');
    if (!['pub', 'test', 'analyze', 'build'].includes(args[0])) throw new Error('flutter_action_blocked');
    return;
  }
  if (base === 'dart') {
    if (!args.length || !args.every(x => !/[;&|$<>]/.test(x))) throw new Error('unsafe_command');
    if (!['test', 'analyze', 'format'].includes(args[0])) throw new Error('dart_action_blocked');
    return;
  }
  if (base === 'npm') {
    const first = args[0];
    if (!['test', 'run', 'build', 'ci', 'install'].includes(first)) throw new Error('npm_action_blocked');
    if (!args.every(x => !/[;&|$<>]/.test(x))) throw new Error('unsafe_command');
    return;
  }
  if (base === 'python' || base === 'python3') {
    if (!args.length || !args.every(x => !/[;&|$<>]/.test(x))) throw new Error('unsafe_command');
    if (args[0] === '-m' && ['pytest', 'compileall'].includes(args[1])) return;
    if (!args[0].startsWith('-')) { safeRelative(args[0]); return; }
    throw new Error('python_action_blocked');
  }
  if (base === 'pytest') {
    if (!args.every(x => !/[;&|$<>]/.test(x))) throw new Error('unsafe_command');
    return;
  }
  if (base === 'node') {
    if (!args.length || !args.every(x => !/[;&|$<>]/.test(x))) throw new Error('unsafe_command');
    if (args.some(x => ['-e', '--eval', '--print', '-p', '--require'].includes(x))) throw new Error('node_eval_blocked');
    safeRelative(args.find(x => !x.startsWith('-')) || '');
    return;
  }
  throw new Error('command_not_allowed');
}

async function runProcess(command, args, cwd, timeoutMs) {
  return await new Promise((resolve, reject) => {
    const child = spawn(command, args, { cwd, shell: false, windowsHide: true, env: { PATH: process.env.PATH || '', HOME: process.env.HOME || '', CI: '1', NODE_ENV: 'test' } });
    let stdout = '';
    let stderr = '';
    let finished = false;
    const append = (kind, chunk) => {
      const value = String(chunk || '');
      if (kind === 'stdout') stdout = (stdout + value).slice(-MAX_OUTPUT);
      else stderr = (stderr + value).slice(-MAX_OUTPUT);
    };
    child.stdout.on('data', chunk => append('stdout', chunk));
    child.stderr.on('data', chunk => append('stderr', chunk));
    const timer = setTimeout(() => {
      if (finished) return;
      finished = true;
      child.kill('SIGTERM');
      setTimeout(() => child.kill('SIGKILL'), 2000).unref();
      resolve({ ok: false, code: null, signal: 'SIGTERM', timed_out: true, stdout, stderr });
    }, timeoutMs);
    child.on('error', error => {
      if (finished) return;
      finished = true;
      clearTimeout(timer);
      reject(error);
    });
    child.on('close', (code, signal) => {
      if (finished) return;
      finished = true;
      clearTimeout(timer);
      resolve({ ok: code === 0, code, signal, timed_out: false, stdout, stderr });
    });
  });
}

export async function workspaceWrite(filePath, content) {
  const { full, requested } = safeRelative(filePath);
  const text = String(content || '');
  if (text.length > 500_000) throw new Error('file_too_large');
  await fs.mkdir(path.dirname(full), { recursive: true });
  await fs.writeFile(full, text, 'utf8');
  return { ok: true, path: requested, bytes: Buffer.byteLength(text), verified: true };
}

export async function workspaceExec({ command, args = [], cwd = '.', timeoutMs = DEFAULT_TIMEOUT }) {
  const safeArgs = normalizeArgs(args);
  validateCommand(command, safeArgs);
  if (cwd !== '.') safeRelative(cwd);
  await fs.mkdir(WORKSPACE, { recursive: true });
  const workdir = cwd === '.' ? WORKSPACE : safeRelative(cwd).full;
  const timeout = Math.max(1000, Math.min(Number(timeoutMs) || DEFAULT_TIMEOUT, MAX_TIMEOUT));
  const result = await runProcess(command, safeArgs, workdir, timeout);
  return { command: [command, ...safeArgs], cwd: cwd === '.' ? '.' : cwd, timeout_ms: timeout, ...result };
}

export async function workspaceClonePublicRepo(repo, destination) {
  const clean = String(repo || '').trim();
  if (!/^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/.test(clean)) throw new Error('invalid_repo');
  const dest = String(destination || clean.split('/').pop() || 'repo').trim();
  safeRelative(dest);
  return workspaceExec({
    command: 'git',
    args: ['clone', '--depth', '1', 'https://github.com/' + clean + '.git', dest],
    cwd: '.',
    timeoutMs: 180000,
  });
}
