"""MOD-60..66 final runtime services.

Provider-neutral, fail-closed runtime services for SHADOW. Cloud/online execution
is preferred, deterministic offline execution is the fallback, discovery is
read-only, integration is journaled, and biometric voice verification is never
faked when no raw-audio provider is configured.
"""
from __future__ import annotations
import hashlib, json, os, time
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
    """Online-first execution with explicit fallback and recovery probes."""
    def __init__(self):
        self.mode = "online"
        self.last_error: Optional[str] = None
        self.last_success = 0.0
        self.last_online_probe = 0.0
        self.fallback_count = 0
        self.recovery_count = 0

    def choose(self, online_available: bool) -> str:
        self.mode = "online" if online_available else "offline"
        self.last_online_probe = time.time()
        return self.mode

    def execute(self, online_fn, offline_fn):
        was_offline = self.mode == "offline"
        try:
            value = online_fn()
            self.mode, self.last_error = "online", None
            self.last_success = time.time()
            self.last_online_probe = self.last_success
            if was_offline:
                self.recovery_count += 1
            return value
        except Exception as exc:
            self.last_error = f"{type(exc).__name__}: {exc}"[:512]
            self.mode = "offline"
            self.fallback_count += 1
            self.last_online_probe = time.time()
            value = offline_fn()
            self.last_success = time.time()
            return value

    def probe(self, online_fn) -> bool:
        self.last_online_probe = time.time()
        try:
            online_fn()
            was_offline = self.mode == "offline"
            self.mode, self.last_error = "online", None
            if was_offline:
                self.recovery_count += 1
            return True
        except Exception as exc:
            self.mode = "offline"
            self.last_error = f"{type(exc).__name__}: {exc}"[:512]
            return False

    def status(self) -> Dict[str, Any]:
        return {"mode": self.mode, "last_error": self.last_error,
                "last_success": self.last_success,
                "last_online_probe": self.last_online_probe,
                "fallback_count": self.fallback_count,
                "recovery_count": self.recovery_count}

class DiscoveryEngine:
    """Read-only capability discovery with stable IDs."""
    def __init__(self, root: str = ""):
        self.root = Path(root or ".")

    def discover(self) -> List[Capability]:
        return [
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

    def snapshot(self) -> Dict[str, Any]:
        caps = self.discover()
        return {"count": len(caps), "capabilities": [asdict(c) for c in caps]}

class IntegrationEngine:
    """Allowlisted integration with checksum and auditable rollback journal."""
    def __init__(self, state_dir: str = ""):
        self.state = Path(state_dir or os.path.expanduser("~/.shadow"))
        self.state.mkdir(parents=True, exist_ok=True)
        self.journal = self.state / "integration-journal.json"

    def _load(self) -> List[Dict[str, Any]]:
        if not self.journal.exists():
            return []
        try:
            data = json.loads(self.journal.read_text(encoding="utf-8"))
            return data if isinstance(data, list) else []
        except Exception:
            return []

    def integrate(self, module_path: str, allowed_prefixes: tuple[str, ...] = ("shadow/",)) -> Dict[str, Any]:
        p = Path(module_path)
        normalized = p.as_posix()
        if not any(normalized.startswith(prefix) for prefix in allowed_prefixes):
            raise PermissionError("module path is outside the integration allowlist")
        if not p.exists() or not p.is_file():
            raise FileNotFoundError(normalized)
        digest = hashlib.sha256(p.read_bytes()).hexdigest()
        entry = {"path": normalized, "sha256": digest, "integrated_at": time.time(),
                 "status": "promoted",
                 "rollback": "journal-only; file mutation requires an authorized installer"}
        log = self._load(); log.append(entry)
        self.journal.write_text(json.dumps(log, ensure_ascii=False, indent=2), encoding="utf-8")
        return entry

    def rollback_last(self) -> Dict[str, Any]:
        log = self._load()
        if not log:
            return {"status": "nothing_to_rollback"}
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
    """Companion lifecycle with explicit authentication, trust and revocation."""
    def __init__(self):
        self.items: Dict[str, Companion] = {}

    def discover(self, cid: str, kind: str) -> Companion:
        c = Companion(cid, kind); self.items[cid] = c; return c

    def authenticate(self, cid: str) -> Companion:
        c = self.items[cid]
        if c.state == "revoked":
            raise PermissionError("revoked companion cannot authenticate")
        c.state = "authenticated"; return c

    def trust(self, cid: str, permissions: tuple[str, ...] = ()) -> Companion:
        c = self.items[cid]
        if c.state not in {"authenticated", "trusted"}:
            raise PermissionError("companion must authenticate before trust promotion")
        c.state = "trusted"; c.permissions = tuple(sorted(set(permissions))); return c

    def revoke(self, cid: str) -> Companion:
        c = self.items[cid]; c.state = "revoked"; c.permissions = (); return c

    def can(self, cid: str, permission: str) -> bool:
        c = self.items.get(cid)
        return bool(c and c.state == "trusted" and permission in c.permissions)

    def snapshot(self) -> List[Dict[str, Any]]:
        return [asdict(x) for x in self.items.values()]

class VoiceprintAdapter:
    """Fail-closed boundary for future raw-audio speaker verification."""
    def __init__(self):
        self.enrolled = False

    def status(self) -> Dict[str, Any]:
        configured = bool(os.environ.get("SHADOW_VOICEPRINT_PROVIDER"))
        return {"available": False, "enrolled": self.enrolled, "verified": False,
                "provider_configured": configured,
                "reason": "raw-audio speaker embedding provider is not configured; SpeechRecognizer text is never biometric evidence"}

    def verify(self, audio_bytes: bytes) -> bool:
        if not audio_bytes:
            return False
        raise RuntimeError("VOICEPRINT_PROVIDER_NOT_CONFIGURED")

class FinalRuntime:
    def __init__(self, root: str = ""):
        self.unified = UnifiedRuntime()
        self.discovery = DiscoveryEngine(root)
        self.integration = IntegrationEngine()
        self.companions = CompanionRegistry()
        self.voiceprint = VoiceprintAdapter()

    def status(self) -> Dict[str, Any]:
        return {"runtime": self.unified.status(),
                "discovery": self.discovery.snapshot(),
                "companions": self.companions.snapshot(),
                "voiceprint": self.voiceprint.status(),
                "provider_boundaries": {
                    "cloud_ai": bool(os.environ.get("OPENAI_API_KEY")),
                    "custom_tts": bool(os.environ.get("SHADOW_TTS_VOICE_ID")),
                    "voiceprint": bool(os.environ.get("SHADOW_VOICEPRINT_PROVIDER"))}}
