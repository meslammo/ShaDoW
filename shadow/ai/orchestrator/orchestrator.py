"""Provider-neutral AI orchestration with real OpenAI tool execution."""
from __future__ import annotations
from dataclasses import dataclass
import json, os
from typing import Any, Callable, Dict, List, Optional
from urllib.request import Request, urlopen

@dataclass(frozen=True)
class TaskProfile:
    provider: str = "openai"
    model: str = "gpt-5.6"
    max_tokens: int = 2048
    temperature: float = 0.2

class AIOrchestrator:
    def __init__(self, *, profiles: Optional[Dict[str, TaskProfile]] = None, max_tool_rounds: int = 8):
        self.profiles = profiles or {"default": TaskProfile()}
        self.max_tool_rounds = max(1, min(int(max_tool_rounds), 16))

    def run(self, request: str, *, context: Optional[Dict[str, Any]] = None,
            tools: Optional[List[Dict[str, Any]]] = None, preferred_provider: Optional[str] = None,
            task: str = "default", tool_executor: Optional[Callable[[str, Dict[str, Any]], Any]] = None,
            confirmed_actions: Optional[List[str]] = None) -> Dict[str, Any]:
        profile = self.profiles.get(task, self.profiles["default"])
        provider = preferred_provider or os.getenv("SHADOW_MODEL_PROVIDER", profile.provider)
        if provider == "openai":
            key = os.getenv("OPENAI_API_KEY", "").strip()
            if key:
                return self._openai(request, key, profile, context or {}, tools or [], tool_executor, confirmed_actions or [])
        return {"answer": self._offline(request), "provider": "offline", "model": "local-safe", "verified": True, "actions": []}

    def _openai(self, request: str, key: str, profile: TaskProfile, context: Dict[str, Any],
                tools: List[Dict[str, Any]], tool_executor: Optional[Callable[[str, Dict[str, Any]], Any]],
                confirmed_actions: List[str]) -> Dict[str, Any]:
        inputs: List[Dict[str, Any]] = [{"role":"user", "content":request}]
        if context:
            inputs.insert(0, {"role":"system", "content":"SHADOW context: " + json.dumps(context, ensure_ascii=False)})
        actions: List[Dict[str, Any]] = []
        last_data: Dict[str, Any] = {}
        for _round in range(self.max_tool_rounds):
            payload: Dict[str, Any] = {"model":profile.model, "input":inputs,
                "max_output_tokens":profile.max_tokens, "temperature":profile.temperature}
            if tools:
                payload["tools"] = tools
            data = self._post(payload, key)
            last_data = data
            calls = [x for x in data.get("output", []) if x.get("type") == "function_call"]
            if not calls or tool_executor is None:
                return {"answer":self._extract_text(data) or "الموديل رجّع رد فاضي.",
                        "provider":"openai", "model":profile.model, "raw":data, "actions":actions}
            inputs.extend(data.get("output", []))
            for call in calls:
                name = str(call.get("name", ""))
                try:
                    arguments = json.loads(call.get("arguments") or "{}")
                    if not isinstance(arguments, dict):
                        raise ValueError("tool arguments must be an object")
                    result = tool_executor(name, {**arguments, "confirmed": name in confirmed_actions})
                    output = result if isinstance(result, str) else json.dumps(result, ensure_ascii=False, default=str)
                    actions.append({"tool":name, "arguments":arguments, "result":output})
                except Exception as exc:
                    output = json.dumps({"success":False,"error":str(exc)}, ensure_ascii=False)
                    actions.append({"tool":name,"arguments":call.get("arguments"),"result":output})
                inputs.append({"type":"function_call_output", "call_id":call.get("call_id"), "output":output})
        return {"answer":self._extract_text(last_data) or "وقفت تنفيذ الأدوات عند حد الأمان.",
                "provider":"openai", "model":profile.model, "raw":last_data, "actions":actions,
                "tool_round_limit":self.max_tool_rounds}

    @staticmethod
    def _post(payload: Dict[str, Any], key: str) -> Dict[str, Any]:
        req = Request("https://api.openai.com/v1/responses", data=json.dumps(payload).encode(),
                      headers={"Authorization":f"Bearer {key}","Content-Type":"application/json"}, method="POST")
        with urlopen(req, timeout=45) as response:
            return json.loads(response.read().decode("utf-8"))

    @staticmethod
    def _extract_text(data: Dict[str, Any]) -> str:
        text = data.get("output_text")
        if text:
            return str(text)
        for item in data.get("output", []):
            for part in item.get("content", []) or []:
                if part.get("type") == "output_text" and part.get("text"):
                    return str(part["text"])
        return ""

    @staticmethod
    def _offline(request: str) -> str:
        r=request.strip(); low=r.lower()
        if low in {"hi","hello","hey","سلام","اهلا","أهلا","مرحبا"}:
            return "أهلاً محمد 👋 أنا SHADOW. أنا شغال محلياً دلوقتي، وتقدر تكلمني كتابة أو بالصوت."
        if "status" in low or "حالة" in low:
            return "أنا شغال. الواجهة والصوت والذاكرة المحلية متاحين، ومحرك الذكاء السحابي غير موصل حالياً."
        if "مين انت" in low or "ما انت" in low or "who are you" in low:
            return "أنا SHADOW، مساعد Android بواجهة محادثة، صوت، ذاكرة محلية، وأدوات للهاتف، ومعايا Python runtime مدمج."
        if "شكرا" in low or "thanks" in low:
            return "العفو يا محمد."
        return f"فهمت رسالتك: {r}\nأنا حالياً في الوضع المحلي الآمن. أقدر أنفذ الوظائف المحلية المتاحة، وللإجابات الذكية الكاملة يحتاج مزود AI متصل."
