"""Fail-closed Docker sandbox for bounded execution of untrusted Python scripts.

The Docker daemon and host kernel remain trusted components. This is a hardened
container boundary, not a VM or a guarantee against host-kernel vulnerabilities.
"""
from __future__ import annotations

import os
import platform
import re
import selectors
import shutil
import subprocess
import tempfile
import time
from pathlib import Path
from typing import Any, Sequence

_IMAGE_ID_RE = re.compile(r"^sha256:[0-9a-f]{64}$")
_SECRET_OUTPUT_RE = re.compile(
    r"(?i)((?:api[_-]?key|access[_-]?token|client[_-]?secret|password|passwd|"
    r"private[_-]?key|authorization)\s*[:=]\s*)[^\s'\",;]{6,}"
)
MAX_SCRIPT_BYTES = 512_000
MAX_OUTPUT_BYTES = 64_000
MAX_TIMEOUT_SECONDS = 60
MAX_ARGS = 32


def _result(status: str, **extra: Any) -> dict[str, Any]:
    return {"ok": False, "status": status, "executed": False, **extra}


def _redact(value: str) -> str:
    return _SECRET_OUTPUT_RE.sub(r"\1[REDACTED]", value)


def _load_script(workspace: str | Path, script_path: str) -> tuple[bytes | None, str | None]:
    root = Path(workspace).resolve()
    rel = Path(str(script_path))
    if rel.is_absolute() or not rel.parts or any(part in {"", ".", ".."} for part in rel.parts):
        return None, "invalid_script_path"
    candidate = root
    for part in rel.parts:
        candidate = candidate / part
        try:
            if candidate.is_symlink():
                return None, "symlink_not_allowed"
        except OSError:
            return None, "script_unavailable"
    try:
        resolved = candidate.resolve(strict=True)
        resolved.relative_to(root)
        if not resolved.is_file():
            return None, "script_not_regular_file"
        data = resolved.read_bytes()
    except (OSError, ValueError):
        return None, "script_unavailable"
    if len(data) > MAX_SCRIPT_BYTES:
        return None, "script_too_large"
    return data, None


def _bounded_capture(argv: Sequence[str], timeout: float = 4.0) -> subprocess.CompletedProcess[str] | None:
    try:
        return subprocess.run(
            list(argv), stdin=subprocess.DEVNULL, stdout=subprocess.PIPE,
            stderr=subprocess.PIPE, text=True, timeout=timeout, check=False,
            close_fds=True,
        )
    except (OSError, subprocess.TimeoutExpired):
        return None


def _read_process(process: subprocess.Popen[bytes], timeout: int, docker: str, name: str) -> tuple[int, bytes, bytes, bool, bool]:
    """Drain pipes without allowing a target to exhaust host memory with output."""
    selector = selectors.DefaultSelector()
    streams: dict[str, bytearray] = {"stdout": bytearray(), "stderr": bytearray()}
    for label, stream in (("stdout", process.stdout), ("stderr", process.stderr)):
        if stream is not None:
            os.set_blocking(stream.fileno(), False)
            selector.register(stream, selectors.EVENT_READ, label)

    deadline = time.monotonic() + timeout
    timed_out = False
    output_limited = False
    total = 0
    try:
        while selector.get_map():
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                timed_out = True
                break
            for key, _ in selector.select(min(remaining, 0.1)):
                try:
                    chunk = os.read(key.fd, 4096)
                except BlockingIOError:
                    continue
                if not chunk:
                    selector.unregister(key.fileobj)
                    continue
                total += len(chunk)
                if total > MAX_OUTPUT_BYTES:
                    output_limited = True
                    break
                streams[key.data].extend(chunk)
            if output_limited:
                break
    finally:
        selector.close()

    if timed_out or output_limited:
        try:
            process.kill()
        except OSError:
            pass
        # Killing the Docker CLI alone can orphan the container. Kill by its
        # generated name, then remove it; neither action runs target code.
        _bounded_capture([docker, "kill", name], timeout=3)
        _bounded_capture([docker, "rm", "-f", name], timeout=3)

    try:
        return_code = process.wait(timeout=3)
    except subprocess.TimeoutExpired:
        process.kill()
        return_code = process.wait(timeout=3)
    for stream in (process.stdout, process.stderr):
        if stream is not None:
            try:
                stream.close()
            except OSError:
                pass
    return return_code, bytes(streams["stdout"]), bytes(streams["stderr"]), timed_out, output_limited


