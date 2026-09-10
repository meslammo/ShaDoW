from __future__ import annotations
from dataclasses import dataclass
from typing import List

@dataclass
class Goal:
    text: str
    status: str = "active"

class GoalManager:
    def __init__(self): self._goals: List[Goal] = []
    def add(self, text: str) -> Goal:
        goal = Goal(text.strip()); self._goals.append(goal); return goal
    def active(self): return [g for g in self._goals if g.status == "active"]
