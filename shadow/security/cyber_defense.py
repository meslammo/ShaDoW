"""Defensive, non-executing security analysis for SHADOW.

This module performs bounded static inspection only. It never executes target
code, installs packages, applies patches, or contacts a target system.
"""
from __future__ import annotations

import ast
import hashlib
import json
import os
import re
import tempfile
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any

MAX_FILES = 400
MAX_FILE_BYTES = 512_000
MAX_TOTAL_BYTES = 8_000_000
SKIP_DIRS = {".git", ".venv", "venv", "node_modules", "__pycache__", ".gradle", "build", "dist"}
SECRET_RE = re.compile(
    r"(?i)(?:api[_-]?key|access[_-]?token|client[_-]?secret|password|passwd|"
    r"private[_-]?key|authorization)\s*[:=]\s*['\"]?([^\s'\",;]{8,})"
)
UNSAFE_PY_RE = re.compile(r"\b(eval|exec)\s*\(|\bos\.system\s*\(|\bpickle\.loads?\s*\(")
API_RE = {
    "wildcard_cors": re.compile(r"(?i)access-control-allow-origin\s*[:=]\s*['\"]?\*"),
    "debug_enabled": re.compile(r"(?i)(?:debug|DEBUG)\s*[:=]\s*(?:true|1)"),
    "tls_verification_disabled": re.compile(r"(?i)(?:verify\s*=\s*False|rejectUnauthorized\s*:\s*false)"),
    "weak_hash": re.compile(r"(?i)(?:md5|sha1)\s*\("),
}
THREAT_PATTERNS = (
    ("shell_pipe_to_shell", re.compile(r"(?i)\b(?:curl|wget)\b[^\n|]*\|\s*(?:sh|bash)\b")),
    ("encoded_powershell", re.compile(r"(?i)powershell(?:\.exe)?\s+[^\n]{0,80}-(?:enc|encodedcommand)\b")),
    ("world_writable_mode", re.compile(r"(?i)chmod\s+(?:777|a\+rwx)\b")),
    ("destructive_recursive_delete", re.compile(r"(?i)\brm\s+-[^\n]{0,20}r[^\n]{0,20}f\b")),
)
DEPENDENCY_FILES = {"requirements.txt", "pyproject.toml", "package.json", "package-lock.json", "pnpm-lock.yaml", "yarn.lock", "go.mod", "Cargo.toml"}

def _safe_text(value: Any, limit: int = 12000) -> str:
    text = str(value or "")
    return SECRET_RE.sub(lambda m: m.group(0).split(m.group(1))[0] + "[REDACTED]", text)[:limit]

def _inside(root: Path, candidate: Path) -> bool:
    try:
        candidate.resolve().relative_to(root.resolve())
        return True
    except (ValueError, OSError):
        return False

def collect_files(workspace: str | Path, paths: list[str] | None = None) -> tuple[list[tuple[str, str]], list[dict[str, str]]]:
    root = Path(workspace).resolve()
    rows: list[tuple[str, str]] = []
    errors: list[dict[str, str]] = []
    total = 0
    candidates: list[Path] = []
    if paths:
        for raw in paths[:MAX_FILES]:
            p = (root / str(raw)).resolve()
            if not _inside(root, p):
                errors.append({"path": str(raw)[:240], "reason": "path_outside_workspace"})
                continue
            candidates.append(p)
    else:
        for base, dirs, files in os.walk(root, followlinks=False):
            dirs[:] = [d for d in dirs if d not in SKIP_DIRS and not (Path(base) / d).is_symlink()]
            for name in files:
                p = Path(base) / name
                if p.is_symlink():
                    continue
                if p.suffix.lower() in {".py", ".js", ".mjs", ".cjs", ".ts", ".tsx", ".jsx", ".json", ".yml", ".yaml", ".toml", ".ini", ".env", ".xml", ".gradle", ".properties", ".sh"} or name in DEPENDENCY_FILES:
                    candidates.append(p)
                if len(candidates) >= MAX_FILES:
                    break
            if len(candidates) >= MAX_FILES:
                break
    for p in candidates:
        if len(rows) >= MAX_FILES:
            break
        try:
            if p.is_symlink() or not p.is_file() or not _inside(root, p):
                continue
            size = p.stat().st_size
            if size > MAX_FILE_BYTES or total + size > MAX_TOTAL_BYTES:
                errors.append({"path": str(p.relative_to(root)), "reason": "size_limit"})
                continue
            data = p.read_text(encoding="utf-8", errors="replace")
            total += size
            rows.append((p.relative_to(root).as_posix(), data))
        except OSError as exc:
            errors.append({"path": str(p)[:240], "reason": type(exc).__name__})
    return rows, errors

