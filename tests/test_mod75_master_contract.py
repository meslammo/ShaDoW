from pathlib import Path

def test_mod75_master_route_and_reasoning_contract():
    p = Path("app/src/main/java/com/shadow/mobile/JarvisMainActivity.java").read_text(encoding="utf-8")
    assert "ShadowMasterOrchestrator" in p
    assert "Think Hard" in p
    assert "no offline AI fallback" in p
    assert "Deep Think" in p
    assert "reasoningEffort" in p
    assert "REASON_BLACK" in p
    assert "REASON_RED" in p

def test_mod75_event_bus_is_wired_to_lifecycle_bridge():
    bridge = Path("app/src/main/java/com/shadow/mobile/ShadowMasterLifecycleBridge.java").read_text(encoding="utf-8")
    core = Path("app/src/main/java/com/shadow/mobile/ShadowCore.java").read_text(encoding="utf-8")
    assert "ShadowMasterEventBus.Listener" in bridge
    assert "core.governance().record" in bridge
    assert "development.memory().record" in bridge
    assert "companionState" in bridge
    assert "ShadowGovernanceRuntime governance()" in core

def test_mod75_wake_assets_are_pinned():
    workflow = Path(".github/workflows/build-apk.yml").read_text(encoding="utf-8")
    wake = Path("app/src/main/java/com/shadow/mobile/ShadowWakeWordService.kt").read_text(encoding="utf-8")
    asset_map = Path("app/src/main/assets/SHADOW_ASSET_MAP.md").read_text(encoding="utf-8")
    assert "jakes1345/ShadowCypher/827829b09399d6c02ba108607e70aa05bf7485a5/android/assistant/src/main/assets" in workflow
    assert "hey_shadow.onnx" in workflow
    assert "EXTRA_WAKE_ASSISTANT" in wake
    assert 'WakeWordModel("shadow", "hey_shadow.onnx"' in wake
    assert "Hey Shadow" in asset_map
    assert "sole SHADOW wake word" in asset_map
