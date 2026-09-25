"""Unified 150-Core orchestration boundary for SHADOW.

This module is intentionally small: it does not replace the existing 12-Core
root or the online provider implementations. It gives them one deterministic
orchestration surface and trace contract so the 150-Core catalog can be
audited for reachability before real E2E is attempted.
"""
from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Callable, Dict, Mapping, Optional

from .catalog import CORE_BY_ID
from .contracts import CoreRequest, CoreResult
from .integration import SuperNiceRuntime


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
                {
                    "stage": e.stage,
                    "core_id": e.core_id,
                    "status": e.status,
                    "detail": e.detail,
                }
                for e in self.events
            ],
            "evidence": list(self.evidence),
            "metadata": dict(self.metadata),
        }


OnlineBrain = Callable[[str, Mapping[str, Any]], Mapping[str, Any]]


class Unified150Orchestrator:
    """Single Python orchestration surface for the full 150-Core catalog.

    The online brain is injected so tests can use a deterministic fixture while
    production wiring can provide the real online provider gateway later.
    """

    def __init__(self, workspace: str = ".") -> None:
        self.workspace = workspace
        self.runtime = SuperNiceRuntime(workspace)

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
            results.append(
                {
                    "core_id": core_id,
                    "ok": result.ok,
                    "status": result.status,
                    "metadata": result.metadata,
                }
            )
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
        ctx = dict(context or {})

        if not text:
            return UnifiedRunResult(False, "request_required")

        events.append(OrchestratorEvent("understand", "CORE-003", "started"))

        identity = self.execute_core(
            "CORE-001",
            text,
            context={"source": "unified_orchestrator", **ctx},
            confirmed=confirmed,
        )
        events.append(OrchestratorEvent("identity", "CORE-001", identity.status))
        if not identity.ok:
            return UnifiedRunResult(False, identity.status, events=events)

        reasoning = self.execute_core(
            "CORE-003",
            text,
            context={"source": "unified_orchestrator", **ctx},
            confirmed=confirmed,
        )
        events.append(OrchestratorEvent("reasoning", "CORE-003", reasoning.status))
        if not reasoning.ok:
            return UnifiedRunResult(False, reasoning.status, events=events)

        routing = self.execute_core(
            "CORE-013",
            text,
            context={"capability": "chat", **ctx},
            confirmed=confirmed,
        )
        events.append(OrchestratorEvent("model_route", "CORE-013", routing.status))
        if not routing.ok:
            return UnifiedRunResult(False, routing.status, events=events)

        memory = self.execute_core(
            "CORE-025",
            text,
            context={"source": "unified_orchestrator", **ctx},
            confirmed=confirmed,
        )
        events.append(OrchestratorEvent("memory", "CORE-025", memory.status))

        selected = selected_core or self.select_core(text)
        events.append(OrchestratorEvent("tool_route", selected, "selected"))

        if online_brain is None:
            events.append(OrchestratorEvent("online_brain", None, "adapter_pending"))
            return UnifiedRunResult(
                False,
                "online_brain_required",
                selected_core=selected,
                events=events,
                metadata={"online_only": True, "core_count": self.core_count},
            )

        try:
            brain_payload = dict(
                online_brain(
                    text,
                    {
                        "identity": identity.result,
                        "reasoning": reasoning.result,
                        "routing": routing.result,
                        "memory": memory.result,
                        "selected_core": selected,
                        **ctx,
                    },
                )
                or {}
            )
        except Exception as exc:
            events.append(OrchestratorEvent("online_brain", None, "failed", type(exc).__name__))
            return UnifiedRunResult(
                False,
                "online_brain_failed",
                selected_core=selected,
                events=events,
                metadata={"online_only": True, "error_type": type(exc).__name__},
            )

        events.append(
            OrchestratorEvent(
                "online_brain",
                None,
                "completed",
                str(brain_payload.get("provider") or "online"),
            )
        )

        answer = str(brain_payload.get("answer") or "").strip()
        if not answer:
            return UnifiedRunResult(
                False,
                "empty_online_brain_response",
                selected_core=selected,
                events=events,
            )

        auth = self.execute_core(
            "CORE-012",
            text,
            context={"capability": "master-pipeline", **ctx},
            confirmed=confirmed,
        )
        events.append(OrchestratorEvent("governance", "CORE-012", auth.status))
        if not auth.ok:
            return UnifiedRunResult(
                False,
                auth.status,
                answer=answer,
                selected_core=selected,
                events=events,
            )

        execution = self.execute_core(
            selected,
            text,
            context={"online_answer": answer, "source": "unified_orchestrator", **ctx},
            confirmed=confirmed,
        )
        events.append(OrchestratorEvent("execute", selected, execution.status))
        if execution.evidence:
            evidence.extend(execution.evidence)

        verification = self.execute_core(
            "CORE-057",
            "verify unified orchestration result",
            context={"expected": "completed", "actual": execution.status},
            confirmed=True,
        )
        events.append(OrchestratorEvent("verify", "CORE-057", verification.status))
        if verification.evidence:
            evidence.extend(verification.evidence)

        if not verification.ok:
            return UnifiedRunResult(
                False,
                "verification_failed",
                answer=answer,
                selected_core=selected,
                events=events,
                evidence=evidence,
            )

        events.append(OrchestratorEvent("audit", "CORE-140", "recorded"))
        return UnifiedRunResult(
            True,
            "completed",
            answer=answer,
            selected_core=selected,
            events=events,
            evidence=evidence,
            metadata={
                "online_only": True,
                "core_count": self.core_count,
                "governance": "CORE-012",
                "verification": "CORE-057",
                "audit": "CORE-140",
            },
        )
