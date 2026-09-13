"""MOD-46: server-side GitHub execution gateway contract.
No credential is stored in SHADOW Android or project files.
"""
from dataclasses import dataclass

@dataclass(frozen=True)
class GitHubOperation:
    repository: str
    branch: str
    action: str
    approved: bool = False

class GitHubGateway:
    def __init__(self, transport=None):
        self.transport = transport

    def execute(self, operation, payload=None):
        if not operation.approved:
            return {'ok': False, 'reason': 'explicit_approval_required'}
        if self.transport is None:
            return {'ok': False, 'reason': 'server_transport_not_connected'}
        return self.transport(operation, payload or {})
