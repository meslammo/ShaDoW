import assert from 'node:assert/strict';
import { applyFiles, createPullRequest } from './development-agent.mjs';

const actionToken = String(process.env.SHADOW_GITHUB_TOKEN || '').trim();
const prToken = String(process.env.SHADOW_GITHUB_PR_TOKEN || '').trim();
const repoName = String(process.env.SHADOW_GITHUB_REPO || 'meslammo/ShaDoW').trim();
const runId = String(process.env.GITHUB_RUN_ID || Date.now());
const branch = 'shadow-e2e/' + runId;
const markerPath = 'tests/.shadow-github-e2e-' + runId + '.md';

if (!actionToken) throw new Error('SHADOW_GITHUB_TOKEN_REQUIRED');

async function gh(path, options = {}, token = actionToken) {
  const response = await fetch('https://api.github.com' + path, {
    ...options,
    headers: {
      Authorization: 'Bearer ' + token,
      Accept: 'application/vnd.github+json',
      'X-GitHub-Api-Version': '2022-11-28',
      'Content-Type': 'application/json',
      ...(options.headers || {}),
    },
    signal: AbortSignal.timeout(30000),
  });
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error('github_' + response.status + ':' + String(body?.message || 'request_failed').slice(0, 160));
  return body;
}

let pullRequestNumber = null;
try {
  const applied = await applyFiles({
    branch,
    message: 'E2E: temporary authenticated SHADOW Development Agent write',
    files: [{ path: markerPath, content: '# SHADOW GitHub E2E\n\nTemporary test marker; branch is deleted after verification.\n' }],
    token: actionToken,
  });
  assert.equal(applied.branch, branch);
  assert.equal(applied.files?.length, 1);
  assert.equal(applied.files[0]?.path, markerPath);

  const verified = await gh('/repos/' + repoName + '/contents/' + markerPath + '?ref=' + encodeURIComponent(branch));
  assert.equal(verified?.name, markerPath.split('/').at(-1));

  if (!prToken) {
    console.log('GITHUB_DEVELOPMENT_WRITE_E2E_OK branch=' + branch + ' pr_creation=blocked_without_pat');
  } else {
    const pr = await createPullRequest({
      branch,
      title: 'SHADOW authenticated Development Agent E2E ' + runId,
      body: 'Temporary automated E2E PR. It is closed and its branch is deleted after verification.',
      token: prToken,
      draft: true,
    });
    pullRequestNumber = pr.number;
    assert.ok(pullRequestNumber);
    assert.equal(pr.head?.length > 0, true);

    const fetched = await gh('/repos/' + repoName + '/pulls/' + pullRequestNumber, {}, prToken);
    assert.equal(fetched?.head?.ref, branch);
    assert.equal(fetched?.base?.ref, 'main');
    assert.equal(fetched?.draft, true);

    console.log('GITHUB_DEVELOPMENT_E2E_OK pr=' + pullRequestNumber + ' branch=' + branch);
  }
} finally {
  const cleanupToken = prToken || actionToken;
  if (pullRequestNumber && prToken) {
    await gh('/repos/' + repoName + '/pulls/' + pullRequestNumber, {
      method: 'PATCH',
      body: JSON.stringify({ state: 'closed' }),
    }, prToken).catch(() => {});
  }
  await gh('/repos/' + repoName + '/git/refs/heads/' + encodeURIComponent(branch), {
    method: 'DELETE',
  }, cleanupToken).catch(() => {});
}
