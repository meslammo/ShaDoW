from shadow.core.twelve_core_runtime import CORE_ORDER, TwelveCoreRuntime
from shadow.diagnostics.system import run_diagnostics

def test_all_twelve_cores_are_composed():
    assert len(CORE_ORDER) == 12
    assert CORE_ORDER[0] == "identity"
    assert CORE_ORDER[-1] == "action_security"
    status = TwelveCoreRuntime(".").status()
    assert status["online_brain_required"] is True
    assert status["diagnostics"]["offline_ai_fallback"] is False

def test_master_planner_is_multicore_and_bounded():
    runtime = TwelveCoreRuntime(".")
    plan = runtime.plan("راجع جيت هاب واعمل خطة قبل أي commit", authenticated=False)
    assert plan["route"] == "github"
    cores = [step["core"] for step in plan["steps"]]
    assert "identity" in cores and "reasoning" in cores and "development" in cores
    assert "action-security" in cores and "verification" in cores
    assert plan["online_brain_required"] is True

def test_memory_requires_explicit_non_sensitive_save():
    runtime = TwelveCoreRuntime(".")
    blocked = runtime.memory_save("remember this", explicit=False)
    assert blocked["saved"] is False
    allowed = runtime.memory_save("SHADOW integration test memory", explicit=True)
    assert allowed["saved"] is True
    assert runtime.memory_forget(allowed["id"], explicit=True)["forgotten"] is True

def test_diagnostics_keep_online_only_contract():
    result = run_diagnostics()
    assert result["online_only"] is True
    assert result["offline_ai_fallback"] is False
