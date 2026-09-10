"""Provider-neutral AI orchestration with a real OpenAI HTTP path and safe offline mode."""
from __future__ import annotations
from dataclasses import dataclass
import json, os
from typing import Any, Dict, List, Optional
from urllib.request import Request, urlopen

@dataclass(frozen=True)
class TaskProfile:
    provider: str = "openai"
    model: str = "gpt-5.6"
    max_tokens: int = 2048
    temperature: float = 0.2

class AIOrchestrator:
    def __init__(self, *, profiles: Optional[Dict[str, TaskProfile]] = None):
        self.profiles = profiles or {"default": TaskProfile()}

    def run(self, request: str, *, context: Optional[Dict[str, Any]] = None,
            tools: Optional[List[Dict[str, Any]]] = None, preferred_provider: Optional[str] = None,
            task: str = "default") -> Dict[str, Any]:
        profile = self.profiles.get(task, self.profiles["default"])
        provider = preferred_provider or os.getenv("SHADOW_MODEL_PROVIDER", profile.provider)
        if provider == "openai":
            key = os.getenv("OPENAI_API_KEY", "").strip()
            if key:
                return self._openai(request, key, profile, context or {}, tools or [])
        # Offline mode is explicit: it never pretends to be a remote model.
        return {"answer": self._offline(request), "provider": "offline", "model": "local-safe", "verified": True}

    def _openai(self, request: str, key: str, profile: TaskProfile, context: Dict[str, Any], tools: List[Dict[str, Any]]) -> Dict[str, Any]:
        payload: Dict[str, Any] = {
            "model": profile.model,
            "input": [{"role": "user", "content": request}],
            "max_output_tokens": profile.max_tokens,
            "temperature": profile.temperature,
        }
        if context:
            payload["input"].insert(0, {"role": "system", "content": "SHADOW context: " + json.dumps(context, ensure_ascii=False)})
        if tools:
            payload["tools"] = tools
        req = Request("https://api.openai.com/v1/responses", data=json.dumps(payload).encode(),
                      headers={"Authorization": f"Bearer {key}", "Content-Type": "application/json"}, method="POST")
        with urlopen(req, timeout=45) as response:
            data = json.loads(response.read().decode("utf-8"))
        text = data.get("output_text")
        if not text:
            for item in data.get("output", []):
                for part in item.get("content", []):
                    if part.get("type") == "output_text" and part.get("text"):
                        text = part["text"]; break
                if text: break
        return {"answer": text or "The model returned no text.", "provider": "openai", "model": profile.model, "raw": data}

    @staticmethod
    def _offline(request: str) -> str:
        r = request.strip()
        low = r.lower()
        if low in {"hi", "hello", "hey", "سلام", "اهلا", "أهلا"}:
            return "SHADOW is online. Offline brain is ready; connect a provider for full model reasoning."
        if "status" in low or "حالة" in low:
            return "SHADOW runtime is online. Provider mode is offline-safe because no provider key is configured."
        return f"I received: {r}\nOffline-safe mode is active. A configured AI provider is required for full model reasoning."
