"""MOD-47.1: deterministic governance primitives for the SHADOW 12-Core runtime.

The control plane keeps identity, intent, risk, evidence, recovery, lifecycle and
learning boundaries explicit so higher-level AI components can remain replaceable.
"""
from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum
from time import time
from typing import Any, Iterable


class State(str, Enum):
    IDLE="idle"; LISTENING="listening"; AUTHENTICATING="authenticating"; UNDERSTANDING="understanding"
    PLANNING="planning"; EXECUTING="executing"; VERIFYING="verifying"; LEARNING="learning"
    DONE="done"; FAILED="failed"; PAUSED="paused"


class ErrorKind(str, Enum):
    AUTH="auth"; PERMISSION="permission"; NETWORK="network"; DEVICE="device"; TOOL="tool"
    LOGIC="logic"; DATA="data"; TIMEOUT="timeout"; UNKNOWN="unknown"


class EvidenceKind(str, Enum):
    FACT="fact"; EVIDENCE="evidence"; INTERPRETATION="interpretation"; CONCLUSION="conclusion"


@dataclass(frozen=True)
class Identity:
    subject: str
    voice_verified: bool = False
    passphrase_verified: bool = False
    extra_factor_verified: bool = False
    master: bool = False

    @property
    def verified(self) -> bool:
        return self.voice_verified and self.passphrase_verified


@dataclass(frozen=True)
class Intent:
    name: str
    goal: str
    constraints: tuple[str, ...] = ()
    expected_result: str = ""


@dataclass(frozen=True)
class Command:
    id: str
    intent: Intent
    priority: int = 0
    scope: str = "default"
    created_at: float = field(default_factory=time)


@dataclass(frozen=True)
class Risk:
    score: int
    blast_radius: int

    @property
    def level(self) -> str:
        value = max(self.score, self.blast_radius)
        return "low" if value < 30 else "medium" if value < 70 else "high"


@dataclass
class Checkpoint:
    id: str
    state: State
    before: dict[str, Any] = field(default_factory=dict)
    after: dict[str, Any] = field(default_factory=dict)
    journal: list[str] = field(default_factory=list)


