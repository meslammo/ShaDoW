"""Real online brain adapter for the unified 150-Core orchestrator.

Reuses SHADOW's existing provider-neutral online AI entry point. It never
provides an offline/local conversational fallback and treats provider
configuration/network failures as failures instead of answers.
"""
from __future__ import annotations

from typing import Any, Mapping

from shadow.ai.orchestrator.orchestrator import AIOrchestrator


class RealOnlineBrainAdapter:
    """Adapter from Unified150Orchestrator to the existing online AI brain."""

    def __init__(self, brain: AIOrchestrator | None = None) -> None:
        self.brain = brain or AIOrchestrator()

    def __call__(
        self,
        request: str,
        context: Mapping[str, Any] | None = None,
    ) -> dict[str, Any]:
        result = self.brain.run(
            str(request or ""),
            context=dict(context or {}),
        )
        if not isinstance(result, dict):
            raise RuntimeError("online_brain_invalid_response")

        online_error = result.get("online_error")
        if online_error:
            raise RuntimeError(str(online_error))

        answer = str(result.get("answer") or "").strip()
        if not answer:
            raise RuntimeError("empty_ai_response")

        return {
            "provider": result.get("provider") or "online",
            "model": result.get("model"),
            "answer": answer,
            "verified": bool(result.get("verified", False)),
            "actions": result.get("actions") or [],
        }

    @staticmethod
    def configured() -> bool:
        import os
        return bool(os.getenv("OPENAI_API_KEY", "").strip())
