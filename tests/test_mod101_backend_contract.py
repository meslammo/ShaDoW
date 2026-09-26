from pathlib import Path

AI = Path("backend/ai-router.mjs").read_text(encoding="utf-8")
SERVER = Path("backend/server.mjs").read_text(encoding="utf-8")
REG = Path("backend/tool-registry.mjs").read_text(encoding="utf-8")

def test_mod101_streaming_normalizes_effort_before_pollinations_branch():
    assert "async function streamProviderWithFallback(" in AI
    assert "const normalizedEffort = ['none','minimal','low','medium','high','xhigh','max']" in AI
    assert "reasoningEffort: normalizedEffort" in AI
    assert "const output = await streamPollinations(message, emit, null" in AI

def test_mod101_agent_continuation_exists_end_to_end():
    assert "export async function continueAgent(" in AI
    assert "app.post('/v1/agent/continue'" in SERVER
    assert "continueAgent({" in SERVER

def test_mod101_device_action_is_client_side_only():
    assert "name: 'device_action'" in REG
    assert "kind: 'client_action'" in REG