def scan_text(path: str, content: str) -> list[dict[str, Any]]:
    findings: list[dict[str, Any]] = []
    for match in SECRET_RE.finditer(content):
        findings.append({"id": "secret-pattern", "category": "secret_exposure", "severity": "high",
                         "path": path, "line": content.count("\n", 0, match.start()) + 1,
                         "evidence": "[REDACTED] matching credential-like assignment"})
    for match in UNSAFE_PY_RE.finditer(content):
        findings.append({"id": "unsafe-dynamic-execution", "category": "unsafe_code", "severity": "medium",
                         "path": path, "line": content.count("\n", 0, match.start()) + 1,
                         "evidence": match.group(0)[:80]})
    for key, pattern in API_RE.items():
        for match in pattern.finditer(content):
            findings.append({"id": key, "category": "api_configuration", "severity": "medium",
                             "path": path, "line": content.count("\n", 0, match.start()) + 1,
                             "evidence": match.group(0)[:100]})
    for name, pattern in THREAT_PATTERNS:
        for match in pattern.finditer(content):
            findings.append({"id": name, "category": "threat_indicator", "severity": "high",
                             "path": path, "line": content.count("\n", 0, match.start()) + 1,
                             "evidence": match.group(0)[:120]})
    if path.endswith(".py"):
        try:
            tree = ast.parse(content)
            for node in ast.walk(tree):
                if isinstance(node, (ast.Call,)) and isinstance(node.func, ast.Name) and node.func.id in {"eval", "exec"}:
                    findings.append({"id": "python-dynamic-evaluation", "category": "unsafe_code", "severity": "high",
                                     "path": path, "line": getattr(node, "lineno", 1), "evidence": node.func.id + "(...) AST call"})
        except SyntaxError as exc:
            findings.append({"id": "python-syntax-error", "category": "code_quality", "severity": "low",
                             "path": path, "line": exc.lineno or 1, "evidence": "Python parser rejected this file"})
    return findings

def parse_dependencies(path: str, content: str) -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    if Path(path).name == "requirements.txt":
        for line in content.splitlines():
            line = line.strip()
            if not line or line.startswith("#") or line.startswith(("-", "git+", "http:")):
                continue
            m = re.match(r"^([A-Za-z0-9_.-]+)\s*==\s*([A-Za-z0-9_.+!-]+)", line)
            if m:
                rows.append({"name": m.group(1), "version": m.group(2), "ecosystem": "PyPI"})
    elif Path(path).name == "package.json":
        try:
            data = json.loads(content)
            for section in ("dependencies", "devDependencies", "optionalDependencies"):
                for name, version in (data.get(section) or {}).items():
                    if isinstance(version, str) and re.fullmatch(r"\d+\.\d+\.\d+[^\s]*", version.lstrip("v^~")):
                        rows.append({"name": name, "version": version.lstrip("v^~"), "ecosystem": "npm"})
        except (ValueError, TypeError):
            pass
    return rows[:200]

def osv_lookup(dependencies: list[dict[str, str]], timeout: float = 5.0) -> list[dict[str, Any]]:
    """Query public OSV only for exact package/version coordinates; no code or secrets."""
    results: list[dict[str, Any]] = []
    for dep in dependencies[:20]:
        payload = json.dumps({"package": {"name": dep["name"], "ecosystem": dep["ecosystem"]},
                              "version": dep["version"]}).encode()
        req = urllib.request.Request("https://api.osv.dev/v1/query", data=payload,
                                     headers={"Content-Type": "application/json", "User-Agent": "SHADOW-CyberDefense/1.0"})
        try:
            with urllib.request.urlopen(req, timeout=timeout) as response:
                data = json.loads(response.read(256_000).decode("utf-8"))
            for vuln in data.get("vulns", [])[:10]:
                results.append({"package": dep["name"], "version": dep["version"],
                                "ecosystem": dep["ecosystem"], "id": vuln.get("id"),
                                "summary": _safe_text(vuln.get("summary", ""), 300),
                                "modified": vuln.get("modified")})
        except (urllib.error.URLError, TimeoutError, ValueError, OSError) as exc:
            results.append({"package": dep["name"], "version": dep["version"], "lookup_error": type(exc).__name__})
    return results

