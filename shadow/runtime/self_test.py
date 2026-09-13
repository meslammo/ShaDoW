"""MOD-37.4: SHADOW runtime self-test and health snapshot."""
from __future__ import annotations
from dataclasses import dataclass

@dataclass(frozen=True)
class Check:
    name:str; ok:bool; detail:str=""

class SelfTest:
    def run(self,runtime)->dict:
        checks=[]
        for name,fn in (("capabilities",runtime.capabilities),("status",runtime.status)):
            try: fn(); checks.append(Check(name,True,"ok"))
            except Exception as e: checks.append(Check(name,False,type(e).__name__))
        return {"ok":all(c.ok for c in checks),"checks":[c.__dict__ for c in checks]}
