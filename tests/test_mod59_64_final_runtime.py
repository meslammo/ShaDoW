from pathlib import Path
import tempfile
from shadow.final_runtime import UnifiedRuntime, DiscoveryEngine, IntegrationEngine, CompanionRegistry, VoiceprintAdapter


def test_unified_runtime_falls_back_and_recovers():
    r = UnifiedRuntime()
    assert r.execute(lambda: (_ for _ in ()).throw(RuntimeError("down")), lambda: "offline") == "offline"
    assert r.mode == "offline"
    assert r.execute(lambda: "online", lambda: "offline") == "online"
    assert r.mode == "online"


def test_discovery_has_core_capabilities():
    ids = {c.id for c in DiscoveryEngine().discover()}
    assert {"android.device", "shadow.12core", "shadow.cloud", "shadow.companions"} <= ids


def test_integration_allowlist_and_rollback():
    with tempfile.TemporaryDirectory() as d:
        root = Path(d); module = root / "shadow" / "demo.py"; module.parent.mkdir(); module.write_text("x=1", encoding="utf-8")
        e = IntegrationEngine(str(root / ".state"))
        entry = e.integrate(str(module), allowed_prefixes=(str(root / "shadow") + "/",))
        assert entry["status"] == "promoted" and len(entry["sha256"]) == 64
        assert e.rollback_last()["status"] == "rolled_back"


def test_companion_lifecycle():
    r = CompanionRegistry(); r.discover("phone", "android"); r.authenticate("phone"); r.trust("phone", ("status",)); r.revoke("phone")
    assert r.snapshot()[0]["state"] == "revoked"


def test_voiceprint_fails_closed_without_provider():
    v = VoiceprintAdapter(); status = v.status()
    assert status["available"] is False and status["verified"] is False
