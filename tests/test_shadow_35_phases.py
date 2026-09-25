from shadow.supernice.evolution import (
    PHASES_35,
    CompanionRecord,
    EvolutionProposal,
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


def test_learning_prediction_companion_spatial_and_backup_controls(tmp_path):
    control = UnifiedControlPlane(str(tmp_path))

    denied = control.learn_correction("remember this", approved=False)
    assert denied["status"] == "approval_required"

    learned = control.learn_correction("approved correction", approved=True)
    assert learned["ok"] is True
    assert control.predict_assistance("approved correction")

    control.companions.register(CompanionRecord("watch-2", "watch", ("voice",), trusted=True))
    delegated = control.delegate_companion("watch-2", "say hello", lambda task: {"task": task})
    assert delegated["ok"] is True
    assert delegated["output"]["task"] == "say hello"

    observed = control.spatial_observe("device-1", "android", relation="near")
    assert observed["ok"] is True
    assert "device-1" in control.knowledge.neighbors("space:default", "near")

    backup = tmp_path / "shadow-backup.json"
    saved = control.backup_state(backup)
    assert saved["ok"] is True
    assert backup.exists()

    progress = control.phase_progress()
    assert progress["phase_count"] == 35
    assert progress["implemented_contracts"] == 35
    assert progress["external_verification_pending"] > 0


def test_rollback_and_controlled_self_improvement(tmp_path):
    control = UnifiedControlPlane(str(tmp_path))
    checkpoint = control.checkpoint("req-rollback", {"step": 4, "status": "before-change"})
    rolled = control.rollback(checkpoint)
    assert rolled["ok"] is True
    assert rolled["status"] == "rolled_back"
    assert rolled["state"]["step"] == 4

    from shadow.supernice.evolution import SelfEvolution
    evolution = SelfEvolution()
    proposal = evolution.propose("safe change", "test", ["x.py"], ["pytest"])
    assert evolution.activation_allowed(proposal, approved_by_master=False, tests_passed=True) is False
    assert evolution.activation_allowed(proposal, approved_by_master=True, tests_passed=False) is False
    assert evolution.activation_allowed(proposal, approved_by_master=True, tests_passed=True) is True
