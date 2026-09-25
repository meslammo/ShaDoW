"""Unified 150-Core orchestration boundary for SHADOW.

The orchestrator keeps the existing 12-Core root and 150-Core catalog, while
binding memory, governance, tools, online brain, audit and verification into
one governed execution loop.
"""
from __future__ import annotations

from dataclasses import dataclass, field
import re
from typing import Any, Callable, Mapping, Optional
from uuid import uuid4

from .catalog import CORE_BY_ID
from .contracts import CoreRequest, CoreResult
from .integration import SuperNiceRuntime
from .evolution import UnifiedControlPlane
from .online_brain import GovernedOnlineBrainAdapter, RealOnlineBrainAdapter
from shadow.core.engine import ShadowEngine
from shadow.core.live_update import LiveUpdateManager


@dataclass(frozen=True)
class OrchestratorEvent:
    stage: str
    core_id: Optional[str]
    status: str
    detail: str = ""


@dataclass
class UnifiedRunResult:
    ok: bool
    status: str
    answer: str = ""
    selected_core: Optional[str] = None
    events: list[OrchestratorEvent] = field(default_factory=list)
    evidence: list[dict[str, Any]] = field(default_factory=list)
    metadata: dict[str, Any] = field(default_factory=dict)

    def to_dict(self) -> dict[str, Any]:
        return {
            "ok": self.ok,
            "status": self.status,
            "answer": self.answer,
            "selected_core": self.selected_core,
            "events": [
                {"stage": e.stage, "core_id": e.core_id, "status": e.status, "detail": e.detail}
                for e in self.events
            ],
            "evidence": list(self.evidence),
            "metadata": dict(self.metadata),
        }


OnlineBrain = Callable[[str, Mapping[str, Any]], Mapping[str, Any]]


