"""SHADOW online session engine.

Keeps the existing 12-Core root + Super Nice 150-Core runtime intact while
providing a single stateful boundary for fast online conversation, streaming,
reconnect recovery, and live-update coordination.
"""
from __future__ import annotations

from dataclasses import asdict, dataclass, field
import json
from pathlib import Path
from time import time
from typing import Any
from uuid import uuid4


@dataclass
class EngineState:
    active_version: str = "0.56.0"
    previous_version: str | None = None
    conversation_id: str = field(default_factory=lambda: str(uuid4()))
    active_turn_id: str | None = None
    connection_state: str = "READY"
    provider_state: str = "UNKNOWN"
    pending_turns: list[str] = field(default_factory=list)
    pending_updates: list[str] = field(default_factory=list)
    last_successful_checkpoint: float | None = None
    last_error: str | None = None


class ShadowEngine:
    """State/orchestration boundary; network transport stays in the online client."""

    def __init__(self, workspace: str = ".", version: str = "0.56.0") -> None:
        self.workspace = Path(workspace)
        self.workspace.mkdir(parents=True, exist_ok=True)
        self.state_path = self.workspace / ".shadow" / "engine_state.json"
        self.state_path.parent.mkdir(parents=True, exist_ok=True)
        self.state = self._load()
        self.state.active_version = version
        self.checkpoint()

    def _load(self) -> EngineState:
        try:
            data = json.loads(self.state_path.read_text(encoding="utf-8"))
            allowed = {f.name for f in EngineState.__dataclass_fields__.values()}
            return EngineState(**{k: v for k, v in data.items() if k in allowed})
        except (OSError, ValueError, TypeError):
            return EngineState()

    def _save(self) -> None:
        tmp = self.state_path.with_suffix(".tmp")
        tmp.write_text(json.dumps(asdict(self.state), ensure_ascii=False, indent=2), encoding="utf-8")
        tmp.replace(self.state_path)

    def checkpoint(self) -> dict[str, Any]:
        self.state.last_successful_checkpoint = time()
        self._save()
        return asdict(self.state)

    def begin_turn(self, request_id: str | None = None) -> str:
        turn_id = request_id or str(uuid4())
        self.state.active_turn_id = turn_id
        if turn_id not in self.state.pending_turns:
            self.state.pending_turns.append(turn_id)
        self.state.connection_state = "CONNECTED"
        self.state.provider_state = "ONLINE"
        self.state.last_error = None
        self._save()
        return turn_id

    def mark_degraded(self, error: str = "online_transport_degraded") -> None:
        self.state.connection_state = "DEGRADED"
        self.state.provider_state = "DEGRADED"
        self.state.last_error = str(error)[:240]
        self._save()

    def mark_reconnecting(self) -> None:
        self.state.connection_state = "RECONNECTING"
        self._save()

    def recover(self) -> dict[str, Any]:
        self.state.connection_state = "RECOVERING"
        self.state.provider_state = "ONLINE"
        self.state.last_error = None
        self._save()
        return asdict(self.state)

    def complete_turn(self, turn_id: str | None = None) -> None:
        tid = turn_id or self.state.active_turn_id
        if tid in self.state.pending_turns:
            self.state.pending_turns.remove(tid)
        if tid == self.state.active_turn_id:
            self.state.active_turn_id = None
        self.state.connection_state = "CONNECTED"
        self.state.provider_state = "ONLINE"
        self.checkpoint()

    def queue_update(self, update_id: str) -> None:
        if update_id not in self.state.pending_updates:
            self.state.pending_updates.append(update_id)
            self._save()

    def finish_update(self, update_id: str, new_version: str) -> None:
        self.state.pending_updates = [x for x in self.state.pending_updates if x != update_id]
        self.state.previous_version = self.state.active_version
        self.state.active_version = new_version
        self._save()

    def rollback_update(self, version: str | None = None) -> None:
        target = version or self.state.previous_version
        if target:
            self.state.active_version, self.state.previous_version = target, self.state.active_version
        self.state.pending_updates.clear()
        self._save()

    def snapshot(self) -> dict[str, Any]:
        return asdict(self.state)