@dataclass(frozen=True)
class Source:
    name: str
    trust: int = 50
    freshness: int = 50
    provenance: str = "unknown"

    @property
    def weight(self) -> int:
        return max(0, min(100, (self.trust + self.freshness) // 2))


@dataclass(frozen=True)
class Adapter:
    name: str
    version: str
    compatible: bool = True
    healthy: bool = True
    rollback_version: str | None = None


@dataclass(frozen=True)
class Companion:
    name: str
    lifecycle: str = "discover"
    trust_score: int = 0


@dataclass(frozen=True)
class LearningItem:
    kind: EvidenceKind
    value: str
    authorized: bool = True
    sensitive: bool = False


@dataclass
class RecoveryPolicy:
    max_attempts: int = 3
    max_seconds: float = 120.0
    require_progress: bool = True


class Governance:
    """Single policy surface used by runtime orchestration and future adapters."""

    def __init__(self) -> None:
        self.state = State.IDLE
        self.identity: Identity | None = None
        self.checkpoints: list[Checkpoint] = []
        self.journal: list[str] = []
        self.recovery = RecoveryPolicy()
        self._attempts = 0

    # 1, 2, 3, 4, 5, 6: identity, conflicts, state, resume, intent, missing info.
    def authenticate(self, identity: Identity) -> bool:
        self.state = State.AUTHENTICATING
        self.identity = identity
        return identity.verified

    def choose_command(self, commands: Iterable[Command]) -> Command | None:
        ordered = sorted(commands, key=lambda c: (c.priority, c.created_at), reverse=True)
        return ordered[0] if ordered else None

    def transition(self, state: State) -> None:
        self.state = state
        self.journal.append(f"state:{state.value}")

    def checkpoint(self, checkpoint_id: str, before: dict[str, Any] | None = None) -> Checkpoint:
        cp = Checkpoint(checkpoint_id, self.state, before or {})
        self.checkpoints.append(cp)
        self.journal.append(f"checkpoint:{checkpoint_id}")
        return cp

    def resume(self) -> State:
        if not self.checkpoints:
            return self.state
        self.state = self.checkpoints[-1].state
        return self.state

    @staticmethod
    def intent(name: str, goal: str, constraints: Iterable[str] = (), expected_result: str = "") -> Intent:
        return Intent(name, goal, tuple(constraints), expected_result)

    @staticmethod
    def missing_info(intent: Intent, known: dict[str, Any]) -> list[str]:
        required = {"goal": intent.goal, "expected_result": intent.expected_result}
        return [key for key, value in required.items() if value and not known.get(key)]

    # 7, 8, 9, 10: risk, rollback, verification, typed errors.
    @staticmethod
    def risk(score: int, blast_radius: int) -> Risk:
        return Risk(max(0, min(100, score)), max(0, min(100, blast_radius)))

    def record_result(self, checkpoint: Checkpoint, result: dict[str, Any]) -> None:
        checkpoint.after = dict(result)
        self.journal.append(f"result:{checkpoint.id}")

    def rollback(self, checkpoint: Checkpoint) -> dict[str, Any]:
        self.state = checkpoint.state
        self.journal.append(f"rollback:{checkpoint.id}")
        return dict(checkpoint.before)

    @staticmethod
    def verify(expected: Any, actual: Any) -> bool:
        return expected == actual

    @staticmethod
    def error(kind: ErrorKind, message: str, recoverable: bool = True) -> dict[str, Any]:
        return {"kind": kind.value, "message": message, "recoverable": recoverable}

    # 11, 12, 13: bounded recovery, deadlines, source conflict resolution.
    def attempt(self, started_at: float, progressed: bool = True) -> bool:
        self._attempts += 1
        elapsed = time() - started_at
        if self._attempts > self.recovery.max_attempts or elapsed > self.recovery.max_seconds:
            return False
        return progressed or not self.recovery.require_progress

    @staticmethod
    def deadline(deadline: float | None, now: float | None = None) -> dict[str, Any]:
        current = time() if now is None else now
        return {"deadline": deadline, "remaining": None if deadline is None else deadline - current,
                "urgent": deadline is not None and deadline - current <= 60}

    @staticmethod
    def resolve_sources(sources: Iterable[Source]) -> Source | None:
        values = list(sources)
        return max(values, key=lambda s: s.weight, default=None)

    # 14, 15: adapter and companion lifecycle.
    @staticmethod
    def adapter_ready(adapter: Adapter) -> bool:
        return adapter.compatible and adapter.healthy

    @staticmethod
    def companion_transition(companion: Companion, event: str) -> Companion:
        flow = {"authenticate":"authenticate", "trust":"trust", "permission":"permission", "use":"use",
                "evaluate":"evaluate", "promote":"promote", "demote":"demote", "revoke":"revoke"}
        return Companion(companion.name, flow.get(event, companion.lifecycle), companion.trust_score)

    # 16, 17: bounded learning and emergency recovery.
    @staticmethod
    def learn(item: LearningItem) -> bool:
        return item.authorized and not item.sensitive

    def emergency_shutdown(self, reason: str) -> dict[str, Any]:
        cp = self.checkpoint("emergency", {"state": self.state.value})
        self.state = State.PAUSED
        self.journal.append(f"emergency:{reason}")
        return {"safe": True, "reason": reason, "checkpoint": cp.id, "state": self.state.value}

    def status(self) -> dict[str, Any]:
        return {"state": self.state.value, "identity_verified": bool(self.identity and self.identity.verified),
                "checkpoints": len(self.checkpoints), "journal_entries": len(self.journal),
                "recovery_attempts": self._attempts, "fail_closed": True}
