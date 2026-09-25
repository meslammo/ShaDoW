"""Governed execution facade for the 150-Core registry."""
from __future__ import annotations

from dataclasses import asdict
from typing import Any, Mapping

from .catalog import CORE_BY_ID
from .contracts import CoreRequest, CoreResult, Handler
from .providers import ProviderRouter
from .live import build_live_handlers
from .optional import OPTIONAL_DEVICE_CORE_IDS, device_integrations_enabled


class CoreRuntime:
    """Execute the full 150-Core surface with concrete local handlers.

    External services and hardware are adapters. Missing adapters are explicit
    and never become startup blockers.
    """

    def __init__(
        self,
        *,
        workspace: str = ".",
        handler_map: Mapping[str, Handler] | None = None,
        free_first: bool = True,
        include_live: bool = True,
    ):
        self.workspace = workspace
        self.providers = ProviderRouter()
        self.free_first = free_first
        self.handlers = build_live_handlers(tuple(CORE_BY_ID.values()), workspace) if include_live else {}
        if handler_map:
            self.handlers.update(dict(handler_map))

    def list_cores(self) -> list[dict[str, Any]]:
        return [asdict(s) for s in CORE_BY_ID.values()]

    def execute(self, request: CoreRequest) -> CoreResult:
        spec = CORE_BY_ID.get(request.core_id)
        if spec is None:
            return CoreResult(request.core_id, False, "unknown_core")

        # Optional hardware/companion cores are disabled before auth checks:
        # they cannot block startup or normal operation when no adapter exists.
        if spec.id in OPTIONAL_DEVICE_CORE_IDS and not device_integrations_enabled():
            return CoreResult(
                spec.id,
                True,
                "disabled_out_of_scope",
                result={
                    "core": spec.id,
                    "name": spec.name,
                    "enabled": False,
                    "reason": "external device integration is outside active Shadow scope",
                },
                evidence=[{"external_io": False, "device_io": False}],
                metadata={"startup_blocking": False, "optional": True},
            )

        if spec.requires_confirmation and not request.confirmed:
            return CoreResult(
                spec.id,
                False,
                "confirmation_required",
                metadata={"risk": spec.risk.value, "core": spec.name, "startup_blocking": False},
            )

        handler = self.handlers.get(spec.id)
        if handler is None:
            return CoreResult(
                spec.id,
                False,
                "internal_handler_missing",
                metadata={"core": spec.name, "domain": spec.domain, "startup_blocking": False},
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

    def self_test(self) -> dict[str, Any]:
        passed = 0
        failures = []
        for core_id in sorted(CORE_BY_ID):
            result = self.execute(
                CoreRequest(
                    core_id,
                    "SHADOW Super Nice core self-test",
                    context={"source": "self_test", "capability": "chat"},
                    confirmed=True,
                )
            )
            if result.ok:
                passed += 1
            else:
                failures.append({"core": core_id, "status": result.status, "metadata": result.metadata})
        return {
            "core_count": len(CORE_BY_ID),
            "passed": passed,
            "failed": len(failures),
            "failures": failures,
            "all_passed": not failures,
        }

    def health(self) -> dict[str, Any]:
        return {
            "architecture": "SHADOW Super Nice 150-Core",
            "core_count": len(CORE_BY_ID),
            "registered_handlers": len(self.handlers),
            "free_first": self.free_first,
            "subscription_gate": False,
            "credit_meter": False,
            "online_ai_mode": True,
            "provider_count": len(self.providers.providers),
            "optional_device_cores_disabled": sorted(
                OPTIONAL_DEVICE_CORE_IDS if not device_integrations_enabled() else ()
            ),
            "optional_device_cores_enabled": device_integrations_enabled(),
            "startup_blocking_integrations": [],
        }
