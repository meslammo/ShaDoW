from shadow.security.cyber_defense import (
    audit_workspace, create_review_sandbox, parse_dependencies, patch_plan,
    regression_plan, scan_text,
)


def test_secret_detection_redacts_value():
    findings = scan_text("settings.py", "API_KEY = 'super-secret-value-123'")
    assert findings
    assert findings[0]["category"] == "secret_exposure"
    assert "super-secret-value-123" not in str(findings)


def test_unsafe_python_and_api_settings_are_flagged():
    findings = scan_text("app.py", "eval(user_input)\nCORS = '*'\n")
    assert {x["category"] for x in findings} >= {"unsafe_code", "api_configuration"}


def test_requirements_exact_pins_are_parsed():
    deps = parse_dependencies("requirements.txt", "requests==2.31.0\n# comment\nflask>=2\n")
    assert deps == [{"name": "requests", "version": "2.31.0", "ecosystem": "PyPI"}]


def test_audit_is_bounded_and_non_executing(tmp_path):
    (tmp_path / "sample.py").write_text("eval('1+1')", encoding="utf-8")
    result = audit_workspace(tmp_path)
    assert result["status"] == "completed"
    assert result["files_scanned"] == 1
    assert result["finding_count"] >= 1
    assert "no code execution" in result["limitations"]


def test_path_traversal_is_rejected(tmp_path):
    (tmp_path / "inside.py").write_text("safe = True", encoding="utf-8")
    result = audit_workspace(tmp_path, paths=["../outside.py"])
    assert result["collection_errors"][0]["reason"] == "path_outside_workspace"


def test_review_sandbox_does_not_execute_or_claim_os_isolation(tmp_path):
    result = create_review_sandbox(tmp_path, {"src/main.py": "print('not run')", "../escape.py": "bad"})
    assert result["files_copied"] == ["src/main.py"]
    assert result["execution_performed"] is False
    assert "no process or network isolation" in result["limitations"]


def test_patch_and_regression_are_proposals_only():
    findings = [{"id": "x", "category": "secret_exposure", "path": "a.py", "line": 1}]
    patch = patch_plan(findings)
    regression = regression_plan(findings)
    assert patch["changes_applied"] is False
    assert patch["actions"][0]["approval_required"] is True
    assert regression["executed"] is False
