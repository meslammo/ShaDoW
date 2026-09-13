"""MOD-46.18: evidence-first web agent contract.

The agent does not invent results. Online retrieval is delegated to the
trusted runtime/provider; every result is represented as evidence.
"""
from dataclasses import dataclass
from typing import Iterable


@dataclass(frozen=True)
class WebEvidence:
    title: str
    url: str
    snippet: str = ""


class WebAgent:
    def __init__(self, provider=None):
        self.provider = provider

    def search(self, query: str) -> tuple[WebEvidence, ...]:
        q = (query or "").strip()
        if not q:
            return ()
        if self.provider is None:
            return ()
        raw = self.provider.search(q)
        return tuple(
            WebEvidence(str(x.get("title", "")), str(x.get("url", "")), str(x.get("snippet", "")))
            for x in raw
            if isinstance(x, dict) and x.get("url")
        )

    def status(self) -> dict[str, object]:
        return {"web_agent": "ready", "provider_connected": self.provider is not None, "evidence_required": True}
