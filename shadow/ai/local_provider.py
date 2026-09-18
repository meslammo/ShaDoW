"""MOD-78: optional provider adapter kept as a non-conversational integration boundary.

The active SHADOW product contract is online-only for AI conversation. This class no longer
supplies deterministic local conversation when an on-device model is absent.
"""
from __future__ import annotations
from dataclasses import dataclass
from typing import Any, Dict, Optional, Protocol

@dataclass(frozen=True)
class LocalAIResult:
    text: str
    provider: str = "local-safe"
    model: str = "rules"
    verified: bool = True

class LocalModel(Protocol):
    def generate(self, prompt: str, *, context: Optional[Dict[str, Any]] = None) -> str: ...

class LocalAI:
    def __init__(self, model: Optional[LocalModel] = None):
        self.model = model

    @property
    def ready(self) -> bool:
        return self.model is not None

    def run(self, prompt: str, *, context: Optional[Dict[str, Any]] = None) -> LocalAIResult:
        if self.model is None:
            raise RuntimeError("online_ai_required")
        text = self.model.generate(prompt, context=context)
        return LocalAIResult(text, "configured-model", "external-adapter", True)
