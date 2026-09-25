from shadow.supernice.evolution import (
    PHASES_35,
    EvolutionProposal,
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


def test_knowledge_skills_are_software_only(tmp_path):
    control = UnifiedControlPlane(str(tmp_path))
    control.knowledge.upsert_node("p1", "project", "SHADOW")
    control.knowledge.upsert_node("f1", "file", "orchestrator.py")
    control.knowledge.relate("p1", "contains", "f1")
    assert control.knowledge.neighbors("p1") == ["f1"]

    control.skills.register("echo", lambda value: value, version="1.2.0")
    assert control.skills.invoke("echo", value="ok") == "ok"
    assert control.skills.manifest()[0]["version"] == "1.2.0"
    assert control.external_device_control is False
    assert control.delegate_companion("legacy-device", "test")["status"] == "disabled_by_scope"
    assert control.spatial_observe("device-1", "android")["status"] == "disabled_by_scope"


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
    assert status["external_device_control"] is False
    assert status["capabilities"]["agent_capability_layer"] is True
    assert status["capabilities"]["world_task_context_model"] is True


def test_learning_knowledge_and_backup_controls(tmp_path):
    control = UnifiedControlPlane(str(tmp_path))

    denied = control.learn_correction("remember this", approved=False)
    assert denied["status"] == "approval_required"

    learned = control.learn_correction("approved correction", approved=True)
    assert learned["ok"] is True
    assert control.predict_assistance("approved correction")

    control.knowledge.upsert_node("p1", "project", "SHADOW")
    control.knowledge.upsert_node("t1", "task", "finish AI-only scope")
    control.knowledge.relate("p1", "contains", "t1")
    assert control.knowledge.neighbors("p1") == ["t1"]

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


def test_long_running_tasks_automation_patterns_simulation_and_continuous_verification(tmp_path):
    control = UnifiedControlPlane(str(tmp_path))
    started = control.start_long_task("job-1", {"step": 1})
    assert started["status"] == "running"
    paused = control.pause_long_task("job-1", {"step": 2})
    assert paused["status"] == "paused"
    resumed = control.resume_long_task("job-1")
    assert resumed["state"]["step"] == 2
    completed = control.complete_long_task("job-1")
    assert completed["status"] == "completed"

    control.register_automation(
        "demo",
        "trigger:test",
        [WorkflowStep("a", lambda state: 1), WorkflowStep("b", lambda state: state["a"] + 1)],
    )
    automation_results = control.run_automation("trigger:test")
    assert automation_results[0].ok is True
    assert automation_results[0].output["b"] == 2

    assert control.observe_pattern("morning workflow") == 1
    assert control.observe_pattern("morning workflow") == 2
    assert control.pattern_suggestions()[0]["pattern"] == "morning workflow"

    impact = control.simulate_impact([
        {"capability": "calculator"},
        {"capability": "file.write"},
    ])
    assert impact["side_effects_executed"] is False
    assert impact["confirmation_count"] == 1

    checked = control.verifier.loop(True, lambda _round: True, max_rounds=3)
    assert checked["ok"] is True
    assert checked["rounds"][0]["round"] == 1


def test_35_phase_roadmap_is_device_independent():
    names = [p.name.lower() for p in PHASES_35]
    joined = " ".join(names)
    assert "device + spatial intelligence" not in joined
    assert "companion system" not in joined
    assert "cross-device shadow" not in joined
    assert "federated device intelligence" not in joined
    assert "spatial world model" not in joined


def test_production_backup_restore_round_trip(tmp_path):
    source = UnifiedControlPlane(str(tmp_path / "source"))
    memory_id = source.remember("approved production restore marker", tags=("backup-e2e",), source="test")
    assert memory_id

    backup = tmp_path / "shadow-backup.json"
    saved = source.backup_state(backup)
    assert saved["ok"] is True
    assert backup.exists()

    restored = UnifiedControlPlane(str(tmp_path / "restored"))
    result = restored.restore_memory_from_backup(backup)
    assert result["ok"] is True
    assert result["restored_memory_items"] >= 1
    assert restored.recall("production restore marker")
    assert any(x["event"] == "backup.restored" for x in restored.audit_log.read())
