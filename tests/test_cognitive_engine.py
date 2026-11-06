from shadow.supernice.cognitive import CognitiveEngine


def test_understands_mixed_egyptian_arabic_and_english_creation_request():
    result = CognitiveEngine().analyze("عايز أعمل app بسيط للموبايل")
    assert result.language_hint == "mixed"
    assert result.intent_type == "creation"
    assert result.goal == "عايز أعمل app بسيط للموبايل"


def test_extracts_explicit_safety_constraints():
    result = CognitiveEngine().analyze("راجع المشروع، لا تدمج ولا تنشر")
    assert "no_merge" in result.constraints
    assert "no_publish" in result.constraints


def test_question_is_not_misclassified_as_action():
    result = CognitiveEngine().analyze("ازاي أتعلم البرمجة؟")
    assert result.intent_type == "question"


def test_empty_request_requires_clarification():
    result = CognitiveEngine().analyze("   ")
    assert result.needs_clarification
    assert result.clarification_reason == "request_required"


def test_model_context_is_explicit_about_heuristic_limits():
    payload = CognitiveEngine().build_model_context("Build a website")
    assert payload["schema"] == "shadow.cognitive-request.v1"
    assert "Do not treat this heuristic intent label as ground truth." in payload["planning_instructions"]


def test_model_context_defines_user_visible_task_plan_contract():
    payload = CognitiveEngine().build_model_context("Build a store app")
    contract = payload["plan_contract"]
    assert contract["schema"] == "shadow.task-plan.v1"
    assert "steps" in contract["fields"]
    assert "approval_required" in contract["fields"]
    assert any("prepare a concise task plan" in item for item in payload["planning_instructions"])
