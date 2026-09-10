"""Unified SHADOW runtime: memory, policy, tools, execution and audit."""
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
from shadow.devices.registry import DeviceRegistry
from shadow.runtime.action_executor import ActionExecutor, ActionResult
from shadow.tools.builtins import build_builtin_registry

@dataclass
class RuntimeResult:
    request:str; answer:str; confidence:float; verified:bool
    plan:List[str]=field(default_factory=list); actions:List[Dict[str,Any]]=field(default_factory=list)
    requires_confirmation:bool=False; metadata:Dict[str,Any]=field(default_factory=dict)

class ShadowRuntime:
    def __init__(self, orchestrator:Optional[AIOrchestrator]=None, *, memory=None, permissions=None,
                 audit=None, devices=None, action_executor=None):
        self.orchestrator=orchestrator or AIOrchestrator()
        self.goals=GoalManager(); self.tasks=TaskManager(); self.decision=DecisionEngine(); self.verifier=Verifier()
        self.memory=memory or PersistentMemory(); self.permissions=permissions or PermissionManager(); self.audit=audit or AuditLog()
        self.devices=devices or DeviceRegistry()
        self.executor=action_executor or ActionExecutor(self.permissions)
        self.tools=build_builtin_registry(memory=self.memory, devices=self.devices)
        for spec in self.tools._tools.values():
            self.executor.register(spec.name, spec.handler)

    def register_action(self, capability: str, handler: Any) -> None:
        """Register a real adapter; permission policy remains authoritative."""
        self.executor.register(capability, handler)

    def handle(self, request:str, *, context:Optional[Dict[str,Any]]=None,
               tools:Optional[List[Dict[str,Any]]]=None) -> RuntimeResult:
        request=(request or '').strip(); context=dict(context or {})
        if not request:
            return RuntimeResult('', 'I need a request to act on.',0.0,True)
        request_id=str(context.get('request_id','')) or None
        hits=self.memory.search(request,5)
        if hits: context['memory']=[m.text for m in hits]
        decision=self.decision.decide(request,context=context)
        confirmation=bool(decision.get('requires_confirmation',False)) if isinstance(decision,dict) else False
        confirmed_actions=context.get('confirmed_actions', [])
        if not isinstance(confirmed_actions,list): confirmed_actions=[]
        self.audit.record('request.received',request_id=request_id,device_id=context.get('device_id'),metadata={'memory_hits':len(hits)})
        try:
            schemas=self.tools.openai_schemas()
            if tools:
                schemas.extend(tools)
            response=self.orchestrator.run(request,context=context,tools=schemas,
                                           tool_executor=self._execute_tool,
                                           confirmed_actions=[str(x) for x in confirmed_actions])
            answer=self._extract(response); verified=self.verifier.verify(answer,request)
            confidence=0.85 if verified and len(answer.strip())>20 else (0.65 if verified else 0.0)
            actions=list(response.get('actions',[])) if isinstance(response,dict) else []
            if any(a.get('result','').find('user confirmation required') >= 0 for a in actions):
                confirmation=True
            self.memory.put(f'User: {request}',kind='conversation',tags=('request',))
            self.memory.put(f'SHADOW: {answer}',kind='conversation',tags=('response',))
            self.audit.record('request.completed',request_id=request_id,device_id=context.get('device_id'),
                              outcome='verified' if verified else 'unverified',metadata={'confidence':confidence,'actions':len(actions)})
            return RuntimeResult(request,answer,confidence,verified,self._plan(request),actions,confirmation,
                                 {'runtime':'unified','memory_hits':len(hits),'provider':response.get('provider') if isinstance(response,dict) else None})
        except Exception as exc:
            self.audit.record('request.failed',request_id=request_id,device_id=context.get('device_id'),outcome='failed',metadata={'error':type(exc).__name__})
            return RuntimeResult(request,f'SHADOW could not complete this request safely: {exc}',0.0,False,self._plan(request),[],confirmation)

    def _execute_tool(self, name:str, arguments:Dict[str,Any]) -> Dict[str,Any]:
        confirmed=bool(arguments.pop('confirmed',False))
        result:ActionResult=self.executor.execute(name,confirmed=confirmed,**arguments)
        payload={'success':result.success,'output':result.output,'requires_confirmation':result.requires_confirmation,'metadata':result.metadata}
        self.audit.record('tool.executed',metadata={'tool':name,'success':result.success,'requires_confirmation':result.requires_confirmation})
        return payload

    def _plan(self,request):
        return ['observe','understand','plan','check permissions','execute','verify','learn']

    @staticmethod
    def _extract(response):
        if isinstance(response,str): return response
        if isinstance(response,dict):
            for key in ('content','answer','text','output'):
                if response.get(key) is not None:return str(response[key])
        return str(response)
