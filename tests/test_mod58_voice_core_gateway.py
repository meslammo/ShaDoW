from pathlib import Path


def test_mod58_identity_context_reaches_python_core_without_duplicate_native_gate():
    runtime = Path("shadow/android_runtime.py").read_text(encoding="utf-8")
    bridge = Path("app/src/main/java/com/shadow/mobile/ShadowPythonRuntimeBridge.java").read_text(encoding="utf-8")
    core = Path("app/src/main/java/com/shadow/mobile/ShadowCore.java").read_text(encoding="utf-8")
    phone = Path("app/src/main/java/com/shadow/mobile/ShadowPhoneController.java").read_text(encoding="utf-8")
    assert "authenticated: bool = False" in runtime
    assert '"identity": "master-authenticated"' in runtime
    assert 'callAttr("authorize"' in bridge
    assert "boolean masterAuthenticated" in core
    assert "pythonCore.authorize(request, masterAuthenticated, authorized, source)" not in core
    assert "String gate = governance.authorize(request);" in core
    assert "if (gate != null) return gate;" in core
    assert "identity.isAuthenticated()" in phone