class Unified150Orchestrator:
    """One orchestration surface for the complete 150-Core catalog."""

    def __init__(self, workspace: str = ".", online_brain: Optional[OnlineBrain] = None) -> None:
        self.workspace = workspace
        self.runtime = SuperNiceRuntime(workspace)
        self.control = UnifiedControlPlane(workspace)
        self.online_brain = online_brain or GovernedOnlineBrainAdapter(self.control)
        self.engine = ShadowEngine(workspace)
        self.live_updates = LiveUpdateManager(workspace)

    @property
    def core_count(self) -> int:
        return len(CORE_BY_ID)

    def execute_core(
        self,
        core_id: str,
        request: str,
        *,
        context: Mapping[str, Any] | None = None,
        confirmed: bool = False,
    ) -> CoreResult:
        return self.runtime.execute(
            core_id,
            request,
            context=dict(context or {}),
            confirmed=bool(confirmed),
        )

    def audit_reachability(self, *, confirmed: bool = True) -> dict[str, Any]:
        results: list[dict[str, Any]] = []
        for core_id in sorted(CORE_BY_ID):
            result = self.execute_core(
                core_id,
                "integration reachability audit",
                context={"source": "unified_orchestrator_audit", "audit_only": True},
                confirmed=confirmed,
            )
            results.append({
                "core_id": core_id,
                "ok": result.ok,
                "status": result.status,
                "metadata": result.metadata,
            })
        passed = sum(1 for row in results if row["ok"])
        return {
            "core_count": self.core_count,
            "reachable": passed,
            "unreachable": self.core_count - passed,
            "all_reachable": passed == self.core_count,
            "results": results,
        }

    @staticmethod
    def select_core(request: str) -> str:
        text = str(request or "").lower()
        if any(x in text for x in ("github", "git ", "repo", "repository", "جيت هاب", "كود", "برمجة")):
            return "CORE-081"
        if any(x in text for x in ("ابحث", "search", "latest", "news", "update", "آخر", "اخر", "النهارده")):
            return "CORE-065"
        if any(x in text for x in ("صورة", "image", "photo", "camera", "vision")):
            return "CORE-099"
        if any(x in text for x in ("موبايل", "تليفون", "phone", "android", "device", "افتح", "اضغط")):
            return "CORE-057"
        if any(x in text for x in ("ذاكرة", "memory", "تفتكر", "remember", "forget")):
            return "CORE-025"
        return "CORE-024"

    @staticmethod
    def _needs_governance(request: str, core_id: str) -> bool:
        spec = CORE_BY_ID[core_id]
        risky = bool(re.search(
            r"(delete|wipe|format|payment|purchase|send|transfer|deploy|commit|push|merge|"
            r"حذف|امسح|فورمات|شراء|دفع|تحويل|نفذ|نفّذ|اتصال|اتصل|رسالة)",
            request,
            re.I,
        ))
        return bool(spec.requires_confirmation or risky)

    def run(
        self,
        request: str,
        *,
        online_brain: Optional[OnlineBrain] = None,
        context: Mapping[str, Any] | None = None,
        confirmed: bool = False,
        selected_core: Optional[str] = None,
    ) -> UnifiedRunResult:
        text = str(request or "").strip()
        events: list[OrchestratorEvent] = []
        evidence: list[dict[str, Any]] = []
        request_id = str((context or {}).get("request_id") or uuid4())
        turn_id = self.engine.begin_turn(request_id)
        ctx = {**dict(context or {}), "request_id": request_id, "turn_id": turn_id, "confirmed": bool(confirmed), "engine_state": self.engine.snapshot()}

        if not text:
            return UnifiedRunResult(False, "request_required", metadata={"request_id": request_id})

        self.control.audit("request.received", request_id=request_id, metadata={"core_count": self.core_count})
        self.control_recall = self.control.recall(text, 8)
        ctx["memory"] = self.control_recall

        events.append(OrchestratorEvent("understand", "CORE-003", "started"))

        identity = self.execute_core(
            "CORE-001",
            text,
            context={"source": "unified_orchestrator", **ctx},
            confirmed=confirmed,
        )
        events.append(OrchestratorEvent("identity", "CORE-001", identity.status))
        if not identity.ok:
            self.engine.mark_degraded("identity")
            self.control.audit("request.failed", request_id=request_id, outcome="failed", metadata={"stage": "identity"})
            return UnifiedRunResult(False, identity.status, events=events, metadata={"request_id": request_id})

        reasoning = self.execute_core(
            "CORE-003",
            text,
            context={"source": "unified_orchestrator", **ctx},
            confirmed=confirmed,
        )
        events.append(OrchestratorEvent("reasoning", "CORE-003", reasoning.status))
        if not reasoning.ok:
            self.engine.mark_degraded("reasoning")
            self.control.audit("request.failed", request_id=request_id, outcome="failed", metadata={"stage": "reasoning"})
            return UnifiedRunResult(False, reasoning.status, events=events, metadata={"request_id": request_id})

        routing = self.execute_core(
            "CORE-013",
            text,
            context={"capability": "chat", **ctx},
            confirmed=confirmed,
        )
        events.append(OrchestratorEvent("model_route", "CORE-013", routing.status))
        if not routing.ok:
            self.engine.mark_degraded("model_route")
            self.control.audit("request.failed", request_id=request_id, outcome="failed", metadata={"stage": "model_route"})
            return UnifiedRunResult(False, routing.status, events=events, metadata={"request_id": request_id})

        memory_result = self.execute_core(
            "CORE-025",
            text,
            context={"source": "unified_orchestrator", "memory_hits": len(self.control_recall), **ctx},
            confirmed=confirmed,
        )
        events.append(OrchestratorEvent("memory", "CORE-025", memory_result.status))

        selected = selected_core or self.select_core(text)
        if selected not in CORE_BY_ID:
            self.engine.mark_degraded("unknown_selected_core")
            self.control.audit("request.failed", request_id=request_id, outcome="failed", metadata={"stage": "route", "core": selected})
            return UnifiedRunResult(False, "unknown_selected_core", selected_core=selected, events=events, metadata={"request_id": request_id})
        events.append(OrchestratorEvent("tool_route", selected, "selected"))

        brain = online_brain or self.online_brain
        try:
            brain_payload = dict(brain(
                text,
                {
                    "identity": identity.result,
                    "reasoning": reasoning.result,
                    "routing": routing.result,
                    "memory": self.control_recall,
                    "selected_core": selected,
                    "request_id": request_id,
                    **ctx,
                },
            ) or {})
        except Exception as exc:
            events.append(OrchestratorEvent("online_brain", None, "failed", type(exc).__name__))
            self.engine.mark_degraded("online_brain")
            self.control.audit("request.failed", request_id=request_id, outcome="failed", metadata={"stage": "online_brain", "error_type": type(exc).__name__})
            return UnifiedRunResult(
                False,
                "online_brain_failed",
                selected_core=selected,
                events=events,
                metadata={"request_id": request_id, "online_only": True, "error_type": type(exc).__name__},
            )

        events.append(OrchestratorEvent(
            "online_brain", None, "completed", str(brain_payload.get("provider") or "online")
        ))
        answer = str(brain_payload.get("answer") or "").strip()
        if not answer:
            self.engine.mark_degraded("empty_online_brain_response")
            self.control.audit("request.failed", request_id=request_id, outcome="failed", metadata={"stage": "online_brain", "error": "empty"})
            return UnifiedRunResult(False, "empty_online_brain_response", selected_core=selected, events=events, metadata={"request_id": request_id})

        governance_needed = self._needs_governance(text, selected)
        governance_status = "policy_allow"
        if governance_needed:
            auth = self.execute_core(
                "CORE-012",
                text,
                context={
                    "capability": selected,
                    "selected_core": selected,
                    "request_id": request_id,
                    **ctx,
                },
                confirmed=confirmed,
            )
            governance_status = auth.status
            events.append(OrchestratorEvent("governance", "CORE-012", auth.status))
            if not auth.ok:
                self.engine.mark_degraded("governance_denied")
                self.control.audit("request.denied", request_id=request_id, outcome="denied", metadata={"core": selected, "reason": auth.status})
                return UnifiedRunResult(False, auth.status, answer=answer, selected_core=selected, events=events, metadata={"request_id": request_id})
        else:
            events.append(OrchestratorEvent("governance", "CORE-012", "policy_allow", "confirmation_not_required"))

        execution = self.execute_core(
            selected,
            text,
            context={
                "online_answer": answer,
                "brain_actions": brain_payload.get("actions") or [],
                "source": "unified_orchestrator",
                "request_id": request_id,
                **ctx,
            },
            confirmed=confirmed,
        )
        events.append(OrchestratorEvent("execute", selected, execution.status))
        evidence.extend(execution.evidence or [])

        verification = self.execute_core(
            "CORE-057",
            "verify unified orchestration result",
            context={
                "expected": True,
                "actual": bool(execution.ok),
                "execution_status": execution.status,
                "request_id": request_id,
            },
            confirmed=True,
        )
        events.append(OrchestratorEvent("verify", "CORE-057", verification.status))
        evidence.extend(verification.evidence or [])
        semantic_verification = self.control.verifier.verify(True, bool(execution.ok))
        evidence.append({"verification": "execution_ok", **semantic_verification})

        if not execution.ok or not verification.ok or not semantic_verification["ok"]:
            self.engine.mark_degraded("verification")
            self.control.audit("request.failed", request_id=request_id, outcome="unverified", metadata={"stage": "verification", "execution": execution.status})
            return UnifiedRunResult(
                False,
                "verification_failed",
                answer=answer,
                selected_core=selected,
                events=events,
                evidence=evidence,
                metadata={"request_id": request_id, "governance": governance_status},
            )

        self.control.remember(
            f"User: {text}\nSHADOW: {answer}",
            kind="conversation",
            tags=("request", "response"),
        )
        audit = self.execute_core(
            "CORE-140",
            text,
            context={
                "event": "unified_orchestration_completed",
                "selected_core": selected,
                "request_id": request_id,
            },
            confirmed=True,
        )
        events.append(OrchestratorEvent("audit", "CORE-140", audit.status))
        self.engine.complete_turn(turn_id)
        self.control.audit(
            "request.completed",
            request_id=request_id,
            outcome="verified",
            metadata={
                "selected_core": selected,
                "governance": governance_status,
                "tool_actions": len(brain_payload.get("actions") or []),
            },
        )
        return UnifiedRunResult(
            True,
            "completed",
            answer=answer,
            selected_core=selected,
            events=events,
            evidence=evidence,
            metadata={
                "request_id": request_id,
                "online_only": True,
                "core_count": self.core_count,
                "governance": "CORE-012",
                "verification": "CORE-057",
                "audit": "CORE-140",
                "memory": "PersistentMemory",
                "tools": len(self.control.tool_schemas()),
            },
        )


    def mesh_status(self) -> dict[str, Any]:
        """Return one status surface for the connected 150-Core runtime mesh."""
        runtime_health = self.runtime.health()
        return {
            "core_count": self.core_count,
            "registered_handlers": runtime_health.get("registered_handlers", 0),
            "all_registered": self.core_count == runtime_health.get("registered_handlers", 0),
            "engine": self.engine.snapshot(),
            "online_only": True,
            "components": [
                "identity", "reasoning", "model_route", "memory",
                "online_brain", "governance", "150_core_execution",
                "verification", "audit", "live_updates",
            ],
        }

    def live_update(self, version: str, files: Mapping[str, str]) -> dict[str, Any]:
        """Stage, validate and activate Python-side updates without resetting the session."""
        staged = self.live_updates.stage(version, dict(files))
        update_id = staged.name
        self.engine.queue_update(update_id)
        result = self.live_updates.activate(staged)
        if result.ok:
            self.engine.finish_update(update_id, version)
        else:
            self.engine.rollback_update()
        return self.live_updates.describe(result)

    def recover_online_session(self) -> dict[str, Any]:
        return self.engine.recover()
