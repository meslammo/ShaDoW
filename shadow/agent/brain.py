"""MOD-37.1: SHADOW Agent Brain planner."""
from __future__ import annotations
from dataclasses import dataclass, asdict
from enum import Enum

class StepKind(str, Enum):
    THINKING="thinking"; READING="reading"; SEARCHING="searching"; ANALYZING="analyzing"
    WRITING="writing"; EXECUTING="executing"; TESTING="testing"; DESIGNING="designing"
    LISTENING="listening"; SPEAKING="speaking"; DONE="done"

@dataclass(frozen=True)
class PlanStep:
    id:int; kind:StepKind; action:str; requires_approval:bool=False

@dataclass(frozen=True)
class AgentPlan:
    request:str; steps:tuple[PlanStep,...]; risk:str="low"
    def to_dict(self):
        return {"request":self.request,"risk":self.risk,"steps":[{**asdict(s),"kind":s.kind.value} for s in self.steps]}

class AgentBrain:
    """Rule-first planner. Execution remains behind existing Android policy gates."""
    def plan(self, request:str)->AgentPlan:
        x=request.lower(); steps=[PlanStep(1,StepKind.THINKING,"understand request")]; n=2
        if any(k in x for k in ("file","zip","repo","code","ملف","مشروع","كود")):
            steps.append(PlanStep(n,StepKind.READING,"inspect supplied project/files")); n+=1
        if any(k in x for k in ("search","latest","web","ابحث","أحدث")):
            steps.append(PlanStep(n,StepKind.SEARCHING,"search and verify current sources")); n+=1
        if any(k in x for k in ("analy","calculate","compare","حلل","احسب","قارن")):
            steps.append(PlanStep(n,StepKind.ANALYZING,"analyze evidence and compute results")); n+=1
        if any(k in x for k in ("design","image","draw","صمم","صورة")):
            steps.append(PlanStep(n,StepKind.DESIGNING,"produce requested design artifact")); n+=1
        if any(k in x for k in ("write","edit","create","build","عدّل","اكتب","ابني")):
            steps.append(PlanStep(n,StepKind.WRITING,"prepare changes")); n+=1
        if any(k in x for k in ("open","tap","click","phone","app","wifi","tv","ac","افتح","اضغط","موبايل")):
            steps.append(PlanStep(n,StepKind.EXECUTING,"execute approved device action",True)); n+=1
        if any(k in x for k in ("test","build","run","اختبر")):
            steps.append(PlanStep(n,StepKind.TESTING,"run validation and report evidence")); n+=1
        steps.extend([PlanStep(n,StepKind.SPEAKING,"report result"),PlanStep(n+1,StepKind.DONE,"complete")])
        return AgentPlan(request,tuple(steps),"approval-required" if any(s.requires_approval for s in steps) else "low")
