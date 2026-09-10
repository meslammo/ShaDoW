import json

from shadow.ai.orchestrator.orchestrator import AIOrchestrator
from shadow.runtime.runtime import ShadowRuntime
from shadow.security.permissions import PermissionManager
from shadow.tools.builtins import build_builtin_registry, calculate


def test_calculator_is_safe_and_permission_aligned():
    assert calculate("(2 + 3) * 4") == "20"
    assert PermissionManager().decide("calculator").allowed
    assert PermissionManager().decide("time.now").allowed


def test_runtime_registers_and_executes_safe_builtin():
    runtime = ShadowRuntime()
    result = runtime.executor.execute("calculator", expression="6*7")
    assert result.success
    assert result.output == "42"


def test_runtime_blocks_confirmation_capability():
    runtime = ShadowRuntime()
    runtime.register_action("message.send", lambda text: f"sent:{text}")
    result = runtime.executor.execute("message.send", text="hello")
    assert not result.success
    assert result.requires_confirmation
    result = runtime.executor.execute("message.send", confirmed=True, text="hello")
    assert result.success


def test_openai_tool_round_trip_executes_function(monkeypatch):
    calls = []
    responses = [
        {"output": [{"type": "function_call", "name": "calculator", "call_id": "call_1", "arguments": json.dumps({"expression": "8*9"})}]},
        {"output_text": "The answer is 72.", "output": []},
    ]

    def fake_post(payload, key):
        calls.append(payload)
        return responses.pop(0)

    monkeypatch.setattr(AIOrchestrator, "_post", staticmethod(fake_post))
    orch = AIOrchestrator()
    executed = []

    def execute(name, args):
        executed.append((name, args))
        return {"success": True, "output": "72"}

    result = orch.run(
        "calculate 8*9",
        tools=[{"type": "function", "name": "calculator", "description": "calc", "parameters": {"type": "object"}}],
        tool_executor=execute,
    )
    assert result["answer"] == "The answer is 72."
    assert executed[0][0] == "calculator"
    assert len(calls) == 2
    assert any(x.get("type") == "function_call_output" for x in calls[1]["input"])
