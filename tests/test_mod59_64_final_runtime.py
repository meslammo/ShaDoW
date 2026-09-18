from pathlib import Path
import tempfile
import pytest
from shadow.final_runtime import UnifiedRuntime, DiscoveryEngine, IntegrationEngine, CompanionRegistry, VoiceprintAdapter


def test_unified_runtime_is_online_only():
    r = UnifiedRuntime()
    with pytest.raises(RuntimeError):
        r.execute(lambda: (_ for _ in ()).throw(RuntimeError("down")))
    assert r.mode == "online-unavailable" and r.provider_failures == 1
    assert r.execute(lambda: "online") == "online"
    assert r.mode == "online"


def test_unified_runtime_probe_recovery():
    r = UnifiedRuntime(); r.choose(False)
    assert r.mode == "online-unavailable"
    assert r.probe(lambda: True) is True
    assert r.mode == "online"


def test_discovery_has_core_capabilities():
    ids = {c.id for c in DiscoveryEngine().discover()}
    assert {"android.device", "shadow.12core", "shadow.cloud", "shadow.companions"} <= ids


def test_integration_allowlist_and_rollback():
    with tempfile.TemporaryDirectory() as d:
        root = Path(d); module = root / "shadow" / "demo.py"
        module.parent.mkdir(); module.write_text("x=1", encoding="utf-8")
        e = IntegrationEngine(str(root / ".state"))
        entry = e.integrate(str(module), allowed_prefixes=(str(root / "shadow") + "/",))
        assert entry["status"] == "promoted" and len(entry["sha256"]) == 64
        assert e.rollback_last()["status"] == "rolled_back"


def test_companion_lifecycle_and_permissions():
    r = CompanionRegistry(); r.discover("phone", "android")
    with pytest.raises(PermissionError):
        r.trust("phone", ("status",))
    r.authenticate("phone"); r.trust("phone", ("status",))
    assert r.can("phone", "status") is True
    r.revoke("phone")
    assert r.can("phone", "status") is False
    with pytest.raises(PermissionError):
        r.authenticate("phone")


def test_voiceprint_fails_closed_without_provider():
    v = VoiceprintAdapter(); status = v.status()
    assert status["available"] is False and status["verified"] is False
    with pytest.raises(RuntimeError):
        v.verify(b"audio")
