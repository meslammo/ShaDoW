"""SHADOW MOD-52 — bounded orchestration state machine.

The orchestrator coordinates understanding/planning/execution/verification while
keeping external execution behind injected callables. It therefore remains safe
to run without credentials, network access, or device permissions.
"""
from __future__ import annotations

from dataclasses import dataclass, field
from time import time
from typing import Any, Callable, Dict, Optional

from .runtime_governance import ErrorType, GovernanceRuntime, Intent, Risk, State


@dataclass
class Task:
    task_id: str
    intent: Intent
    state: State = State.IDLE
    attempts: int = 0
    started_at: float = field(default_factory=time)
    result: Any = None
    error: Optional[Dict[str, Any]] = None


class ShadowOrchestrator:
    """Reference runtime for the Shadow 12-Core execution loop."""

    def __init__(self, governance: Optional[GovernanceRuntime] = None) -> None:
        self.gov = governance or GovernanceRuntime()
        self.tasks: Dict[str, Task] = {}

    def submit(self, task_id: str, intent: Intent) -> Task:
        task = Task(task_id=task_id, intent=intent)
        self.tasks[task_id] = task
        self.gov.record(task_id, "submit", State.IDLE, True, {"intent": intent.__dict__})
        return task

    def run(
        self,
        task_id: str,
        *,
        authenticated: bool,
        authorized: bool,
        executor: Callable[[Intent], Any],
        verifier: Callable[[Intent, Any], bool],
        impact: str = "",
    ) -> Task:
        task = self.tasks[task_id]
        task.started_at = time()
        self._transition(task, State.AUTHENTICATING)
        decision = self.gov.decide(task.intent.intent, authenticated, authorized, impact)
        if not decision.allowed:
            task.error = {"type": ErrorType.AUTH.value if not authenticated else ErrorType.PERMISSION.value,
                          "reason": decision.reason, "risk": decision.risk.value}
            self._transition(task, State.PAUSED)
            return task

        self._transition(task, State.UNDERSTANDING)
        self._transition(task, State.PLANNING)
        self.gov.checkpoint(task.task_id, State.PLANNING, {"expected_result": task.intent.expected_result})

        while True:
            self._transition(task, State.EXECUTING)
            try:
                task.result = executor(task.intent)
                self.gov.record(task.task_id, "execute", State.EXECUTING, True)
            except Exception as exc:  # fail closed and classify without leaking secrets
                task.attempts += 1
                task.error = {"type": ErrorType.UNKNOWN.value, "reason": str(exc)}
                self.gov.record(task.task_id, "execute", State.EXECUTING, False, task.error)
                if self.gov.can_retry(task.attempts, task.started_at):
                    continue
                self._transition(task, State.FAILED)
                return task

            self._transition(task, State.VERIFYING)
            try:
                verified = bool(verifier(task.intent, task.result))
            except Exception as exc:
                verified = False
                task.error = {"type": ErrorType.LOGIC.value, "reason": str(exc)}
            self.gov.record(task.task_id, "verify", State.VERIFYING, verified)
            if verified:
                self._transition(task, State.LEARNING)
                self._transition(task, State.DONE)
                return task

            task.attempts += 1
            task.error = {"type": ErrorType.DATA.value, "reason": "verification_failed"}
            if not self.gov.can_retry(task.attempts, task.started_at):
                self._transition(task, State.FAILED)
                return task

    def _transition(self, task: Task, state: State) -> None:
        task.state = state
        self.gov.record(task.task_id, "state", state, True)
