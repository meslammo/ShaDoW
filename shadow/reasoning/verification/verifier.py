from __future__ import annotations
from typing import Any

class Verifier:
    def verify(self, answer: Any, request: str = "") -> bool:
        if answer is None: return False
        text = str(answer).strip()
        return bool(text) and not text.lower().startswith(("error:", "exception:"))
