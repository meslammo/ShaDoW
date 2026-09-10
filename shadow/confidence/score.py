from __future__ import annotations
from dataclasses import dataclass

@dataclass(frozen=True)
class ConfidenceScore:
    value: float
    rationale: str = ""
    def __post_init__(self):
        object.__setattr__(self, "value", max(0.0, min(1.0, float(self.value))))
    def __float__(self): return self.value
