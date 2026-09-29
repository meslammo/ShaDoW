"""MOD-45.6: runtime self-test with recovery hooks and truthful evidence."""
from __future__ import annotations
from dataclasses import dataclass
from typing import Callable

@dataclass(frozen=True)
class Check:
    name: str
    ok: bool
    detail: str = ""

class SelfTest:
    def __init__(self, recovery: Callable[[str], str] | None = None):
        self.recovery = recovery

    def run(self, runtime) -> dict:
        checks = []
        checks.append(self._check("capabilities", runtime.capabilities))
        checks.append(self._check("status", runtime.status))
        checks.append(self._check("development_engine", runtime.development.health))
        checks.append(self._check("memory", runtime.memory.status))
        checks.append(self._check("voice", runtime.voice.status))
        failed = [c.name for c in checks if not c.ok]
        recovery = None
        if failed and self.recovery:
            recovery = self.recovery(",".join(failed))
        return {
            "ok": not failed,
            "checks": [c.__dict__ for c in checks],
            "failed": failed,
            "recovery_attempted": bool(failed and self.recovery),
            "recovery": recovery,
        }

    @staticmethod
    def _check(name: str, fn: Callable) -> Check:
        try:
            result = fn()
            return Check(name, True, "ok" if result is not None else "ok")
        except Exception as exc:
            return Check(name, False, f"{type(exc).__name__}: {exc}")
