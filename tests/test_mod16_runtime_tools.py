import json

from shadow.runtime.runtime import ShadowRuntime
from shadow.ai.orchestrator.orchestrator import AIOrchestrator


def test_runtime_executes_safe_calculator():
    result = ShadowRuntime().handle("calculate 2 + 3 * 4")
    assert result.actions == []  # offline mode has no model tool call
    assert result.verified


def test_runtime_builtin_registry_is_executable():
    runtime = ShadowRuntime()
    out = runtime._execute_tool("calculator", {"expression": "2 + 3 * 4"})
    assert out["success"]
    assert out["output"] == "14"


def test_confirmation_is_fail_closed():
    runtime = ShadowRuntime()
    out = runtime._execute_tool("message.send", {"message": "hello"})
    assert not out["success"]
    assert out["requires_confirmation"]


def test_orchestrator_runs_function_call_round(monkeypatch):
    calls = []
    responses = [
        {"output":[{"type":"function_call","name":"calculator","arguments":json.dumps({"expression":"6*7"}),"call_id":"call_1"}]},
        {"output_text":"42","output":[{"type":"message","content":[{"type":"output_text","text":"42"}]}]},
    ]
    def fake_post(payload, key):
        calls.append(payload)
        return responses.pop(0)
    monkeypatch.setattr(AIOrchestrator, "_post", staticmethod(fake_post))
    orch = AIOrchestrator()
    result = orch._openai("what is 6*7", "x", orch.profiles["default"], {},
                          [{"type":"function","name":"calculator","description":"calc","parameters":{"type":"object"}}],
                          lambda name, args: {"success":True,"output":"42"}, [])
    assert result["answer"] == "42"
    assert result["actions"][0]["tool"] == "calculator"
    assert len(calls) == 2
    assert any(x.get("type") == "function_call_output" for x in calls[1]["input"])