def audit_workspace(workspace: str | Path, paths: list[str] | None = None, online_lookup: bool = False) -> dict[str, Any]:
    files, errors = collect_files(workspace, paths)
    findings: list[dict[str, Any]] = []
    dependencies: list[dict[str, str]] = []
    for path, content in files:
        findings.extend(scan_text(path, content))
        dependencies.extend(parse_dependencies(path, content))
    osv = osv_lookup(dependencies) if online_lookup else []
    for item in osv:
        if item.get("id"):
            findings.append({"id": item["id"], "category": "known_vulnerability", "severity": "needs_triage",
                             "path": "dependency manifest", "line": None,
                             "evidence": f"{item['package']}@{item['version']} — {_safe_text(item.get('summary', ''), 240)}"})
    counts = {level: sum(1 for f in findings if f["severity"] == level) for level in ("critical", "high", "medium", "low", "needs_triage")}
    return {"status": "completed" if not errors else "completed_with_limits", "files_scanned": len(files),
            "bytes_scanned": sum(len(x.encode("utf-8")) for _, x in files), "findings": findings[:500],
            "finding_count": len(findings), "severity_counts": counts, "dependencies": dependencies[:200],
            "osv_lookup": {"requested": bool(online_lookup), "results": osv, "source": "OSV.dev" if online_lookup else None},
            "limitations": ["static analysis only; no code execution", "not a penetration test",
                            "dependency findings require version and reachability triage"],
            "collection_errors": errors[:100]}

def create_review_sandbox(workspace: str | Path, files: dict[str, str] | None = None) -> dict[str, Any]:
    """Create a temporary review copy; intentionally does not claim OS process isolation."""
    root = Path(workspace).resolve()
    sandbox = Path(tempfile.mkdtemp(prefix="shadow-cyber-review-")).resolve()
    copied = []
    for raw, content in list((files or {}).items())[:100]:
        rel = Path(str(raw))
        if rel.is_absolute() or ".." in rel.parts or len(str(content).encode()) > 100_000:
            continue
        target = (sandbox / rel).resolve()
        if not _inside(sandbox, target):
            continue
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(str(content), encoding="utf-8")
        copied.append(rel.as_posix())
    return {"path": str(sandbox), "files_copied": copied, "file_count": len(copied),
            "isolation": "temporary_review_copy_only", "execution_performed": False,
            "limitations": ["not an OS-level sandbox", "no process or network isolation", "no target code executed"]}

def patch_plan(findings: list[dict[str, Any]]) -> dict[str, Any]:
    actions = []
    for item in findings[:100]:
        category = item.get("category")
        advice = {
            "secret_exposure": "Revoke and rotate the exposed credential; load it from a secret manager or environment.",
            "unsafe_code": "Replace dynamic execution with explicit parsing or a constrained allow-list.",
            "api_configuration": "Review the setting against the deployment trust boundary and enforce least privilege.",
            "threat_indicator": "Quarantine for analyst review; do not execute the matched command.",
            "known_vulnerability": "Upgrade to a vendor-fixed version after compatibility and reachability checks.",
        }.get(category, "Review the evidence and add a focused regression test.")
        actions.append({"finding_id": item.get("id"), "path": item.get("path"), "line": item.get("line"),
                        "recommendation": advice, "applied": False, "approval_required": True})
    return {"status": "proposal_only", "changes_applied": False, "actions": actions,
            "requires_human_review": True}

def regression_plan(findings: list[dict[str, Any]], test_paths: list[str] | None = None) -> dict[str, Any]:
    return {"status": "plan_only", "executed": False,
            "recommended_checks": ["run existing project test suite", "run static security scan again",
                                   "verify each finding is no longer reproducible",
                                   "review dependency lockfile and runtime compatibility"],
            "test_paths": [str(x)[:240] for x in (test_paths or [])[:50]],
            "finding_ids_to_recheck": sorted({str(x.get("id")) for x in findings if x.get("id")}),
            "note": "This planner does not claim tests were run."}
