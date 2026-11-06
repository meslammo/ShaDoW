from shadow.supernice.planning import PLAN_SCHEMA, extract_task_plan, validate_task_plan


def valid_plan(**overrides):
    value = {
        "schema": PLAN_SCHEMA,
        "goal": "Prepare a safe project audit",
        "assumptions": [],
        "steps": [
            {"id": "inspect", "title": "Inspect the repository", "status": "pending", "core_id": "CORE-081", "tool": "github_read", "requires_approval": False}
        ],
        "clarification_question": "",
        "risk": "low",
        "approval_required": False,
        "recommended_core": "CORE-081",
    }
    value.update(overrides)
    return value


def test_valid_model_plan_is_normalized_and_marked_unexecuted():
    result = validate_task_plan(valid_plan(), "راجع المستودع")
    assert result.ok
    assert result.plan["source"] == "model_validated"
    assert result.plan["steps"][0]["status"] == "pending"
    assert result.plan["recommended_core"] == "CORE-081"


def test_unknown_core_and_tool_are_rejected():
    plan = valid_plan(steps=[{"id": "x", "title": "Run", "status": "pending", "core_id": "CORE-999", "tool": "shell_exec"}])
    result = validate_task_plan(plan)
    assert not result.ok
    assert "step_0_unknown_core" in result.errors
    assert "step_0_unknown_tool" in result.errors


def test_model_cannot_claim_step_completed():
    plan = valid_plan(steps=[{"id": "x", "title": "Already done", "status": "completed"}])
    result = validate_task_plan(plan)
    assert not result.ok
    assert "step_0_forged_status" in result.errors


def test_high_risk_request_forces_approval():
    result = validate_task_plan(valid_plan(), "احذف الملفات القديمة")
    assert result.ok
    assert result.plan["approval_required"] is True
    assert result.plan["risk"] == "high"


def test_invalid_plan_shape_fails_closed():
    result = validate_task_plan({"schema": PLAN_SCHEMA, "goal": "x", "steps": []})
    assert not result.ok


def test_extracts_only_explicit_plan_envelope():
    raw = '<shadow_task_plan>{"schema":"shadow.task-plan.v1","goal":"x"}</shadow_task_plan>'
    assert extract_task_plan(raw)["goal"] == "x"
    assert extract_task_plan("ordinary text with no plan") is None