def run_python_sandbox(
    workspace: str | Path,
    script_path: str,
    *,
    args: Sequence[str] | None = None,
    timeout_seconds: int = 20,
    confirmed: bool = False,
) -> dict[str, Any]:
    """Run one Python script in a read-only, no-network, resource-limited container.

    The image must already exist locally and be selected by its immutable Docker
    image ID (sha256:...). The function never pulls images or falls back to host
    execution. User confirmation is mandatory.
    """
    if not confirmed:
        return _result("confirmation_required", approval_required=True)
    if platform.system() not in {"Linux", "Darwin"}:
        return _result("unsupported_host_platform")
    if not isinstance(timeout_seconds, int) or not 1 <= timeout_seconds <= MAX_TIMEOUT_SECONDS:
        return _result("invalid_timeout")
    safe_args = list(args or [])
    if len(safe_args) > MAX_ARGS or any(not isinstance(x, str) or len(x) > 512 or "\x00" in x for x in safe_args):
        return _result("invalid_arguments")

    image_id = os.environ.get("SHADOW_SANDBOX_IMAGE", "").strip()
    if not _IMAGE_ID_RE.fullmatch(image_id):
        return _result("pinned_image_required", required_format="sha256:<64 lowercase hex characters>")

    script, error = _load_script(workspace, script_path)
    if error:
        return _result(error)

    docker = shutil.which("docker")
    if not docker:
        return _result("docker_unavailable", isolation_required=True)

    daemon = _bounded_capture([docker, "info", "--format", "{{.ServerVersion}}"])
    if daemon is None or daemon.returncode != 0:
        return _result("docker_engine_unavailable", isolation_required=True)

    image = _bounded_capture([docker, "image", "inspect", "--format", "{{.Id}}", image_id])
    if image is None or image.returncode != 0 or image.stdout.strip() != image_id:
        return _result("pinned_image_not_available_locally", image_id=image_id)

    started = time.monotonic()
    with tempfile.TemporaryDirectory(prefix="shadow-sandbox-") as temp_dir:
        stage = Path(temp_dir)
        staged_script = stage / "entry.py"
        try:
            staged_script.write_bytes(script or b"")
            staged_script.chmod(0o444)
            stage.chmod(0o755)
        except OSError:
            return _result("sandbox_staging_failed")

        container_name = "shadow-sandbox-" + os.urandom(8).hex()
        command = [
            docker, "run", "--rm", "--pull=never", "--name", container_name,
            "--network=none", "--read-only", "--cap-drop=ALL",
            "--security-opt=no-new-privileges:true", "--pids-limit=32",
            "--memory=128m", "--memory-swap=128m", "--cpus=0.50",
            "--ulimit=nofile=64:64", "--user=65534:65534",
            "--tmpfs=/tmp:rw,noexec,nosuid,nodev,size=16m,mode=1777",
            "--mount", f"type=bind,src={stage},dst=/workspace,readonly",
            "--workdir=/workspace", "--env=HOME=/tmp",
            "--env=PYTHONDONTWRITEBYTECODE=1", "--env=PYTHONNOUSERSITE=1",
            "--entrypoint=python3", image_id, "-I", "/workspace/entry.py", *safe_args,
        ]
        try:
            process = subprocess.Popen(
                command, stdin=subprocess.DEVNULL, stdout=subprocess.PIPE,
                stderr=subprocess.PIPE, close_fds=True,
            )
        except OSError:
            _bounded_capture([docker, "rm", "-f", container_name], timeout=3)
            return _result("docker_execution_unavailable")

        code, stdout, stderr, timed_out, output_limited = _read_process(
            process, timeout_seconds, docker, container_name
        )
        elapsed = round(time.monotonic() - started, 3)
        result = {
            "ok": code == 0 and not timed_out and not output_limited,
            "status": "timeout" if timed_out else "output_limit" if output_limited else "completed" if code == 0 else "execution_failed",
            "executed": True,
            "exit_code": code,
            "stdout": _redact(stdout.decode("utf-8", errors="replace"))[:32_000],
            "stderr": _redact(stderr.decode("utf-8", errors="replace"))[:32_000],
            "duration_seconds": elapsed,
            "output_limited": output_limited,
            "isolation": {
                "backend": "docker",
                "image_id": image_id,
                "network": "none",
                "root_filesystem": "read_only",
                "workspace_mount": "read_only",
                "user": "65534:65534",
                "capabilities": "all_dropped",
                "no_new_privileges": True,
                "pids_limit": 32,
                "memory_limit": "128m",
                "cpu_limit": "0.50",
                "default_runtime_seccomp": True,
            },
            "limitations": [
                "container isolation shares the host kernel; it is not a VM",
                "the Docker daemon and host kernel remain trusted",
                "no packages are installed and no network is available to the target",
            ],
        }
        return result
