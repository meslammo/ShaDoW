"""Optional hardware/companion capability policy.

Hardware integrations are never part of SHADOW startup. They are disabled by
default and can be enabled explicitly when an adapter exists.
"""
from __future__ import annotations

import os

OPTIONAL_DEVICE_CORE_IDS = frozenset(f"CORE-{i:03d}" for i in range(113, 132))


def device_integrations_enabled() -> bool:
    return os.getenv("SHADOW_ENABLE_DEVICE_CORES", "").strip().lower() in {
        "1", "true", "yes", "on"
    }


def is_optional_device_core(core_id: str) -> bool:
    return core_id in OPTIONAL_DEVICE_CORE_IDS


def optional_core_status(core_id: str) -> str:
    if is_optional_device_core(core_id) and not device_integrations_enabled():
        return "disabled_optional"
    return "enabled"
