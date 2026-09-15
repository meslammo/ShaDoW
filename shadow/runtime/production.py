"""MOD-47.2: unified production runtime with active Development Engine and governance."""
from __future__ import annotations
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any
from ..agent.brain import AgentBrain
from ..ai.local_provider import LocalAI
from ..analysis.ev_engine import analyze
from ..development.agent import DevelopmentAgent
from ..development.file_understanding import summarize
from ..development.github_authorization import GitHubAuthorization
from ..design.site_building import Site, propose
from ..device.capabilities import DeviceCapabilities
from ..governance import Governance
from ..memory.store import MemoryStore
from ..perception.local_router import LocalPerceptionRouter
from ..perception.vision import VisionRouter
from ..runtime.self_test import SelfTest
from ..skills.registry import SkillRegistry
from ..voice.local_voice import LocalVoice

@dataclass(frozen=True)
class RuntimeCapabilities:
    analysis:bool; local_ai:bool; local_voice_adapter:bool; perception_router:bool
    vision_router:bool; file_understanding:bool; design:bool; skills:bool
    github_authorization:bool; agent_brain:bool; memory:bool; development_agent:bool
    self_test:bool; device_capability_model:bool; development_engine:bool; governance:bool
    def to_dict(self)->dict[str,bool]: return asdict(self)

class ProductionRuntime:
    def __init__(self,workspace:str|Path|None=None):
        self.workspace=Path(workspace or ".").resolve()
        self.ai=LocalAI(); self.voice=LocalVoice(); self.perception=LocalPerceptionRouter(); self.vision=VisionRouter()
        self.skills=SkillRegistry(); self.skills.defaults(); self.github=GitHubAuthorization.pending()
        self.brain=AgentBrain(); self.development=DevelopmentAgent(); self.memory=MemoryStore(self.workspace/".shadow/memory.json"); self.self_test=SelfTest()
        self.governance=Governance()
    def capabilities(self)->RuntimeCapabilities:
        return RuntimeCapabilities(True,True,True,True,True,True,True,True,self.github.authorized,True,True,True,True,True,True,True)
    def chat_local(self,prompt:str)->dict[str,Any]:
        result=self.ai.run(prompt); self.memory.remember(prompt,"conversation"); return {"text":result.text,"provider":result.provider,"model":result.model,"verified":result.verified}
    def plan(self,request:str)->dict[str,Any]: return self.brain.plan(request).to_dict()
    def develop(self,request:str="طور SHADOW وشوف الناقص وكمله")->dict[str,Any]:
        files=[]
        if self.workspace.exists():
            files=[str(p.relative_to(self.workspace)) for p in self.workspace.rglob('*') if p.is_file() and '.shadow' not in p.parts]
        result=self.development.start_self_development(request,files)
        result["next_action"]=self.development.next_action()
        self.memory.remember(request,"development_request")
        return result
    def analyze_numbers(self,values:list[float])->dict[str,Any]:
        r=analyze(values); return {"count":r.count,"mean":r.mean,"median":r.median,"minimum":r.minimum,"maximum":r.maximum,"standard_deviation":r.standard_deviation,"trend":r.trend}
    def inspect_file(self,path:str)->dict[str,Any]:
        target=(self.workspace/path).resolve()
        if target!=self.workspace and self.workspace not in target.parents: raise PermissionError("path escapes SHADOW workspace")
        return asdict(summarize(target))
    def perceive_image(self,data:bytes,name:str="image/jpeg")->dict[str,Any]: return asdict(self.perception.inspect_image(data,mime_type=name))
    def perceive_audio(self,data:bytes,sample_rate:int=16000)->dict[str,Any]: return asdict(self.perception.inspect_audio(data,sample_rate=sample_rate))
    def vision_receipt(self,data:bytes,mime_type:str)->dict[str,Any]: return self.vision.receipt(data,mime_type)
    def design_site(self,width:float,depth:float,rooms:list[str],floors:int=1,coverage:float=.60)->dict[str,Any]: return propose(Site(width,depth),rooms,floors,coverage).to_dict()
    def authorization_request(self,account:str,permissions:list[str])->dict[str,Any]:
        self.github=GitHubAuthorization(False,tuple(permissions),account); return self.github.summary()
    def status(self)->dict[str,Any]:
        return {"workspace":str(self.workspace),"capabilities":self.capabilities().to_dict(),"development":self.development.status(),"development_engine":self.development.health(),"governance":self.governance.status(),"voice":self.voice.status(),"github":self.github.summary(),"memory":self.memory.status(),"skills":[asdict(s) for s in self.skills.list()]}
    def health(self)->dict[str,Any]: return self.self_test.run(self)
