"""MOD-36.1: production runtime facade.

One executable, fail-closed Python runtime for the existing SHADOW foundations.
It deliberately does not store credentials and does not claim capabilities that
are only contracts. Android remains responsible for device-specific execution.
"""
from __future__ import annotations

from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any

from ..ai.local_provider import LocalAI
from ..analysis.ev_engine import analyze
from ..development.file_understanding import summarize
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
    """Single local entry point for analysis, files, perception and design."""

    def __init__(self, workspace: str | Path | None = None) -> None:
        self.workspace = Path(workspace or ".").resolve()
        self.ai = LocalAI()
        self.voice = LocalVoice()
        self.perception = LocalPerceptionRouter()
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

    def chat_local(self, prompt: str) -> dict[str, Any]:
        result = self.ai.run(prompt)
        return {"text": result.text, "provider": result.provider, "model": result.model, "verified": result.verified}

    def analyze_numbers(self, values: list[float]) -> dict[str, Any]:
        result = analyze(values)
        return {
            "count": result.count,
            "mean": result.mean,
            "median": result.median,
            "minimum": result.minimum,
            "maximum": result.maximum,
            "standard_deviation": result.standard_deviation,
            "trend": result.trend,
        }

    def inspect_file(self, path: str) -> dict[str, Any]:
        target = (self.workspace / path).resolve()
        if target != self.workspace and self.workspace not in target.parents:
            raise PermissionError("path escapes SHADOW workspace")
        return asdict(summarize(target))

    def perceive_image(self, data: bytes, name: str = "image/jpeg") -> dict[str, Any]:
        return asdict(self.perception.inspect_image(data, mime_type=name))

    def perceive_audio(self, data: bytes, sample_rate: int = 16000) -> dict[str, Any]:
        return asdict(self.perception.inspect_audio(data, sample_rate=sample_rate))

    def design_site(self, width: float, depth: float, rooms: list[str], floors: int = 1, coverage: float = 0.60) -> dict[str, Any]:
        return propose(Site(width, depth), rooms, floors, coverage).to_dict()

    def authorization_request(self, account: str, permissions: list[str]) -> dict[str, Any]:
        self.github = GitHubAuthorization(False, tuple(permissions), account)
        return self.github.summary()

    def status(self) -> dict[str, Any]:
        return {
            "workspace": str(self.workspace),
            "capabilities": self.capabilities().to_dict(),
            "voice": self.voice.status(),
            "github": self.github.summary(),
            "skills": [asdict(skill) for skill in self.skills.list()],
        }
