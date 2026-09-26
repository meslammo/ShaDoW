from pathlib import Path


def test_fix01_master_authentication_is_not_a_local_execution_gate():
    core = Path("app/src/main/java/com/shadow/mobile/ShadowCore.java").read_text(encoding="utf-8")
    assert "FIX-01: Master authentication is no longer a runtime gate." in core
    assert "pythonCore.authorize(request, masterAuthenticated, authorized, source)" not in core
    assert "Identity: OWNER SESSION" in core
    assert "PASSPHRASE DISABLED" in core


def test_fix01_native_governance_remains_the_only_local_gate():
    core = Path("app/src/main/java/com/shadow/mobile/ShadowCore.java").read_text(encoding="utf-8")
    assert "String gate = governance.authorize(request);" in core
    assert "if (gate != null) return gate;" in core
