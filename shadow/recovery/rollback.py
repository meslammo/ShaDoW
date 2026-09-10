"""Release checkpoint and rollback bookkeeping."""
from __future__ import annotations
from dataclasses import dataclass
import time

@dataclass(frozen=True)
class Checkpoint:
    id: str
    ref: str
    created_at: float
    description: str=""

class RollbackManager:
    def __init__(self): self.checkpoints:list[Checkpoint]=[]
    def checkpoint(self, ref: str, description: str="") -> Checkpoint:
        cp=Checkpoint(f"cp-{len(self.checkpoints)+1}",ref,time.time(),description); self.checkpoints.append(cp); return cp
    def latest(self)->Checkpoint|None: return self.checkpoints[-1] if self.checkpoints else None
    def target(self, checkpoint_id: str)->Checkpoint|None: return next((x for x in self.checkpoints if x.id==checkpoint_id),None)
