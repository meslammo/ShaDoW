"""MOD-60..64 final runtime services.

These services are deliberately provider-neutral: online/cloud is preferred when
available, offline remains deterministic and safe, discovery is read-only, and
integration is transactional with rollback metadata. Voiceprint is an adapter
contract: Android SpeechRecognizer text is never treated as biometric proof.
"""
from __future__ import annotations
import hashlib, importlib.util, json, os, time
from dataclasses import dataclass, asdict
from pathlib import Path
from typing import Any, Dict, List, Optional


@dataclass(frozen=True)
class Capability:
    id: str
    name: str
    source: str
    online: bool
    offline: bool
    risk: str = "low"


class UnifiedRuntime:
    """MOD-60: online-first with automatic offline fallback and recovery."""
    def __init__(self):
        self.mode = "online"
        self.last_error: Optional[str] = None
        self.last_success = 0.0

    def choose(self, online_available: bool) -> str:
        self.mode = "online" if online_available else "offline"
        return self.mode

    def execute(self, online_fn, offline_fn):
        try:
            value = online_fn()
            self.mode, self.last_error, self.last_success = "online", None, time.time()
            return value
        except Exception as exc:
            self.last_error = type(exc).__name__
            self.mode = "offline"
            value = offline_fn()
            self.last_success = time.time()
            return value

    def status(self) -> Dict[str, Any]:
        return {"mode": self.mode, "last_error": self.last_error, "last_success": self.last_success}


class DiscoveryEngine:
    """MOD-61: read-only capability discovery with stable IDs."""
    def __init__(self, root: str = ""):
        self.root = Path(root or ".")

    def discover(self) -> List[Capability]:
        caps = [
            Capability("android.device", "Android device control", "native", False, True, "medium"),
            Capability("android.apps", "Installed application launch", "native", False, True),
            Capability("android.contacts", "Contacts and calling", "native", False, True, "high"),
            Capability("android.media", "Camera/media/volume", "native", False, True, "medium"),
            Capability("shadow.12core", "Governed 12-Core runtime", "embedded-python", False, True, "high"),
            Capability("shadow.cloud", "Cloud AI gateway", "railway", True, False),
            Capability("shadow.tts", "Shadow TTS gateway", "railway", True, False),
            Capability("shadow.github", "GitHub development gateway", "oauth", True, False, "high"),
            Capability("shadow.home", "Smart-home adapter", "adapter", True, False, "high"),
            Capability("shadow.car", "Car/vehicle adapter", "adapter", True, False, "high"),
            Capability("shadow.companions", "Companion capability bus", "adapter", True, True, "medium"),
        ]
        return caps

    def snapshot(self) -> Dict[str, Any]:
        caps = self.discover()
        return {"count": len(caps), "capabilities": [asdict(c) for c in caps]}


class IntegrationEngine:
    """MOD-62: allowlisted module integration with checksum + rollback journal."""
    def __init__(self, state_dir: str = ""):
        self.state = Path(state_dir or os.path.expanduser("~/.shadow"))
        self.state.mkdir(parents=True, exist_ok=True)
        self.journal = self.state / "integration-journal.json"

    def _load(self) -> List[Dict[str, Any]]:
        if not self.journal.exists(): return []
        try: return json.loads(self.journal.read_text(encoding="utf-8"))
        except Exception: return []

    def integrate(self, module_path: str, allowed_prefixes: tuple[str, ...] = ("shadow/",)) -> Dict[str, Any]:
        p = Path(module_path)
        normalized = p.as_posix()
        if not any(normalized.startswith(prefix) for prefix in allowed_prefixes):
            raise PermissionError("module path is outside the integration allowlist")
        if not p.exists() or not p.is_file(): raise FileNotFoundError(normalized)
        digest = hashlib.sha256(p.read_bytes()).hexdigest()
        entry = {"path": normalized, "sha256": digest, "integrated_at": time.time(), "status": "promoted"}
        log = self._load(); log.append(entry); self.journal.write_text(json.dumps(log, ensure_ascii=False, indent=2), encoding="utf-8")
        return entry

    def rollback_last(self) -> Dict[str, Any]:
        log = self._load()
        if not log: return {"status": "nothing_to_rollback"}
        entry = log.pop(); entry["status"] = "rolled_back"; entry["rolled_back_at"] = time.time()
        self.journal.write_text(json.dumps(log, ensure_ascii=False, indent=2), encoding="utf-8")
        return entry


@dataclass
class Companion:
    id: str
    kind: str
    state: str = "discovered"
    permissions: tuple[str, ...] = ()


class CompanionRegistry:
    """MOD-63: companion lifecycle with explicit trust and revocation."""
    def __init__(self): self.items: Dict[str, Companion] = {}
    def discover(self, cid: str, kind: str) -> Companion:
        c = Companion(cid, kind); self.items[cid] = c; return c
    def authenticate(self, cid: str) -> Companion:
        c = self.items[cid]; c.state = "authenticated"; return c
    def trust(self, cid: str, permissions: tuple[str, ...] = ()) -> Companion:
        c = self.items[cid]; c.state = "trusted"; c.permissions = permissions; return c
    def revoke(self, cid: str) -> Companion:
        c = self.items[cid]; c.state = "revoked"; c.permissions = (); return c
    def snapshot(self) -> List[Dict[str, Any]]: return [asdict(x) for x in self.items.values()]


class VoiceprintAdapter:
    """MOD-64 security boundary for future raw-audio speaker verification."""
    def __init__(self): self.enrolled = False
    def status(self) -> Dict[str, Any]:
        return {"available": False, "enrolled": self.enrolled, "verified": False,
                "reason": "raw-audio speaker embedding provider is not bundled; SpeechRecognizer text is not biometric evidence"}
    def verify(self, audio_bytes: bytes) -> bool:
        if not audio_bytes: return False
        raise RuntimeError("VOICEPRINT_PROVIDER_NOT_CONFIGURED")


class FinalRuntime:
    def __init__(self, root: str = ""):
        self.unified = UnifiedRuntime()
        self.discovery = DiscoveryEngine(root)
        self.integration = IntegrationEngine()
        self.companions = CompanionRegistry()
        self.voiceprint = VoiceprintAdapter()

    def status(self) -> Dict[str, Any]:
        return {"runtime": self.unified.status(), "discovery": self.discovery.snapshot(),
                "companions": self.companions.snapshot(), "voiceprint": self.voiceprint.status()}
