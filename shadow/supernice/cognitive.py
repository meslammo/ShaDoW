"""Structured natural-language request analysis for SHADOW.

This first cognitive layer is deliberately provider-neutral and conservative:
it extracts explicit signals without claiming that heuristics understand intent
as well as an online model. Model-backed planning can enrich this contract.
"""
from __future__ import annotations

from dataclasses import asdict, dataclass, field
import re
from typing import Any, Mapping


@dataclass(frozen=True)
class CognitiveIntent:
    raw_request: str
    normalized_request: str
    language_hint: str
    intent_type: str
    goal: str
    constraints: tuple[str, ...] = ()
    requested_outputs: tuple[str, ...] = ()
    needs_clarification: bool = False
    clarification_reason: str = ""
    confidence: float = 0.0
    source: str = "conservative_local_analysis"

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


class CognitiveEngine:
    """Build a safe, explicit request contract before model/tool execution."""

    VERSION = "1.0.0"

    _QUESTION = re.compile(r"^(?:what|why|how|when|where|who|which|can you|could you|explain|اشرح|يعني|ليه|ازاي|إزاي|متى|فين|ما هو|ما هي)\b", re.I)
    _CREATE = re.compile(r"\b(?:create|build|make|write|generate|design|develop|أعمل|اعمل|اعملي|اعمللي|أنشئ|انشئ|صمم|صمّم|ابني|اكتب|طوّر|طور)\b", re.I)
    _CHANGE = re.compile(r"\b(?:edit|update|modify|fix|change|repair|عدّل|عدل|حدّث|حدث|غيّر|غير|اصلح|أصلح)\b", re.I)
    _RESEARCH = re.compile(r"\b(?:search|find|research|look up|compare|ابحث|دور|هات|قارن|اعرف)\b", re.I)
    _ACTION = re.compile(r"\b(?:run|execute|deploy|publish|send|delete|merge|commit|push|نفّذ|نفذ|شغّل|شغل|انشر|ابعت|احذف|ادمج)\b", re.I)
    _CONSTRAINTS = (
        ("no_merge", re.compile(r"\b(?:do not merge|don't merge|no merge|لا تدمج|ممنوع الدمج)\b", re.I)),
        ("no_publish", re.compile(r"\b(?:do not publish|don't publish|no release|لا تنشر|بدون نشر)\b", re.I)),
        ("free_only", re.compile(r"\b(?:free|no cost|بدون تكلفة|مجاني|مجانا)\b", re.I)),
        ("preserve_existing", re.compile(r"\b(?:preserve|keep existing|don't rebuild|حافظ على|بدون إعادة بناء|من غير إعادة بناء)\b", re.I)),
    )

    def analyze(self, request: str, context: Mapping[str, Any] | None = None) -> CognitiveIntent:
        raw = str(request or "")
        normalized = " ".join(raw.strip().split())
        if not normalized:
            return CognitiveIntent(raw, "", "unknown", "empty", "", needs_clarification=True,
                                   clarification_reason="request_required", confidence=1.0)

        has_arabic = bool(re.search(r"[\u0600-\u06FF]", normalized))
        has_latin = bool(re.search(r"[A-Za-z]", normalized))
        language = "mixed" if has_arabic and has_latin else "ar" if has_arabic else "en" if has_latin else "unknown"

        if self._QUESTION.search(normalized) or normalized.endswith("?") or normalized.endswith("؟"):
            kind = "question"
        elif self._ACTION.search(normalized):
            kind = "action"
        elif self._CREATE.search(normalized):
            kind = "creation"
        elif self._CHANGE.search(normalized):
            kind = "modification"
        elif self._RESEARCH.search(normalized):
            kind = "research"
        else:
            kind = "conversation"

        constraints = tuple(name for name, pattern in self._CONSTRAINTS if pattern.search(normalized))
        clarification = len(normalized) < 3
        reason = "request_too_short" if clarification else ""
        return CognitiveIntent(
            raw_request=raw,
            normalized_request=normalized,
            language_hint=language,
            intent_type=kind,
            goal=normalized,
            constraints=constraints,
            needs_clarification=clarification,
            clarification_reason=reason,
            confidence=0.55 if kind != "conversation" else 0.35,
        )

    def build_model_context(
        self, request: str, context: Mapping[str, Any] | None = None
    ) -> dict[str, Any]:
        intent = self.analyze(request, context)
        return {
            "schema": "shadow.cognitive-request.v1",
            "engine_version": self.VERSION,
            "intent": intent.to_dict(),
            "planning_instructions": [
                "Infer the user's intended outcome from the complete conversation context.",
                "Do not treat this heuristic intent label as ground truth.",
                "Ask one concise clarification only when a missing detail blocks safe or useful progress.",
                "Create internal task steps and tool instructions; the user should not need to write prompts.",
                "Use registered tools only when they materially advance the request.",
                "Never claim an action succeeded without tool evidence.",
                "Require explicit approval before destructive, external, financial, publishing, or repository-mutating actions.",
                "Respect all explicit constraints and preserve existing project architecture.",
            ],
            "conversation_context_available": bool(context),
        }
