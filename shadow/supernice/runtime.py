"""Governed execution facade for the 150-Core registry."""
from __future__ import annotations

from dataclasses import asdict
from typing import Any, Mapping

from .catalog import CORE_BY_ID
from .contracts import CoreRequest, CoreResult, Handler
from .providers import ProviderRouter
from .builtins import build_default_handlers
from .optional import OPTIONAL_DEVICE_CORE_IDS, device_integrations_enabled


class CoreRuntime:
    """Runs every registered core through a safe builtin or a supplied adapter.

    Builtins never claim external I/O. Specialized integrations can replace a
    builtin handler explicitly. Optional hardware/companion cores are disabled
    by default and never block startup.
    """

    def __init__(
        self,
        *,
        handler_map: Mapping[str, Handler] | None = None,
        free_first: bool = True,
        include_builtins: bool = True,
    ):
        self.providers = ProviderRouter()
        self.free_first = free_first
        self.handlers = build_default_handlers(tuple(CORE_BY_ID.values())) if include_builtins else {}
        if handler_map:
            self.handlers.update(dict(handler_map))

    def list_cores(self) -> list[dict[str, Any]]:
        return [asdict(s) for s in CORE_BY_ID.values()]

    def execute(self, request: CoreRequest) -> CoreResult:
        spec = CORE_BY_ID.get(request.core_id)
        if spec is None:
            return CoreResult(request.core_id, False, "unknown_core")

        if spec.requires_confirmation and not request.confirmed:
            return CoreResult(
                spec.id,
                False,
                "confirmation_required",
                metadata={"risk": spec.risk.value, "core": spec.name},
            )

        handler = self.handlers.get(spec.id)
        if handler is None:
            return CoreResult(
                spec.id,
                False,
                "internal_handler_missing",
                metadata={
                    "core": spec.name,
                    "domain": spec.domain,
                    "startup_blocking": False,
                },
            )

        try:
            return handler(request, spec)
        except Exception as exc:
            return CoreResult(
                spec.id,
                False,
                "handler_error",
                metadata={
                    "core": spec.name,
                    "startup_blocking": False,
                    "error_type": type(exc).__name__,
                    "error": str(exc)[:240],
                },
            )

    def health(self) -> dict[str, Any]:
        disabled_optional = sorted(
            core_id
            for core_id in OPTIONAL_DEVICE_CORE_IDS
            if not device_integrations_enabled()
        )
        return {
            "architecture": "SHADOW Super Nice 150-Core",
            "core_count": len(CORE_BY_ID),
            "registered_handlers": len(self.handlers),
            "free_first": self.free_first,
            "subscription_gate": False,
            "credit_meter": False,
            "online_ai_mode": True,
            "provider_count": len(self.providers.providers),
            "optional_device_cores_disabled": disabled_optional,
            "optional_device_cores_enabled": device_integrations_enabled(),
            "startup_blocking_integrations": [],
        }
