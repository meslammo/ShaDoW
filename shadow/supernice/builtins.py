"""Executable default handlers for all Super Nice cores.

These handlers provide a real, deterministic contract-level implementation for
every registered core without pretending to perform unavailable external work.
Network, media-provider, GitHub, and hardware operations remain adapters.
"""
from __future__ import annotations

import hashlib
from pathlib import PurePath
from typing import Any, Mapping

from .contracts import CoreRequest, CoreResult, CoreSpec
from .providers import ProviderRouter
from .optional import is_optional_device_core, device_integrations_enabled


_SECRET_MARKERS = (
    "api_key=",
    "api-key=",
    "access_token=",
    "authorization=",
    "private_key=",
    "password=",
    "secret=",
)


def _digest(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()[:16]


def _safe_text(text: str, limit: int = 1200) -> str:
    value = text or ""
    for marker in _SECRET_MARKERS:
        lower = value.lower()
        start = 0
        while True:
            index = lower.find(marker, start)
            if index < 0:
                break
            end = value.find(" ", index)
            if end < 0:
                end = len(value)
            value = value[:index] + marker + "[REDACTED]" + value[end:]
            lower = value.lower()
            start = index + len(marker) + len("[REDACTED]")
    return value[:limit]


def _artifact_summary(artifacts: list[dict[str, Any]]) -> list[dict[str, Any]]:
    out = []
    for item in artifacts[:32]:
        name = str(item.get("name") or item.get("path") or "artifact")
        out.append({
            "name": name[:180],
            "kind": PurePath(name).suffix.lower().lstrip(".") or "unknown",
            "has_content": bool(item.get("content") or item.get("text") or item.get("data")),
        })
    return out


def _urls(text: str) -> list[str]:
    return [part.rstrip(".,);]}>") for part in text.split() if part.startswith(("http://", "https://"))][:20]


def _handler_payload(request: CoreRequest, spec: CoreSpec) -> dict[str, Any]:
    text = request.request.strip()
    context = request.context if isinstance(request.context, Mapping) else {}
    urls = _urls(text)
    artifacts = _artifact_summary(request.artifacts)

    base = {
        "core": spec.id,
        "name": spec.name,
        "domain": spec.domain,
        "request_digest": _digest(text),
        "request": _safe_text(text),
        "context_keys": sorted(str(k) for k in context.keys())[:64],
        "artifact_summary": artifacts,
        "evidence": {
            "mode": "deterministic_builtin",
            "network_performed": False,
            "device_io_performed": False,
            "external_provider_called": False,
        },
    }

    if spec.domain == "intelligence":
        providers = ProviderRouter().route("chat", free_first=True)
        base["routing"] = {
            "ordered_providers": [p.name for p in providers],
            "free_first": True,
            "selected": providers[0].name if providers else None,
            "selection_reason": "local/free-first contract route; no remote call made",
        }
    elif spec.domain == "memory":
        base["memory"] = {
            "persistence_performed": False,
            "privacy": "no durable write by default",
            "candidate_text": _safe_text(text, 800),
        }
    elif spec.domain == "personal_execution":
        base["task"] = {
            "kind": spec.name,
            "ready": True,
            "execution_performed": False,
            "next_action": "hand off to the appropriate governed executor",
        }
    elif spec.domain in {"research", "web"}:
        base["research"] = {
            "urls_detected": urls,
            "artifact_count": len(artifacts),
            "network_performed": False,
            "next_action": "use the existing web/file adapter when explicitly requested",
        }
    elif spec.domain == "software":
        lowered = text.lower()
        base["software"] = {
            "plan_ready": True,
            "mutating_operation_detected": any(
                token in lowered
                for token in ("commit", "push", "merge", "deploy", "delete", "publish", "write")
            ),
            "execution_performed": False,
            "next_action": "route to the governed development gateway",
        }
    elif spec.domain == "multimodal":
        base["multimodal"] = {
            "operation": spec.name,
            "input_artifacts": len(artifacts),
            "external_adapter_call": False,
            "next_action": "use a configured media provider when needed",
        }
    elif spec.domain == "security":
        lower = text.lower()
        base["security"] = {
            "sensitive_request": True,
            "redactions_applied": any(marker in lower for marker in _SECRET_MARKERS),
            "confirmation_seen": request.confirmed,
        }
    elif spec.domain in {"device", "spatial", "companion"}:
        base["integration"] = {
            "hardware_enabled": device_integrations_enabled(),
            "device_io_performed": False,
            "adapter_contract_only": True,
        }

    return base


def make_builtin_handler(spec: CoreSpec):
    def handle(request: CoreRequest, _spec: CoreSpec = spec) -> CoreResult:
        if is_optional_device_core(_spec.id) and not device_integrations_enabled():
            return CoreResult(
                _spec.id,
                True,
                "disabled_optional",
                result={
                    "core": _spec.id,
                    "name": _spec.name,
                    "enabled": False,
                    "reason": "hardware/companion integrations are optional and disabled by default",
                },
                metadata={
                    "startup_blocking": False,
                    "adapter_required": False,
                    "external_io_performed": False,
                },
            )

        return CoreResult(
            _spec.id,
            True,
            "contract_ready",
            result=_handler_payload(request, _spec),
            metadata={
                "startup_blocking": False,
                "adapter_required": _spec.domain in {"web", "software", "multimodal", "device", "spatial", "companion"},
                "external_io_performed": False,
            },
        )

    return handle


def build_default_handlers(specs: tuple[CoreSpec, ...]) -> dict[str, Any]:
    return {spec.id: make_builtin_handler(spec) for spec in specs}
