"""MOD-46: recovery state and rollback contract."""
from dataclasses import dataclass

@dataclass(frozen=True)
class RecoveryResult:
    ok: bool
    action: str
    detail: str

class RecoveryManager:
    def __init__(self, workspace):
        self.workspace = str(workspace)
        self.last_known_good = None

    def checkpoint(self, identifier):
        self.last_known_good = str(identifier)
        return RecoveryResult(True, 'checkpoint', self.last_known_good)

    def failure(self, reason):
        return RecoveryResult(False, 'recover', f'{reason}; rollback={self.last_known_good}')
