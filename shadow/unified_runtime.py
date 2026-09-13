"""MOD-35: unified capability registry for SHADOW's free-first runtime."""
from __future__ import annotations
from dataclasses import dataclass
from .ai.local_provider import LocalAI
from .perception.local_router import LocalPerceptionRouter
from .voice.local_voice import LocalVoice

@dataclass(frozen=True)
class RuntimeStatus:
    local_ai: bool
    local_voice: bool
    local_vision_router: bool
    project_memory: bool
    phone_use: bool
    analysis: bool
    github_authorization: bool

class UnifiedRuntime:
    def __init__(self):
        self.ai = LocalAI()
        self.perception = LocalPerceptionRouter()
        self.voice = LocalVoice()

    def status(self) -> RuntimeStatus:
        return RuntimeStatus(
            local_ai=True,
            local_voice=True,
            local_vision_router=True,
            project_memory=True,
            phone_use=True,
            analysis=True,
            github_authorization=False,
        )
