from pathlib import Path

def test_phone_commands_use_local_device_route():
    src = Path("app/src/main/java/com/shadow/mobile/ShadowMasterOrchestrator.java").read_text(encoding="utf-8")
    assert "else if (isLocalDevice(x)) route = Route.LOCAL_DEVICE;" in src
    assert "isSpatial(x) || isCompanion(x)" in src
