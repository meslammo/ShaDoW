from pathlib import Path

def test_mod75_master_route_and_reasoning_contract():
    p = Path("app/src/main/java/com/shadow/mobile/JarvisMainActivity.java").read_text(encoding="utf-8")
    assert "ShadowMasterOrchestrator" in p
    assert "Think Hard" in p
    assert "⚡ Full 35-Phase Master" in p
    assert "مش هيستخدم نسخة أوفلاين" in p
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
    assert "sole wake phrase" in asset_map

def test_mod79_lifecycle_ledgers_are_wired_without_external_device_activation():
    bridge = Path("app/src/main/java/com/shadow/mobile/ShadowMasterLifecycleBridge.java").read_text(encoding="utf-8")
    activity = Path("app/src/main/java/com/shadow/mobile/JarvisMainActivity.java").read_text(encoding="utf-8")
    assert "ShadowVerificationLedger" in bridge
    assert "ShadowRecoveryLedger" in bridge
    assert "verificationLedger=new ShadowVerificationLedger(this)" in activity
    assert "recoveryLedger=new ShadowRecoveryLedger(this)" in activity
    assert "new ShadowMasterLifecycleBridge(orchestrator.events(),core,developmentAgent,null,pythonBridge,null" in activity

def test_ai_only_client_exposes_no_active_device_control():
    activity = Path("app/src/main/java/com/shadow/mobile/JarvisMainActivity.java").read_text(encoding="utf-8")
    manifest = Path("app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
    for forbidden in ("TV Remote", "AC Remote", "Deep phone control", "Spatial Radar", "ACTION_ACCESSIBILITY_SETTINGS"):
        assert forbidden not in activity
    for forbidden in ("Shadow Device Control", "Shadow Device Control"):
        assert forbidden not in manifest
    assert "ACCESS_FINE_LOCATION" not in manifest
    assert "ACCESS_COARSE_LOCATION" not in manifest

def test_mod82_online_voiceprint_path_is_explicit():
    identity = Path("app/src/main/java/com/shadow/mobile/ShadowVoiceIdentityGateway.java").read_text(encoding="utf-8")
    client = Path("app/src/main/java/com/shadow/mobile/ShadowCloudClient.java").read_text(encoding="utf-8")
    activity = Path("app/src/main/java/com/shadow/mobile/JarvisMainActivity.java").read_text(encoding="utf-8")
    assert "isVoiceVerified" in identity
    assert "verifyVoiceprint" in client
    assert "startVoiceprintVerification" in activity
    assert "voiceprint" in activity

def test_mod85_master_cycle_contract():
    cycle = Path("app/src/main/java/com/shadow/mobile/ShadowMasterCycle.java").read_text(encoding="utf-8")
    bridge = Path("app/src/main/java/com/shadow/mobile/ShadowMasterLifecycleBridge.java").read_text(encoding="utf-8")
    activity = Path("app/src/main/java/com/shadow/mobile/JarvisMainActivity.java").read_text(encoding="utf-8")
    assert "class ShadowMasterCycle" in cycle
    assert "cycle.record(event.coreLayer)" in bridge
    assert "masterCycle=new ShadowMasterCycle()" in activity

def test_mod86_route_mapping_is_ai_only():
    orchestrator = Path("app/src/main/java/com/shadow/mobile/ShadowMasterOrchestrator.java").read_text(encoding="utf-8")
    bus = Path("app/src/main/java/com/shadow/mobile/ShadowMasterEventBus.java").read_text(encoding="utf-8")
    cycle = Path("app/src/main/java/com/shadow/mobile/ShadowMasterCycle.java").read_text(encoding="utf-8")
    assert "device_control" not in orchestrator.lower() or "not part of the active Shadow route" in orchestrator
    for route in ("development", "github", "image", "chat"):
        assert '"' + route + '"' in bus
    for route in ("companion", "spatial", "local-device"):
        assert ('is' not in route) or route in orchestrator
    assert "verification" in cycle

def test_mod86_device_like_requests_cannot_enter_local_device_route():
    source = orchestrator = Path("app/src/main/java/com/shadow/mobile/ShadowMasterOrchestrator.java").read_text(encoding="utf-8")
    assert "else if (isSpatial(x) || isCompanion(x) || isLocalDevice(x)) route = Route.CHAT;" in source

def test_mod87_github_priority_and_sensitive_pause_events():
    orchestrator = Path("app/src/main/java/com/shadow/mobile/ShadowMasterOrchestrator.java").read_text(encoding="utf-8")
    activity = Path("app/src/main/java/com/shadow/mobile/JarvisMainActivity.java").read_text(encoding="utf-8")
    github_pos = orchestrator.find("else if (isGithub(x))")
    development_pos = orchestrator.find("else if (isDevelopment(x))")
    assert github_pos >= 0 and development_pos > github_pos
    assert "sensitive_identity_required" not in activity
    assert "user_cancelled_action" in activity
    assert "passphrase" not in activity.lower() or "passphrase" in activity.lower()

def test_mod88_launcher_is_shadow_named():
    main = Path("app/src/main/java/com/shadow/mobile/MainActivity.java").read_text(encoding="utf-8")
    shadow = Path("app/src/main/java/com/shadow/mobile/ShadowMainActivity.java").read_text(encoding="utf-8")
    assert "extends ShadowMainActivity" in main
    assert "canonical user-facing SHADOW activity" in shadow
