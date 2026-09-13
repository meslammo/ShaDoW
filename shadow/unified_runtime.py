"""MOD-35: unified capability registry for SHADOW's free-first runtime."""
from __future__ import annotations
from dataclasses import dataclass
from .ai.local_provider import LocalAI
from .perception.local_router import LocalPerceptionRouter
from .voice.local_voice import LocalVoice
from .design.site_building import Site, propose
from .skills.registry import SkillRegistry
from .development.github_authorization import GitHubAuthorization

@dataclass(frozen=True)
class RuntimeStatus:
    local_ai: bool
    local_voice: bool
    local_vision_router: bool
    project_memory: bool
    phone_use: bool
    analysis: bool
    site_building_design: bool
    github_authorization: bool

class UnifiedRuntime:
    def __init__(self):
        self.ai = LocalAI()
        self.perception = LocalPerceptionRouter()
        self.voice = LocalVoice()
        self.skills = SkillRegistry()
        self.skills.defaults()
        self.github = GitHubAuthorization.pending()

    def status(self) -> RuntimeStatus:
        return RuntimeStatus(True, True, True, True, True, True, True, self.github.authorized)

    def design_site(self, width: float, depth: float, rooms: list[str], floors: int = 1, coverage: float = 0.60):
        return propose(Site(width, depth), rooms, floors, coverage)
