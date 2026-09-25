"""Online brain adapters for SHADOW.

The unified path reuses the existing provider-neutral AIOrchestrator. Tool
schemas and governed execution are injected from UnifiedControlPlane so the
brain can call the same tool surface used by the runtime.
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


class GovernedOnlineBrainAdapter:
    """Online brain with the real runtime tool registry and permission boundary."""

    def __init__(self, control_plane: Any, brain: AIOrchestrator | None = None) -> None:
        self.control_plane = control_plane
        self.brain = brain or AIOrchestrator()

    def __call__(
        self,
        request: str,
        context: Mapping[str, Any] | None = None,
    ) -> dict[str, Any]:
        ctx = dict(context or {})
        confirmed_actions = [str(x) for x in ctx.get("confirmed_actions", []) if str(x).strip()]
        top_level_confirmed = bool(ctx.get("confirmed", False))
        schemas = self.control_plane.tool_schemas()

        def execute_tool(name: str, arguments: dict[str, Any]) -> dict[str, Any]:
            return self.control_plane.execute_tool(
                name,
                arguments,
                confirmed=(name in confirmed_actions) or top_level_confirmed,
                automation_granted=bool(ctx.get("automation_granted", False)),
            )

        result = self.brain.run(
            str(request or ""),
            context=ctx,
            tools=schemas,
            tool_executor=execute_tool,
            confirmed_actions=confirmed_actions,
        )
        if not isinstance(result, dict):
            raise RuntimeError("online_brain_invalid_response")
        if result.get("online_error"):
            raise RuntimeError(str(result["online_error"]))
        answer = str(result.get("answer") or "").strip()
        if not answer:
            raise RuntimeError("empty_ai_response")
        return {
            "provider": result.get("provider") or "online",
            "model": result.get("model"),
            "answer": answer,
            "verified": bool(result.get("verified", False)),
            "actions": result.get("actions") or [],
            "tool_round_limit": result.get("tool_round_limit"),
        }

    @staticmethod
    def configured() -> bool:
        import os
        return bool(os.getenv("OPENAI_API_KEY", "").strip())
