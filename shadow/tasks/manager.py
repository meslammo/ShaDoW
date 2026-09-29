"""MOD-99: bounded multi-step task manager with checkpoint/recovery support."""
from __future__ import annotations
from dataclasses import asdict, dataclass, field
from time import time
from typing import Any, List

@dataclass
class Task:
    text: str
    status: str = "pending"
    task_id: str = ""
    steps: List[str] = field(default_factory=list)
    current_step: int = 0
    attempts: int = 0
    created_at: float = field(default_factory=time)
    updated_at: float = field(default_factory=time)
    checkpoint: dict[str, Any] = field(default_factory=dict)
    result: Any = None
    error: str = ""

class TaskManager:
    def __init__(self, max_steps: int = 32, max_attempts: int = 3):
        self._tasks: List[Task] = []
        self.max_steps = max(1, int(max_steps))
        self.max_attempts = max(0, int(max_attempts))

    def add(self, text: str, *, steps: list[str] | None = None, task_id: str = "") -> Task:
        prepared = [str(x).strip() for x in (steps or []) if str(x).strip()][:self.max_steps]
        task = Task(str(text).strip(), "pending", str(task_id), prepared)
        self._tasks.append(task)
        return task

    def start(self, task: Task) -> Task:
        task.status = "running"; task.updated_at = time(); return task

    def checkpoint(self, task: Task, *, result: Any = None, step: int | None = None) -> Task:
        if step is not None:
            task.current_step = max(0, min(int(step), max(0, len(task.steps)-1)))
        task.checkpoint = {"step": task.current_step, "result": result, "timestamp": time()}
        task.updated_at = time()
        return task

    def complete(self, task: Task, result: Any = None) -> Task:
        task.status = "completed"; task.result = result; task.updated_at = time(); return task

    def fail(self, task: Task, error: str, *, retry: bool = True) -> Task:
        task.attempts += 1; task.error = str(error); task.updated_at = time()
        if retry and task.attempts <= self.max_attempts:
            task.status = "retry_pending"
        else:
            task.status = "failed"
        return task

    def cancel(self, task: Task) -> Task:
        task.status = "cancelled"; task.updated_at = time(); return task

    def pending(self):
        return [t for t in self._tasks if t.status in {"pending", "retry_pending", "running"}]

    def get(self, task_id: str) -> Task | None:
        for task in self._tasks:
            if task.task_id == str(task_id):
                return task
        return None

    def snapshot(self) -> list[dict[str, Any]]:
        return [asdict(t) for t in self._tasks]
