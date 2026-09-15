"""MOD-45.9: Development Agent with executable quest delegation."""
from __future__ import annotations
from dataclasses import dataclass
from .engine import DevelopmentEngine, DevelopmentQuest, Stage

@dataclass(frozen=True)
class DevPlan:
    request:str
    stages:tuple[str,...]=( "understand","search","evaluate","plan","approval","edit","test","build","verify","report" )
    approval_required:bool=True
    def to_dict(self)->dict[str,object]:return {"request":self.request,"stages":list(self.stages),"approval_required":self.approval_required}

class DevelopmentAgent:
    def __init__(self)->None:self.engine=DevelopmentEngine();self.quest:DevelopmentQuest|None=None
    def plan(self,request:str)->DevPlan:self.quest=self.engine.start(request);self.quest.mark(Stage.UNDERSTAND,"request accepted");self.quest.mark(Stage.PLAN,"development quest created");return DevPlan(request.strip())
    def start_self_development(self,request:str="طور SHADOW وشوف الناقص وكمله",available_files=())->dict[str,object]:self.quest=self.engine.auto_scope(request,available_files);return self.quest.status()
    def execute(self,approved:bool,edit=None,test=None,build=None,verify=None,rollback=None)->dict[str,object]:
        if self.quest is None:self.quest=self.engine.start("self-development")
        return self.engine.execute(self.quest,approved,edit,test,build,verify,rollback)
    def workspace_rule(self)->dict[str,object]:return {"no_secrets":True,"no_unapproved_write":True,"test_before_report":True,"version_changes":True,"rollback_before_edit":True}
    def next_action(self)->str:return self.engine.next_action(self.quest) if self.quest else "start development quest"
    def status(self)->dict[str,object]:return self.quest.status() if self.quest else {"current":"idle"}
    def health(self)->dict[str,object]:return self.engine.health()
