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
