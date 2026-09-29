"""SHADOW MOD-52 — deterministic governance primitives for the 12-Core runtime.

This module is intentionally dependency-free. It does not execute external actions;
it decides whether an action may proceed, records checkpoints/journal entries, and
provides bounded retry/rollback metadata for the higher-level orchestrator.
"""
from __future__ import annotations

from dataclasses import dataclass, field, asdict
from enum import Enum
from time import time
from typing import Any, Dict, List, Optional


class Risk(str, Enum):
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"
    CRITICAL = "CRITICAL"


class State(str, Enum):
    IDLE = "Idle"
    LISTENING = "Listening"
    AUTHENTICATING = "Authenticating"
    UNDERSTANDING = "Understanding"
    PLANNING = "Planning"
    EXECUTING = "Executing"
    VERIFYING = "Verifying"
    LEARNING = "Learning"
    DONE = "Done"
    FAILED = "Failed"
    PAUSED = "Paused"


class ErrorType(str, Enum):
    AUTH = "Auth"
    PERMISSION = "Permission"
    NETWORK = "Network"
    DEVICE = "Device"
    TOOL = "Tool"
    LOGIC = "Logic"
    DATA = "Data"
    TIMEOUT = "Timeout"
    UNKNOWN = "Unknown"


@dataclass(frozen=True)
class Intent:
    intent: str
    goal: str
    constraints: Dict[str, Any] = field(default_factory=dict)
    expected_result: str = ""


@dataclass
class Checkpoint:
    task_id: str
    state: State
    created_at: float = field(default_factory=time)
    payload: Dict[str, Any] = field(default_factory=dict)


@dataclass
class JournalEntry:
    task_id: str
    event: str
    state: State
    ok: bool
    timestamp: float = field(default_factory=time)
    detail: Dict[str, Any] = field(default_factory=dict)


@dataclass
class GovernanceDecision:
    allowed: bool
    risk: Risk
    confirmation_required: bool
    reason: str


class GovernanceRuntime:
    """Fail-closed policy engine and execution ledger."""

    def __init__(self, retry_budget: int = 3, time_budget_s: float = 120.0) -> None:
        self.retry_budget = max(0, retry_budget)
        self.time_budget_s = max(1.0, time_budget_s)
        self.checkpoints: Dict[str, Checkpoint] = {}
        self.journal: List[JournalEntry] = []

    @staticmethod
    def classify_risk(action: str, impact: str = "") -> Risk:
        text = f"{action} {impact}".lower()
        if any(k in text for k in ("delete", "wipe", "payment", "credential", "key", "factory reset")):
            return Risk.CRITICAL
        if any(k in text for k in ("merge", "deploy", "publish", "call", "message", "github", "write")):
            return Risk.HIGH
        if any(k in text for k in ("install", "edit", "change", "open", "trade")):
            return Risk.MEDIUM
        return Risk.LOW

    def decide(self, action: str, authenticated: bool, authorized: bool, impact: str = "") -> GovernanceDecision:
        risk = self.classify_risk(action, impact)
        if not authenticated:
            return GovernanceDecision(False, risk, True, "authentication_required")
        if not authorized:
            return GovernanceDecision(False, risk, True, "authorization_required")
        if risk == Risk.CRITICAL:
            return GovernanceDecision(False, risk, True, "explicit_confirmation_and_strong_auth_required")
        if risk == Risk.HIGH:
            return GovernanceDecision(False, risk, True, "explicit_confirmation_required")
        return GovernanceDecision(True, risk, False, "policy_allow")

    def checkpoint(self, task_id: str, state: State, payload: Optional[Dict[str, Any]] = None) -> Checkpoint:
        cp = Checkpoint(task_id, state, payload=payload or {})
        self.checkpoints[task_id] = cp
        self.record(task_id, "checkpoint", state, True, {"checkpoint": asdict(cp)})
        return cp

    def record(self, task_id: str, event: str, state: State, ok: bool, detail: Optional[Dict[str, Any]] = None) -> None:
        self.journal.append(JournalEntry(task_id, event, state, ok, detail=detail or {}))

    def can_retry(self, attempts: int, started_at: float) -> bool:
        return attempts < self.retry_budget and (time() - started_at) < self.time_budget_s

    def recovery(self, task_id: str) -> Optional[Checkpoint]:
        return self.checkpoints.get(task_id)

    def export_audit(self) -> Dict[str, Any]:
        return {
            "retry_budget": self.retry_budget,
            "time_budget_s": self.time_budget_s,
            "checkpoints": {k: asdict(v) for k, v in self.checkpoints.items()},
            "journal": [asdict(v) for v in self.journal],
        }
