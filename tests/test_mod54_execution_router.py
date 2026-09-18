from pathlib import Path


def test_mod54_android_router_source_exists():
    path = Path("app/src/main/java/com/shadow/mobile/ShadowOnlineExecutionRouter.java")
    assert path.exists()
    text = path.read_text(encoding="utf-8")
    assert "requiresLocalExecution" in text
    assert "calculator" in text
    assert "واتساب" in text
    assert "flashlight" in text


def test_mod54_design_is_online_first():
    path = Path("app/src/main/java/com/shadow/mobile/ShadowCloudClient.java")
    text = path.read_text(encoding="utf-8")
    assert "ShadowOnlineExecutionRouter.requiresLocalExecution" in text
    assert "LocalExecutionRequiredException" in text
