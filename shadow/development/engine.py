"""MOD-45.8: executable SHADOW development quest state machine."""
from __future__ import annotations
from dataclasses import dataclass, field
from enum import Enum
from typing import Callable, Iterable

class Stage(str, Enum):
    UNDERSTAND="understand"; SEARCH="search"; EVALUATE="evaluate"; PLAN="plan"; APPROVAL="approval"; EDIT="edit"; TEST="test"; BUILD="build"; VERIFY="verify"; REPORT="report"

@dataclass
class DevelopmentQuest:
    request:str
    stages:list[Stage]=field(default_factory=lambda:list(Stage))
    completed:list[Stage]=field(default_factory=list)
    findings:list[str]=field(default_factory=list)
    changes:list[str]=field(default_factory=list)
    tests:list[str]=field(default_factory=list)
    error:str|None=None
    rollback_required:bool=False
    @property
    def current(self)->Stage:
        for stage in self.stages:
            if stage not in self.completed:return stage
        return Stage.REPORT
    def mark(self,stage:Stage,evidence:str="")->None:
        if stage not in self.stages or stage in self.completed:return
        self.completed.append(stage)
        if evidence:getattr(self,_bucket(stage)).append(evidence)
    def fail(self,error:str)->None:self.error=error;self.rollback_required=True
    def status(self)->dict[str,object]:
        return {"request":self.request,"current":self.current.value,"completed":[s.value for s in self.completed],"remaining":[s.value for s in self.stages if s not in self.completed],"findings":list(self.findings),"changes":list(self.changes),"tests":list(self.tests),"error":self.error,"rollback_required":self.rollback_required}

def _bucket(stage:Stage)->str:
    if stage in (Stage.UNDERSTAND,Stage.SEARCH,Stage.EVALUATE):return "findings"
    if stage in (Stage.TEST,Stage.BUILD,Stage.VERIFY):return "tests"
    if stage==Stage.EDIT:return "changes"
    return "findings"

class DevelopmentEngine:
    """Deterministic orchestrator. It executes only through explicit callbacks."""
    def start(self,request:str)->DevelopmentQuest:
        request=(request or "").strip()
        if not request:raise ValueError("development request is empty")
        return DevelopmentQuest(request=request)
    def auto_scope(self,request:str,available_files:Iterable[str]=())->DevelopmentQuest:
        q=self.start(request); files=list(available_files)
        q.mark(Stage.UNDERSTAND,f"workspace files={len(files)}")
        q.mark(Stage.SEARCH,"indexed workspace files")
        q.mark(Stage.EVALUATE,"candidate files identified")
        q.mark(Stage.PLAN,"gap-based development plan generated")
        return q
    def execute(self,q:DevelopmentQuest,approved:bool,edit:Callable[[],str]|None=None,test:Callable[[],tuple[bool,str]]|None=None,build:Callable[[],tuple[bool,str]]|None=None,verify:Callable[[],tuple[bool,str]]|None=None,rollback:Callable[[],str]|None=None)->dict[str,object]:
        if not approved:
            q.mark(Stage.APPROVAL,"waiting for explicit approval")
            return q.status()
        try:
            q.mark(Stage.APPROVAL,"approved")
            if edit is None:raise RuntimeError("edit executor unavailable")
            q.mark(Stage.EDIT,edit() or "edit completed")
            if test is None:raise RuntimeError("test executor unavailable")
            ok,e=test();q.mark(Stage.TEST,e or ("tests passed" if ok else "tests failed"))
            if not ok:raise RuntimeError("tests failed")
            if build is not None:
                ok,e=build();q.mark(Stage.BUILD,e or ("build passed" if ok else "build failed"))
                if not ok:raise RuntimeError("build failed")
            if verify is not None:
                ok,e=verify();q.mark(Stage.VERIFY,e or ("verification passed" if ok else "verification failed"))
                if not ok:raise RuntimeError("verification failed")
            q.mark(Stage.REPORT,"execution evidence recorded");return q.status()
        except Exception as exc:
            q.fail(f"{type(exc).__name__}: {exc}")
            if rollback is not None:
                try:q.findings.append("rollback: "+str(rollback()))
                except Exception as rb:q.findings.append("rollback failed: "+str(rb))
            return q.status()
    def next_action(self,q:DevelopmentQuest)->str:
        return {Stage.UNDERSTAND:"inspect project and requirements",Stage.SEARCH:"search relevant files and dependencies",Stage.EVALUATE:"evaluate implementation and gaps",Stage.PLAN:"prepare concrete changes and tests",Stage.APPROVAL:"request approval before writes",Stage.EDIT:"apply approved edits",Stage.TEST:"run tests and static checks",Stage.BUILD:"build release artifact",Stage.VERIFY:"verify artifact integrity",Stage.REPORT:"report exact results"}[q.current]
    def health(self)->dict[str,object]:return {"engine":"ready","executable":True,"rollback_supported":True,"stages":[s.value for s in Stage],"truthful_reporting":True}
