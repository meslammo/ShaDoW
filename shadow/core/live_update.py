"""Bounded live-update manager for SHADOW Python-side updates.

Updates are staged and validated before activation. Android Java changes still
require a normal APK build; this manager is intentionally non-destructive and
never replaces the running conversation state.
"""
from __future__ import annotations

from dataclasses import dataclass, asdict
import hashlib
from pathlib import Path
import py_compile
import shutil
from typing import Iterable


@dataclass(frozen=True)
class UpdateResult:
    ok: bool
    update_id: str
    status: str
    version: str
    files: tuple[str, ...] = ()
    error: str = ""


class LiveUpdateManager:
    def __init__(self, workspace: str = ".") -> None:
        self.root = Path(workspace).resolve()
        self.staging = self.root / ".shadow" / "updates" / "staging"
        self.backups = self.root / ".shadow" / "updates" / "backups"
        self.staging.mkdir(parents=True, exist_ok=True)
        self.backups.mkdir(parents=True, exist_ok=True)

    @staticmethod
    def _safe_rel(path: str) -> Path:
        rel = Path(path)
        if rel.is_absolute() or ".." in rel.parts or rel.parts[:1] not in {("shadow",), ("plugins",)}:
            raise ValueError("update_path_outside_allowed_roots")
        return rel

    def stage(self, version: str, files: dict[str, str]) -> Path:
        if not version.strip():
            raise ValueError("version_required")
        update_id = hashlib.sha256((version + "|" + "|".join(sorted(files))).encode()).hexdigest()[:16]
        target = self.staging / update_id
        if target.exists():
            shutil.rmtree(target)
        target.mkdir(parents=True)
        for name, content in files.items():
            rel = self._safe_rel(name)
            dst = target / rel
            dst.parent.mkdir(parents=True, exist_ok=True)
            dst.write_text(str(content), encoding="utf-8")
        (target / ".version").write_text(version.strip(), encoding="utf-8")
        return target

    def validate(self, staged: Path) -> UpdateResult:
        version_file = staged / ".version"
        version = version_file.read_text(encoding="utf-8").strip() if version_file.exists() else ""
        files = tuple(str(p.relative_to(staged)) for p in staged.rglob("*.py"))
        try:
            for path in files:
                py_compile.compile(str(staged / path), doraise=True)
        except Exception as exc:
            return UpdateResult(False, staged.name, "validation_failed", version, files, str(exc)[:240])
        return UpdateResult(True, staged.name, "validated", version, files)

    def activate(self, staged: Path) -> UpdateResult:
        check = self.validate(staged)
        if not check.ok:
            return check
        backup = self.backups / f"{check.update_id}-{check.version}"
        backup.mkdir(parents=True, exist_ok=True)
        try:
            for name in check.files:
                rel = self._safe_rel(name)
                src = staged / rel
                dst = self.root / rel
                dst.parent.mkdir(parents=True, exist_ok=True)
                if dst.exists():
                    backup_file = backup / rel
                    backup_file.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copy2(dst, backup_file)
                shutil.copy2(src, dst)
        except Exception as exc:
            self.rollback(backup, check.files)
            return UpdateResult(False, check.update_id, "activation_failed_rolled_back", check.version, check.files, str(exc)[:240])
        return UpdateResult(True, check.update_id, "activated", check.version, check.files)

    def rollback(self, backup: Path, files: Iterable[str]) -> None:
        for name in files:
            rel = self._safe_rel(name)
            src = backup / rel
            dst = self.root / rel
            if src.exists():
                dst.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(src, dst)

    @staticmethod
    def describe(result: UpdateResult) -> dict:
        return asdict(result)
