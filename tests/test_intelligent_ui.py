from shadow.supernice.contracts import CoreRequest
from shadow.supernice.intelligent_ui import build_intelligent_response
from shadow.supernice.runtime import CoreRuntime


def test_intelligent_ui_returns_text_only_when_no_safe_components_exist():
    result = build_intelligent_response("Answer", [{"type": "script", "source": "alert(1)"}])
    assert result["schema"] == "shadow.intelligent-ui.v1"
    assert result["mode"] == "text"
    assert result["components"] == []
    assert result["execution_policy"]["arbitrary_html_or_javascript"] is False


def test_intelligent_ui_keeps_structured_components_and_requires_action_confirmation():
    result = build_intelligent_response(
        "Here is the result",
        [
            {"type": "card", "title": "Summary", "body": "Three findings"},
            {"type": "table", "columns": ["Name", "Score"], "rows": [["A", 9], ["B", 7]]},
            {"type": "chart", "chart_type": "bar", "data": [{"label": "A", "value": 9}]},
            {"type": "action", "action_id": "open_report", "label": "Open report"},
        ],
        mode="interactive",
    )
    assert result["mode"] == "interactive"
    assert [x["type"] for x in result["components"]] == ["card", "table", "chart", "action"]
    action = result["components"][-1]
    assert action["requires_confirmation"] is True


def test_intelligent_ui_rejects_unsafe_urls_and_script_diagrams():
    result = build_intelligent_response(
        "Safe answer",
        [
            {"type": "image", "url": "javascript:alert(1)", "alt": "bad"},
            {"type": "diagram", "source": "<script>alert(1)</script>"},
            {"type": "image", "url": "https://example.test/image.png", "alt": "safe"},
        ],
    )
    assert len(result["components"]) == 1
    assert result["components"][0]["url"].startswith("https://")


def test_core_024_exposes_intelligent_ui_schema(tmp_path):
    result = CoreRuntime(workspace=str(tmp_path)).execute(
        CoreRequest(
            "CORE-024",
            "compose response",
            context={
                "answer": "Hello",
                "ui_mode": "visual",
                "ui_components": [{"type": "checklist", "items": ["one", "two"]}],
            },
        )
    )
    assert result.ok is True
    assert result.result["schema"] == "shadow.intelligent-ui.v1"
    assert result.result["components"][0]["type"] == "checklist"
    assert result.metadata["client_rendering_required"] is True
