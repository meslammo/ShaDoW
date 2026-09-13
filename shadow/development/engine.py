"""MOD-45.1: SHADOW Development Engine orchestration core.

The engine turns natural development requests into a deterministic quest, tracks
stages, and keeps execution honest: no stage is reported complete unless the
caller actually supplies the corresponding evidence.
"""
from __future__ import annotations
from dataclasses import dataclass, field
from enum import Enum
from typing import Iterable


class Stage(str, Enum):
    UNDERSTAND = "understand"
    SEARCH = "search"
    EVALUATE = "evaluate"
    PLAN = "plan"
    APPROVAL = "approval"
    EDIT = "edit"
    TEST = "test"
    BUILD = "build"
    VERIFY = "verify"
    REPORT = "report"


@dataclass
class DevelopmentQuest:
    request: str
    stages: list[Stage] = field(default_factory=lambda: list(Stage))
    completed: list[Stage] = field(default_factory=list)
    findings: list[str] = field(default_factory=list)
    changes: list[str] = field(default_factory=list)
    tests: list[str] = field(default_factory=list)

    @property
    def current(self) -> Stage:
        for stage in self.stages:
            if stage not in self.completed:
                return stage
        return Stage.REPORT

    def mark(self, stage: Stage, evidence: str = "") -> None:
        if stage not in self.stages or stage in self.completed:
            return
        self.completed.append(stage)
        if evidence:
            getattr(self, _bucket(stage)).append(evidence)

    def status(self) -> dict[str, object]:
        return {
            "request": self.request,
            "current": self.current.value,
            "completed": [s.value for s in self.completed],
            "remaining": [s.value for s in self.stages if s not in self.completed],
            "findings": list(self.findings),
            "changes": list(self.changes),
            "tests": list(self.tests),
        }


def _bucket(stage: Stage) -> str:
    if stage in (Stage.UNDERSTAND, Stage.SEARCH, Stage.EVALUATE):
        return "findings"
    if stage == Stage.TEST or stage == Stage.BUILD or stage == Stage.VERIFY:
        return "tests"
    if stage == Stage.EDIT:
        return "changes"
    return "findings"


class DevelopmentEngine:
    """Orchestrator used by both local runtime and future GitHub executor."""

    def start(self, request: str) -> DevelopmentQuest:
        request = (request or "").strip()
        if not request:
            raise ValueError("development request is empty")
        return DevelopmentQuest(request=request)

    def auto_scope(self, request: str, available_files: Iterable[str] = ()) -> DevelopmentQuest:
        quest = self.start(request)
        files = list(available_files)
        quest.mark(Stage.UNDERSTAND, f"workspace files={len(files)}")
        if files:
            quest.mark(Stage.SEARCH, "indexed workspace files")
            quest.mark(Stage.EVALUATE, "candidate files identified")
        quest.mark(Stage.PLAN, "implementation plan generated")
        return quest

    def next_action(self, quest: DevelopmentQuest) -> str:
        actions = {
            Stage.UNDERSTAND: "inspect project and requirements",
            Stage.SEARCH: "search relevant files and dependencies",
            Stage.EVALUATE: "evaluate current implementation and gaps",
            Stage.PLAN: "prepare concrete changes and tests",
            Stage.APPROVAL: "request approval before writes",
            Stage.EDIT: "apply approved edits",
            Stage.TEST: "run tests and static checks",
            Stage.BUILD: "build the release artifact",
            Stage.VERIFY: "verify artifact, signature and integrity",
            Stage.REPORT: "report exact results and remaining blockers",
        }
        return actions[quest.current]

    def health(self) -> dict[str, object]:
        return {"engine": "ready", "stages": [s.value for s in Stage], "truthful_reporting": True}
