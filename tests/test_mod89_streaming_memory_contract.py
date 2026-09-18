from pathlib import Path

def test_mod89_backend_streaming_route_and_memory_recall():
    router = Path("backend/ai-router.mjs").read_text(encoding="utf-8")
    server = Path("backend/server.mjs").read_text(encoding="utf-8")
    client = Path("app/src/main/java/com/shadow/mobile/ShadowCloudClient.java").read_text(encoding="utf-8")
    activity = Path("app/src/main/java/com/shadow/mobile/JarvisMainActivity.java").read_text(encoding="utf-8")
    assert "export async function streamAgent" in router
    assert "async function memoryPrompt" in router
    assert "streamAgent" in server
    assert "/v1/chat/stream" in server
    assert "'Content-Type': 'text/event-stream" in server
    assert "streamChat" in client
    assert "runStreamChat" in activity

def test_mod89_memory_is_explicitly_governed():
    router = Path("backend/ai-router.mjs").read_text(encoding="utf-8")
    assert "memory_save only when the user explicitly asks" in router
    assert "Relevant SHADOW memory" in router
    assert "do not invent or expose sensitive data" in router
