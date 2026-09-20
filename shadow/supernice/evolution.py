"""Bounded self-evolution: stage, test, approve, activate, rollback."""
from __future__ import annotations
from dataclasses import dataclass
from hashlib import sha256
from typing import Iterable

@dataclass(frozen=True)
class EvolutionProposal:
    proposal_id:str; title:str; reason:str
    files:tuple[str,...]; test_commands:tuple[str,...]
    rollback_required:bool=True; requires_approval:bool=True

class SelfEvolution:
    def propose(self,title:str,reason:str,files:Iterable[str],tests:Iterable[str])->EvolutionProposal:
        files=tuple(files); tests=tuple(tests)
        pid=sha256("|".join((title,reason,*files,*tests)).encode()).hexdigest()[:16]
        return EvolutionProposal(pid,title,reason,files,tests)

    @staticmethod
    def activation_allowed(p:EvolutionProposal,*,approved_by_master:bool,tests_passed:bool)->bool:
        return bool(approved_by_master and tests_passed and p.requires_approval)
