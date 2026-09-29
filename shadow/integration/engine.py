"""MOD-98: governed adapter/module integration engine."""
from __future__ import annotations
from dataclasses import asdict, dataclass
import hashlib
from pathlib import Path
from time import time
from typing import Iterable

@dataclass(frozen=True)
class IntegrationReceipt:
    module: str
    sha256: str
    status: str
    integrated_at: float

class AdapterIntegrationEngine:
    def __init__(self, root: str | Path = "."):
        self.root = Path(root).resolve()
        self._history: list[IntegrationReceipt] = []

    def inspect(self, module_path: str, allowed_prefixes: Iterable[str] = ("shadow/",)) -> dict:
        raw = str(module_path).replace("\\", "/").lstrip("/")
        if not any(raw.startswith(x) for x in allowed_prefixes):
            raise PermissionError("module_path_not_allowed")
        target = (self.root / raw).resolve()
        if not target.is_file() or self.root not in target.parents:
            raise FileNotFoundError(raw)
        return {"module": raw, "size": target.stat().st_size, "sha256": hashlib.sha256(target.read_bytes()).hexdigest()}

    def promote(self, module_path: str) -> IntegrationReceipt:
        info = self.inspect(module_path)
        receipt = IntegrationReceipt(info["module"], info["sha256"], "PROMOTED", time())
        self._history.append(receipt)
        return receipt

    def rollback_last(self) -> dict:
        if not self._history:
            return {"status": "NOTHING_TO_ROLLBACK"}
        last = self._history.pop()
        return {"status": "ROLLED_BACK", "module": last.module, "sha256": last.sha256}

    def snapshot(self) -> list[dict]:
        return [asdict(x) for x in self._history]
