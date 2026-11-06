import os
from pathlib import Path

import pytest

from shadow.security.docker_sandbox import run_python_sandbox


def test_sandbox_requires_explicit_confirmation(tmp_path):
    result = run_python_sandbox(tmp_path, "sample.py", confirmed=False)
    assert result["status"] == "confirmation_required"
    assert result["executed"] is False


def test_sandbox_requires_an_immutable_local_image_id(tmp_path, monkeypatch):
    (tmp_path / "sample.py").write_text("print('must not run')", encoding="utf-8")
    monkeypatch.delenv("SHADOW_SANDBOX_IMAGE", raising=False)
    result = run_python_sandbox(tmp_path, "sample.py", confirmed=True)
    assert result["status"] == "pinned_image_required"
    assert result["executed"] is False


def test_sandbox_rejects_path_traversal_and_symlinks(tmp_path):
    outside = tmp_path.parent / "outside_shadow_script.py"
    outside.write_text("print('outside')", encoding="utf-8")
    result = run_python_sandbox(tmp_path, "../outside_shadow_script.py", confirmed=True)
    assert result["status"] == "invalid_script_path"

    inside_link = tmp_path / "linked.py"
    try:
        inside_link.symlink_to(outside)
    except (OSError, NotImplementedError):
        pytest.skip("symlinks are unavailable on this platform")
    result = run_python_sandbox(tmp_path, "linked.py", confirmed=True)
    assert result["status"] == "symlink_not_allowed"


def test_core_138_fails_closed_and_never_runs_on_host(tmp_path, monkeypatch):
    from shadow.supernice.contracts import CoreRequest
    from shadow.supernice.runtime import CoreRuntime

    (tmp_path / "sample.py").write_text("print('must stay isolated')", encoding="utf-8")
    monkeypatch.delenv("SHADOW_SANDBOX_IMAGE", raising=False)
    runtime = CoreRuntime(workspace=str(tmp_path))
    result = runtime.execute(
        CoreRequest(
            "CORE-138",
            "execute sample in sandbox",
            context={"operation": "execute_python", "script_path": "sample.py"},
            confirmed=True,
        )
    )
    assert result.ok is False
    assert result.status == "pinned_image_required"
    assert result.result["executed"] is False


@pytest.mark.skipif(
    not os.environ.get("SHADOW_SANDBOX_IMAGE"),
    reason="requires the pinned Docker sandbox image prepared by CI",
)
def test_docker_sandbox_enforces_runtime_isolation(tmp_path):
    script = tmp_path / "probe.py"
    script.write_text(
        "import os, socket\n"
        "print('uid=' + str(os.getuid()))\n"
        "try:\n"
        "    open('/workspace/blocked.txt', 'w').write('no')\n"
        "except OSError:\n"
        "    print('workspace_read_only')\n"
        "else:\n"
        "    raise SystemExit('workspace_write_allowed')\n"
        "try:\n"
        "    socket.create_connection(('1.1.1.1', 53), timeout=1)\n"
        "except OSError:\n"
        "    print('network_blocked')\n"
        "else:\n"
        "    raise SystemExit('network_available')\n",
        encoding="utf-8",
    )
    result = run_python_sandbox(tmp_path, "probe.py", timeout_seconds=10, confirmed=True)
    assert result["ok"] is True, result
    assert result["executed"] is True
    assert "uid=65534" in result["stdout"]
    assert "workspace_read_only" in result["stdout"]
    assert "network_blocked" in result["stdout"]
    assert result["isolation"]["network"] == "none"
    assert result["isolation"]["root_filesystem"] == "read_only"
    assert result["isolation"]["capabilities"] == "all_dropped"
    assert result["isolation"]["no_new_privileges"] is True
