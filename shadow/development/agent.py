"""MOD-45.2: Development Agent backed by the unified Development Engine."""
from __future__ import annotations
from dataclasses import dataclass
from .engine import DevelopmentEngine, DevelopmentQuest, Stage


@dataclass(frozen=True)
class DevPlan:
    request: str
    stages: tuple[str, ...] = (
        "understand", "search", "evaluate", "plan", "approval",
        "edit", "test", "build", "verify", "report"
    )
    approval_required: bool = True

    def to_dict(self) -> dict[str, object]:
        return {
            "request": self.request,
            "stages": list(self.stages),
            "approval_required": self.approval_required,
        }


class DevelopmentAgent:
    def __init__(self) -> None:
        self.engine = DevelopmentEngine()
        self.quest: DevelopmentQuest | None = None

    def plan(self, request: str) -> DevPlan:
        self.quest = self.engine.start(request)
        self.quest.mark(Stage.UNDERSTAND, "request accepted")
        self.quest.mark(Stage.PLAN, "development quest created")
        return DevPlan(request.strip())

    def start_self_development(self, request: str = "طور SHADOW وشوف الناقص وكمله", available_files=()) -> dict[str, object]:
        self.quest = self.engine.auto_scope(request, available_files)
        self.quest.completed = [s for s in self.quest.completed if s != Stage.PLAN]
        self.quest.mark(Stage.PLAN, "gap-based development plan generated")
        return self.quest.status()

    def workspace_rule(self) -> dict[str, object]:
        return {
            "no_secrets": True,
            "no_unapproved_write": True,
            "test_before_report": True,
            "version_changes": True,
            "rollback_before_edit": True,
        }

    def next_action(self) -> str:
        return self.engine.next_action(self.quest) if self.quest else "start development quest"

    def status(self) -> dict[str, object]:
        return self.quest.status() if self.quest else {"current": "idle"}

    def health(self) -> dict[str, object]:
        return self.engine.health()
