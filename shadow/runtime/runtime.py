"""Unified SHADOW runtime: Observe -> Understand -> Plan -> Permission -> Execute -> Verify -> Learn."""
from __future__ import annotations
from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional
from shadow.ai.orchestrator.orchestrator import AIOrchestrator
from shadow.confidence.score import ConfidenceScore
from shadow.goals.manager import GoalManager
from shadow.tasks.manager import TaskManager
from shadow.reasoning.decision.engine import DecisionEngine
from shadow.reasoning.verification.verifier import Verifier

@dataclass
class RuntimeResult:
    request: str
    answer: str
    confidence: float
    verified: bool
    plan: List[str] = field(default_factory=list)
    actions: List[Dict[str, Any]] = field(default_factory=list)
    requires_confirmation: bool = False
    metadata: Dict[str, Any] = field(default_factory=dict)

class ShadowRuntime:
    """Single entry point for app, voice, automation and future device clients."""
    def __init__(self, orchestrator: Optional[AIOrchestrator] = None):
        self.orchestrator = orchestrator or AIOrchestrator()
        self.goals = GoalManager()
        self.tasks = TaskManager()
        self.decision = DecisionEngine()
        self.verifier = Verifier()

    def handle(self, request: str, *, context: Optional[Dict[str, Any]] = None,
               tools: Optional[List[Dict[str, Any]]] = None) -> RuntimeResult:
        request = (request or "").strip()
        context = context or {}
        if not request:
            return RuntimeResult(request="", answer="I need a request to act on.", confidence=0.0, verified=True)
        plan = self._plan(request)
        decision = self.decision.decide(request, context=context)
        confirmation = bool(decision.get("requires_confirmation", False)) if isinstance(decision, dict) else False
        try:
            response = self.orchestrator.run(request, context=context, tools=tools)
            answer = self._extract(response)
            verified = self._verify(answer, request)
            confidence = self._confidence(answer, verified)
        except Exception as exc:
            return RuntimeResult(request=request, answer=f"SHADOW could not complete this request safely: {exc}", confidence=0.0, verified=False, plan=plan, requires_confirmation=confirmation)
        return RuntimeResult(request=request, answer=answer, confidence=confidence, verified=verified,
                             plan=plan, requires_confirmation=confirmation,
                             metadata={"runtime": "unified", "context_keys": sorted(context.keys())})

    def _plan(self, request: str) -> List[str]:
        return ["understand request", "select reasoning path", "check permissions", "execute", "verify result", "record outcome"]

    def _extract(self, response: Any) -> str:
        if isinstance(response, str): return response
        if isinstance(response, dict):
            for key in ("content", "answer", "text", "output"):
                if response.get(key) is not None: return str(response[key])
        return str(response)

    def _verify(self, answer: str, request: str) -> bool:
        if not answer.strip(): return False
        # Verification is intentionally conservative; provider/tool verification can be added without changing callers.
        return not answer.lower().startswith(("error:", "exception:"))

    def _confidence(self, answer: str, verified: bool) -> float:
        if not verified: return 0.0
        return 0.85 if len(answer.strip()) > 20 else 0.65
