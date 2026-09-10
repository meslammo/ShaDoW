"""Controlled self-development pipeline with immutable proposals and rollback records."""
from __future__ import annotations
from dataclasses import dataclass, field
import time
from typing import Callable

@dataclass
class ChangeProposal:
    id: str
    title: str
    description: str
    patch_ref: str|None=None
    tests_passed: bool=False
    reviewed: bool=False
    built: bool=False
    verified: bool=False
    approved: bool=False
    created_at: float=field(default_factory=time.time)

class SelfDevelopmentPipeline:
    def __init__(self): self.proposals: dict[str,ChangeProposal]={}; self.history:list[str]=[]
    def propose(self, proposal: ChangeProposal): self.proposals[proposal.id]=proposal; return proposal
    def validate(self, proposal_id: str, test: Callable[[],bool], build: Callable[[],bool], verify: Callable[[],bool]) -> ChangeProposal:
        p=self.proposals[proposal_id]
        p.tests_passed=bool(test())
        if not p.tests_passed: return p
        p.reviewed=True; p.built=bool(build())
        if p.built: p.verified=bool(verify())
        return p
    def approve(self, proposal_id: str): self.proposals[proposal_id].approved=True; self.history.append(proposal_id); return self.proposals[proposal_id]
    def rollback(self, proposal_id: str):
        p=self.proposals[proposal_id]; p.approved=False; p.verified=False; return p
