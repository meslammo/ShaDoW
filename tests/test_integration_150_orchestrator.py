from shadow.supernice.orchestrator import Unified150Orchestrator


def test_unified_orchestrator_reaches_every_registered_core(tmp_path):
    orchestrator = Unified150Orchestrator(str(tmp_path))
    report = orchestrator.audit_reachability(confirmed=True)

    assert report["core_count"] == 150
    assert report["reachable"] == 150
    assert report["unreachable"] == 0
    assert report["all_reachable"] is True
    assert [row["core_id"] for row in report["results"]] == [
        f"CORE-{i:03d}" for i in range(1, 151)
    ]


def test_unified_orchestrator_joins_request_to_online_brain_governance_execution_verify_audit(tmp_path):
    orchestrator = Unified150Orchestrator(str(tmp_path))

    def fake_online_brain(request, context):
        assert request == "اكتب خطة للمشروع"
        assert context["selected_core"] == "CORE-024"
        return {"provider": "e2e-fixture", "answer": "تم إعداد الخطة."}

    result = orchestrator.run(
        "اكتب خطة للمشروع",
        online_brain=fake_online_brain,
        confirmed=True,
    )

    assert result.ok is True
    assert result.status == "completed"
    assert result.answer == "تم إعداد الخطة."

    stages = [(event.stage, event.core_id, event.status) for event in result.events]
    assert ("identity", "CORE-001", "executed") in stages
    assert ("reasoning", "CORE-003", "executed") in stages
    assert ("model_route", "CORE-013", "executed") in stages
    assert ("memory", "CORE-025", "executed") in stages
    assert ("online_brain", None, "completed") in stages
    assert ("governance", "CORE-012", "policy_allow") in stages
    assert ("execute", "CORE-024", "executed") in stages
    assert ("verify", "CORE-057", "executed") in stages
    assert ("audit", "CORE-140", "executed") in stages

    assert result.metadata["online_only"] is True
    assert result.metadata["governance"] == "CORE-012"
    assert result.metadata["verification"] == "CORE-057"
    assert result.metadata["audit"] == "CORE-140"


def test_unified_orchestrator_fails_closed_when_online_brain_is_unavailable(tmp_path, monkeypatch):
    monkeypatch.delenv("OPENAI_API_KEY", raising=False)
    orchestrator = Unified150Orchestrator(str(tmp_path))
    result = orchestrator.run("عايز إجابة أونلاين")

    assert result.ok is False
    assert result.status == "online_brain_failed"
    assert any(
        event.stage == "online_brain" and event.status == "failed"
        for event in result.events
    )


def _model_plan(**overrides):
    plan = {
        "schema": "shadow.task-plan.v1",
        "goal": "Inspect the requested repository",
        "assumptions": [],
        "steps": [
            {"id": "inspect", "title": "Inspect repository", "status": "pending", "core_id": "CORE-081", "tool": "github_read", "requires_approval": False}
        ],
        "clarification_question": "",
        "risk": "low",
        "approval_required": False,
        "recommended_core": "CORE-081",
    }
    plan.update(overrides)
    return plan


def test_model_task_plan_controls_core_route_only_after_validation(tmp_path):
    orchestrator = Unified150Orchestrator(str(tmp_path))

    def fake_online_brain(request, context):
        return {"provider": "plan-fixture", "answer": "خطة جاهزة", "task_plan": _model_plan()}

    result = orchestrator.run("راجع مستودع GitHub", online_brain=fake_online_brain, confirmed=True)
    assert result.ok
    assert result.selected_core == "CORE-081"
    assert result.metadata["plan_status"] == "validated"
    assert result.metadata["task_plan"]["steps"][0]["status"] == "pending"


def test_model_task_plan_clarification_stops_before_execution(tmp_path):
    orchestrator = Unified150Orchestrator(str(tmp_path))

    def fake_online_brain(request, context):
        return {"provider": "plan-fixture", "answer": "محتاج توضيح", "task_plan": _model_plan(clarification_question="أي مستودع تقصد؟")}

    result = orchestrator.run("راجع المشروع", online_brain=fake_online_brain)
    assert not result.ok
    assert result.status == "clarification_required"
    assert result.answer == "أي مستودع تقصد؟"
    assert not any(event.stage == "execute" for event in result.events)


def test_model_task_plan_approval_stops_before_sensitive_execution(tmp_path):
    orchestrator = Unified150Orchestrator(str(tmp_path))

    def fake_online_brain(request, context):
        return {"provider": "plan-fixture", "answer": "خطة حذف", "task_plan": _model_plan(
            risk="high", approval_required=True,
            steps=[{"id": "delete", "title": "Delete files", "status": "pending", "core_id": "CORE-081", "tool": "file_write", "requires_approval": True}],
        )}

    result = orchestrator.run("احذف الملفات القديمة", online_brain=fake_online_brain)
    assert not result.ok
    assert result.status == "approval_required"
    assert result.metadata["approval_required"] is True
    assert not any(event.stage == "execute" for event in result.events)


def test_invalid_model_task_plan_fails_closed(tmp_path):
    orchestrator = Unified150Orchestrator(str(tmp_path))

    def fake_online_brain(request, context):
        return {"provider": "plan-fixture", "answer": "خطة غير صالحة", "task_plan": _model_plan(
            recommended_core="CORE-999",
        )}

    result = orchestrator.run("راجع المشروع", online_brain=fake_online_brain)
    assert not result.ok
    assert result.status == "invalid_task_plan"
    assert result.metadata["plan_status"] == "rejected"
