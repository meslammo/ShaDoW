"""MOD-36.1: production runtime facade.

Turns the MOD-35 contracts into one executable, fail-closed Python runtime.
The Android layer can use this facade through the embedded Python runtime.
No credentials are stored here and no paid service is required for the core path.
"""
from __future__ import annotations

from dataclasses import dataclass, asdict
from pathlib import Path
from typing import Any

from ..analysis.ev_engine import EVEngine
from ..ai.local_provider import LocalAI
from ..development.file_understanding import inspect_path
from ..development.github_authorization import GitHubAuthorization
from ..design.site_building import Site, propose
from ..perception.local_router import LocalPerceptionRouter
from ..skills.registry import SkillRegistry
from ..voice.local_voice import LocalVoice


@dataclass(frozen=True)
class RuntimeCapabilities:
    analysis: bool
    local_ai: bool
    local_voice_adapter: bool
    perception_router: bool
    file_understanding: bool
    design: bool
    skills: bool
    github_authorization: bool

    def to_dict(self) -> dict[str, bool]:
        return asdict(self)


class ProductionRuntime:
    """Single entry point for real local capabilities and explicit approvals."""

    def __init__(self, workspace: str | Path | None = None) -> None:
        self.workspace = Path(workspace or ".").resolve()
        self.ai = LocalAI()
        self.voice = LocalVoice()
        self.perception = LocalPerceptionRouter()
        self.analysis = EVEngine()
        self.skills = SkillRegistry()
        self.skills.defaults()
        self.github = GitHubAuthorization.pending()

    def capabilities(self) -> RuntimeCapabilities:
        return RuntimeCapabilities(
            analysis=True,
            local_ai=True,
            local_voice_adapter=True,
            perception_router=True,
            file_understanding=True,
            design=True,
            skills=True,
            github_authorization=self.github.authorized,
        )

    def analyze(self, text: str) -> Any:
        """Run the deterministic E.V.-style analysis engine without fabricating data."""
        return self.analysis.analyze(text)

    def inspect_file(self, path: str) -> dict[str, Any]:
        """Inspect only a path inside the configured workspace."""
        target = (self.workspace / path).resolve()
        if target != self.workspace and self.workspace not in target.parents:
            raise PermissionError("path escapes SHADOW workspace")
        return inspect_path(target)

    def perceive_image(self, data: bytes, name: str = "image") -> dict[str, Any]:
        return self.perception.image_receipt(data, name=name)

    def design_site(
        self,
        width: float,
        depth: float,
        rooms: list[str],
        floors: int = 1,
        coverage: float = 0.60,
    ) -> dict[str, Any]:
        return propose(Site(width, depth), rooms, floors, coverage).to_dict()

    def authorize_github(self, account: str, permissions: list[str]) -> dict[str, Any]:
        """Record an explicit authorization request; never accepts/stores a token."""
        self.github = GitHubAuthorization.request(account, permissions)
        return self.github.to_dict()

    def status(self) -> dict[str, Any]:
        return {
            "workspace": str(self.workspace),
            "capabilities": self.capabilities().to_dict(),
            "voice": self.voice.status(),
            "github": self.github.to_dict(),
            "skills": self.skills.list(),
        }
