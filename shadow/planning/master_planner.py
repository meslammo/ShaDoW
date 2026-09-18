"""MOD-92: bounded master planner for SHADOW's 12-Core route.

The planner is provider-neutral. It prepares a deterministic execution skeleton
around the online AI brain; it does not execute actions or invent tool results.
"""
from __future__ import annotations
from dataclasses import asdict, dataclass
from typing import Any

@dataclass(frozen=True)
class PlanStep:
    index: int
    core: str
    action: str
    approval_required: bool = False
    verification_required: bool = False

@dataclass(frozen=True)
class MasterPlan:
    request: str
    route: str
    online_brain_required: bool
    steps: tuple[PlanStep, ...]
    risk: str

    def to_dict(self) -> dict[str, Any]:
        return {
            "request": self.request,
            "route": self.route,
            "online_brain_required": self.online_brain_required,
            "risk": self.risk,
            "steps": [asdict(s) for s in self.steps],
        }

class MasterPlanner:
    ROUTE_HINTS = (
        ("github", ("github", "git hub", "جيت هاب", "جيتهاب")),
        ("development", ("develop", "build", "code", "project", "طور", "عدّل", "عدل", "مشروع", "كود")),
        ("companion", ("companion", "watch", "smart home", "car companion", "الساعة", "البيت الذكي", "السيارة")),
        ("spatial", ("spatial", "gps", "location", "موقعي", "موقع", "رادار")),
        ("device", ("phone", "android", "wifi", "bluetooth", "camera", "افتح", "شغل", "اقفل")),
        ("image", ("image", "picture", "صورة", "صمم")),
    )

    def plan(self, request: str, *, route: str | None = None, authenticated: bool = False) -> MasterPlan:
        text = str(request or "").strip()
        low = text.lower()
        chosen = route or self._route(low)
        critical = any(k in low for k in ("delete", "wipe", "payment", "password", "token", "private key", "حذف نهائي", "مسح كامل", "فلوس", "باسورد", "توكن"))
        high = critical or any(k in low for k in ("commit", "push", "merge", "deploy", "publish", "send", "message", "اتصل", "رسالة", "نفذ", "نفّذ"))
        steps: list[PlanStep] = [
            PlanStep(1, "identity", "resolve actor and authority boundary"),
            PlanStep(2, "conversation", "normalize request and session context"),
            PlanStep(3, "reasoning", "interpret intent and constraints"),
            PlanStep(4, "memory", "retrieve only relevant governed memory"),
        ]
        if chosen in {"chat", "development", "github", "companion", "device", "spatial", "image"}:
            steps.append(PlanStep(len(steps)+1, "web/personal", "gather current evidence when useful", False, True))
        if chosen in {"development", "github"}:
            steps.append(PlanStep(len(steps)+1, "development", "inspect repository and prepare bounded change set", True, True))
        if chosen == "companion":
            steps.append(PlanStep(len(steps)+1, "companion", "negotiate capabilities and trust state", True, True))
        if chosen == "device":
            steps.append(PlanStep(len(steps)+1, "device", "prepare a local device action", True, True))
        if chosen == "spatial":
            steps.append(PlanStep(len(steps)+1, "spatial", "build semantic spatial context without raw coordinates", False, True))
        if chosen == "image":
            steps.append(PlanStep(len(steps)+1, "integration", "prepare the image-provider handoff", False, True))
        steps.append(PlanStep(len(steps)+1, "action-security", "apply risk, permission and approval policy", high or critical, True))
        steps.append(PlanStep(len(steps)+1, "verification", "verify the external result before claiming completion", False, True))
        steps.append(PlanStep(len(steps)+1, "recovery", "checkpoint and prepare bounded retry/rollback path", False, False))
        steps.append(PlanStep(len(steps)+1, "communication", "report evidence, uncertainty and next state", False, False))
        risk = "critical" if critical else "high" if high else "medium" if chosen != "chat" else "low"
        if not authenticated and any(s.approval_required for s in steps):
            risk = max_risk(risk, "high")
        return MasterPlan(text, chosen, True, tuple(steps), risk)

    @classmethod
    def _route(cls, text: str) -> str:
        for route, hints in cls.ROUTE_HINTS:
            if any(h in text for h in hints):
                return route
        return "chat"

def max_risk(a: str, b: str) -> str:
    order = {"low": 0, "medium": 1, "high": 2, "critical": 3}
    return a if order[a] >= order[b] else b
