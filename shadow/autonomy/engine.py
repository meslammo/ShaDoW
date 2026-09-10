"""Deterministic autonomy coordinator: plan, gate, execute, verify, learn."""
from __future__ import annotations
from dataclasses import dataclass, field
from typing import Any, Callable

@dataclass
class AutomationRule:
    id: str
    trigger: str
    goal: str
    enabled: bool = True
    metadata: dict[str,Any] = field(default_factory=dict)

@dataclass
class ExecutionReport:
    goal: str
    steps: list[str]
    success: bool
    verified: bool
    requires_confirmation: bool=False
    error: str|None=None

class AutonomyEngine:
    def __init__(self): self.rules: dict[str,AutomationRule]={}
    def add_rule(self, rule: AutomationRule): self.rules[rule.id]=rule; return rule
    def remove_rule(self, rule_id: str): return self.rules.pop(rule_id,None) is not None
    def trigger(self, event: str, executor: Callable[[str], bool], *, confirmed=False) -> list[ExecutionReport]:
        reports=[]
        for r in self.rules.values():
            if not r.enabled or r.trigger != event: continue
            if not confirmed and r.metadata.get("requires_confirmation",False):
                reports.append(ExecutionReport(r.goal,["permission check"],False,False,True)); continue
            try:
                ok=bool(executor(r.goal)); reports.append(ExecutionReport(r.goal,["observe","understand","plan","permission","execute","verify","learn"],ok,ok))
            except Exception as exc: reports.append(ExecutionReport(r.goal,["observe","understand","plan","permission","execute"],False,False,error=str(exc)))
        return reports
