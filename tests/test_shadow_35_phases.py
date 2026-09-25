from shadow.supernice.evolution import (
    PHASES_35,
    CompanionRecord,
    DeviceRecord,
    SkillRegistry,
    UnifiedControlPlane,
    WorkflowStep,
)
from shadow.supernice.online_brain import GovernedOnlineBrainAdapter


def test_35_phase_registry_is_complete_and_numbered():
    assert len(PHASES_35) == 35
    assert [p.number for p in PHASES_35] == list(range(1, 36))
    assert PHASES_35[0].name == "Deep Integration"
    assert PHASES_35[-1].name == "Shadow Long-Term Platform"


def test_control_plane_unifies_memory_governance_tools_and_audit(tmp_path):
    control = UnifiedControlPlane(str(tmp_path))
    memory_id = control.remember("Shadow should keep approved project context.", tags=("approved", "project"))
    assert memory_id
    assert control.recall("approved project")
    assert control.governance("calculator").allowed is True
    denied = control.governance("file.write")
    assert denied.allowed is False
    assert denied.requires_confirmation is True

    result = control.execute_tool("calculator", {"expression": "2+3"})
    assert result["success"] is True
    assert result["output"] == "5"

    audit = control.audit_log.read()
    assert any(x["event"] == "tool.executed" for x in audit)


def test_workflow_checkpoint_simulation_and_verification(tmp_path):
    control = UnifiedControlPlane(str(tmp_path))
    result = control.run_workflow([
        WorkflowStep("first", lambda state: "ok"),
        WorkflowStep("second", lambda state: state["first"] + "-next"),
    ], initial={"seed": "x"})
    assert result.ok is True
    assert result.completed == ["first", "second"]
    assert result.output["second"] == "ok-next"

    checkpoint = control.checkpoint("req-1", {"step": 2, "state": "paused"})
    assert control.resume(checkpoint)["step"] == 2

    preview = control.simulate_actions([
        {"capability": "calculator"},
        {"capability": "file.write"},
    ])
    assert preview[0]["allowed"] is True
    assert preview[1]["requires_confirmation"] is True

    verified = control.verifier.verify(True, True)
    assert verified["ok"] is True


def test_knowledge_skills_companions_and_devices(tmp_path):
    control = UnifiedControlPlane(str(tmp_path))
    control.knowledge.upsert_node("p1", "project", "SHADOW")
    control.knowledge.upsert_node("f1", "file", "orchestrator.py")
    control.knowledge.relate("p1", "contains", "f1")
    assert control.knowledge.neighbors("p1") == ["f1"]

    control.skills.register("echo", lambda value: value, version="1.2.0")
    assert control.skills.invoke("echo", value="ok") == "ok"
    assert control.skills.manifest()[0]["version"] == "1.2.0"

    control.companions.register(CompanionRecord("watch-1", "watch", ("voice",), trusted=True))
    assert len(control.companions.trusted()) == 1

    control.devices.register(DeviceRecord("phone-1", "android", ("camera", "voice"), online=True, trusted=True))
    assert [d.device_id for d in control.devices.discover("camera")] == ["phone-1"]


def test_multimodal_envelope_and_diagnostics(tmp_path):
    control = UnifiedControlPlane(str(tmp_path))
    envelope = control.multimodal(
        text="حلل الصورة",
        image_ref="image://1",
        vision={"objects": ["phone"]},
        metadata={"source": "android"},
    )
    context = envelope.as_context()
    assert context["multimodal"] is True
    assert context["vision"]["objects"] == ["phone"]

    diagnostics = control.diagnostics()
    assert diagnostics["core_count"] == 150
    assert diagnostics["core_catalog_complete"] is True
    assert diagnostics["workspace_writable"] is True


def test_governed_online_brain_feeds_real_tool_results_back_to_brain(tmp_path):
    control = UnifiedControlPlane(str(tmp_path))

    class Brain:
        def run(self, request, *, context=None, tools=None, tool_executor=None, confirmed_actions=None, **kwargs):
            assert tools
            assert callable(tool_executor)
            tool_result = tool_executor("calculator", {"expression": "7*6"})
            assert tool_result["success"] is True
            assert tool_result["output"] == "42"
            return {
                "answer": "tool result 42",
                "provider": "stub-online",
                "model": "test",
                "actions": [{"tool": "calculator", "result": "42"}],
            }

    adapter = GovernedOnlineBrainAdapter(control, Brain())
    result = adapter("calculate", {"request_id": "req-2"})
    assert result["answer"] == "tool result 42"
    assert result["actions"][0]["tool"] == "calculator"


def test_platform_status_exposes_35_phase_contract(tmp_path):
    status = UnifiedControlPlane(str(tmp_path)).platform_status()
    assert status["phase_count"] == 35
    assert status["core_count"] == 150
    assert status["online_only_brain"] is True
    assert status["offline_ai_removed"] is True
