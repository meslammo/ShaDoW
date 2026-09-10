"""Unified SHADOW runtime with memory, policy and audit integration."""
from __future__ import annotations
from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional
from shadow.ai.orchestrator.orchestrator import AIOrchestrator
from shadow.goals.manager import GoalManager
from shadow.tasks.manager import TaskManager
from shadow.reasoning.decision.engine import DecisionEngine
from shadow.reasoning.verification.verifier import Verifier
from shadow.memory.persistent import PersistentMemory
from shadow.security.permissions import PermissionManager
from shadow.observability.audit import AuditLog

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
    def __init__(self, orchestrator: Optional[AIOrchestrator] = None, *, memory=None, permissions=None, audit=None):
        self.orchestrator=orchestrator or AIOrchestrator(); self.goals=GoalManager(); self.tasks=TaskManager()
        self.decision=DecisionEngine(); self.verifier=Verifier(); self.memory=memory or PersistentMemory()
        self.permissions=permissions or PermissionManager(); self.audit=audit or AuditLog()
    def handle(self, request: str, *, context: Optional[Dict[str,Any]]=None, tools: Optional[List[Dict[str,Any]]]=None) -> RuntimeResult:
        request=(request or '').strip(); context=dict(context or {})
        if not request: return RuntimeResult('', 'I need a request to act on.', 0.0, True)
        request_id=str(context.get('request_id','')) or None
        memory_hits=self.memory.search(request,limit=5)
        if memory_hits: context['memory']=[m.text for m in memory_hits]
        decision=self.decision.decide(request,context=context)
        confirmation=bool(decision.get('requires_confirmation',False)) if isinstance(decision,dict) else False
        self.audit.record('request.received',request_id=request_id,device_id=context.get('device_id'),metadata={'memory_hits':len(memory_hits)})
        try:
            response=self.orchestrator.run(request,context=context,tools=tools); answer=self._extract(response)
            verified=self.verifier.verify(answer,request); confidence=0.85 if verified and len(answer.strip())>20 else (0.65 if verified else 0.0)
            self.memory.put(f'User: {request}',kind='conversation',tags=('request',),source='runtime')
            self.memory.put(f'SHADOW: {answer}',kind='conversation',tags=('response',),source='runtime')
            self.audit.record('request.completed',request_id=request_id,device_id=context.get('device_id'),outcome='verified' if verified else 'unverified',metadata={'confidence':confidence})
            return RuntimeResult(request,answer,confidence,verified,self._plan(request),[],confirmation,{'runtime':'unified','memory_hits':len(memory_hits),'provider':response.get('provider') if isinstance(response,dict) else None})
        except Exception as exc:
            self.audit.record('request.failed',request_id=request_id,device_id=context.get('device_id'),outcome='failed',metadata={'error':type(exc).__name__})
            return RuntimeResult(request,f'SHADOW could not complete this request safely: {exc}',0.0,False,self._plan(request),[],confirmation)
    def _plan(self,request): return ['observe','understand','plan','permission check','execute','verify','learn']
    def _extract(self,response):
        if isinstance(response,str): return response
        if isinstance(response,dict):
            for key in ('content','answer','text','output'):
                if response.get(key) is not None: return str(response[key])
        return str(response)
