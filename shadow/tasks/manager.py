from __future__ import annotations
from dataclasses import dataclass
from typing import List

@dataclass
class Task:
    text: str
    status: str = "pending"

class TaskManager:
    def __init__(self): self._tasks: List[Task] = []
    def add(self, text: str) -> Task:
        task = Task(text.strip()); self._tasks.append(task); return task
    def pending(self): return [t for t in self._tasks if t.status == "pending"]
