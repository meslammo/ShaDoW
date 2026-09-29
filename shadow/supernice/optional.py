"""Retired external-device capability policy.

Device/car/smart-home/wearable/companion integrations are outside the active
Shadow AI build. Their historical Core contracts remain for compatibility and
catalog completeness, but they can never be enabled by runtime configuration.
"""
from __future__ import annotations

OPTIONAL_DEVICE_CORE_IDS = frozenset(f"CORE-{i:03d}" for i in range(113, 132))


def device_integrations_enabled() -> bool:
    return False


def is_optional_device_core(core_id: str) -> bool:
    return core_id in OPTIONAL_DEVICE_CORE_IDS


def optional_core_status(core_id: str) -> str:
    if is_optional_device_core(core_id):
        return "disabled_out_of_scope"
    return "enabled"
