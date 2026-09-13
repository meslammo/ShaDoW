"""MOD-46: safe execution bridge contract for the Development Engine."""
from dataclasses import dataclass

@dataclass(frozen=True)
class BridgeResult:
    ok: bool
    action: str
    detail: str
    evidence: tuple[str, ...] = ()

class ExecutionBridge:
    def __init__(self, executor):
        self.executor = executor

    def execute(self, action: str, approved: bool = False) -> BridgeResult:
        if not approved:
            return BridgeResult(False, action, "explicit approval required")
        return BridgeResult(False, action, "executor adapter must be connected by the trusted runtime")

    def health(self) -> dict[str, object]:
        return {"bridge": "ready", "credential_storage": False, "approval_required": True, "execution_adapter_connected": False}
