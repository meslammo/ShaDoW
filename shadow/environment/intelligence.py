"""MOD-96: privacy-first environment/device intelligence contracts."""
from __future__ import annotations
from dataclasses import asdict, dataclass, field
from time import time
from typing import Any

@dataclass
class EnvironmentSignal:
    source: str
    kind: str
    label: str
    confidence: float
    metadata: dict[str, Any] = field(default_factory=dict)
    observed_at: float = field(default_factory=time)

class EnvironmentIntelligence:
    """Stores semantic capability signals, not raw sensor/audio/location payloads."""
    def __init__(self):
        self._signals: list[EnvironmentSignal] = []

    def observe(self, source: str, kind: str, label: str, confidence: float = 0.0, **metadata: Any) -> EnvironmentSignal:
        signal = EnvironmentSignal(str(source), str(kind), str(label), max(0.0, min(1.0, float(confidence))), dict(metadata))
        self._signals.insert(0, signal)
        del self._signals[100:]
        return signal

    def snapshot(self) -> dict[str, Any]:
        return {"signals": [asdict(x) for x in self._signals], "raw_payload_retention": False}

    def capabilities(self) -> list[str]:
        return sorted({f"{x.kind}:{x.label}" for x in self._signals})
