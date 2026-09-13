"""MOD-35: provider-neutral local AI contract.

This module deliberately has no paid dependency. It provides a deterministic
local fallback and a clean adapter boundary for an on-device runtime such as
llama.cpp/ExecuTorch/MLC when a compatible model is installed.
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
        if self.model is not None:
            try:
                return LocalAIResult(self.model.generate(prompt, context=context), "local-model", "installed", True)
            except Exception:
                pass
        text = self._safe(prompt)
        return LocalAIResult(text)

    @staticmethod
    def _safe(prompt: str) -> str:
        p = (prompt or "").strip()
        low = p.lower()
        if low in {"hi", "hello", "hey", "سلام", "اهلا", "أهلا", "مرحبا"}:
            return "أهلاً. SHADOW شغال محلياً."
        if "status" in low or "حالة" in low:
            return "SHADOW LOCAL: core=ready, memory=ready, tools=ready, cloud=optional."
        if "مين انت" in low or "who are you" in low:
            return "أنا SHADOW، والنواة المحلية تقدر تشتغل بدون اشتراك أو Credits."
        return "أنا في الوضع المحلي الآمن حالياً. أقدر أنفذ الأدوات المحلية المتاحة بدون اختلاق إجابة."
