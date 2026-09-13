// MOD-46: server-side GitHub bridge boundary.
// Credentials must be injected by the hosting environment and never shipped in the APK.
export function createGitHubBridge(transport) {
  return async function execute(operation) {
    if (!operation || operation.approved !== true) {
      return { ok: false, reason: 'explicit_approval_required' };
    }
    if (typeof transport !== 'function') {
      return { ok: false, reason: 'github_transport_not_configured' };
    }
    return transport(operation);
  };
}
