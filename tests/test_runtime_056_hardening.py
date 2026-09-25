from pathlib import Path

from shadow.core.engine import ShadowEngine
from shadow.core.live_update import LiveUpdateManager


def test_engine_persists_and_recovers(tmp_path):
    engine = ShadowEngine(tmp_path)
    turn = engine.begin_turn("turn-1")
    assert turn == "turn-1"
    engine.mark_degraded("test")
    assert engine.snapshot()["connection_state"] == "DEGRADED"
    engine.recover()
    assert engine.snapshot()["connection_state"] == "RECOVERING"
    engine.complete_turn(turn)
    restored = ShadowEngine(tmp_path)
    state = restored.snapshot()
    assert state["active_turn_id"] is None
    assert state["connection_state"] == "CONNECTED"
    assert state["provider_state"] == "ONLINE"


def test_live_update_validates_before_activation(tmp_path):
    manager = LiveUpdateManager(tmp_path)
    staged = manager.stage("0.56.1", {"shadow/core/temporary_probe.py": "VALUE = 150\n"})
    result = manager.activate(staged)
    assert result.ok
    assert result.status == "activated"
    assert (Path(tmp_path) / "shadow/core/temporary_probe.py").exists()


def test_live_update_rejects_unsafe_path(tmp_path):
    manager = LiveUpdateManager(tmp_path)
    try:
        manager.stage("0.56.1", {"../escape.py": "NOPE"})
    except ValueError as exc:
        assert "outside_allowed_roots" in str(exc)
    else:
        raise AssertionError("unsafe path was accepted")