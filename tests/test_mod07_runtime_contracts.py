from shadow.runtime.capabilities import requires_confirmation
from shadow.runtime.runtime import ShadowRuntime
from shadow.tools.registry import ToolRegistry, ToolSpec

class FakeOrchestrator:
    def run(self, request, *, context=None, tools=None):
        return {"answer": f"handled: {request}"}

def test_runtime_pipeline_returns_verified_result():
    result = ShadowRuntime(orchestrator=FakeOrchestrator()).handle("hello")
    assert result.verified is True
    assert result.confidence > 0
    assert "check permissions" in result.plan

def test_dangerous_capabilities_require_confirmation():
    assert requires_confirmation("device.control") is True
    assert requires_confirmation("chat") is False

def test_tool_registry_fails_closed():
    registry = ToolRegistry([ToolSpec("delete", "delete data", lambda: "ok")])
    try:
        registry.invoke("delete")
        assert False
    except PermissionError:
        pass
    assert registry.invoke("delete", confirmed=True) == "ok"
