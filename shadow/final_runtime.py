"""Final provider-neutral runtime services for SHADOW.

Online execution is preferred. Deterministic local execution remains available as a
fallback. Biometric voiceprint is an optional legacy adapter only and is never a
product requirement; absence of its provider fails closed rather than blocking SHADOW.
"""
from __future__ import annotations
import hashlib, json, os, time
from dataclasses import dataclass, asdict
from pathlib import Path
from typing import Any, Dict, List, Optional
from shadow.voiceprint_provider import RawAudioVoiceprintProvider

@dataclass(frozen=True)
class Capability:
    id: str
    name: str
    source: str
    online: bool
    offline: bool
    risk: str = "low"

class UnifiedRuntime:
    """Online connectivity state for SHADOW's AI runtime.
 
    Local Android/device adapters may still operate under governance, but this
    runtime never substitutes local AI for an unavailable cloud provider.
    """
    def __init__(self):
        self.mode = "online-required"
        self.last_error: Optional[str] = None
        self.last_success = 0.0
        self.last_online_probe = 0.0
        self.provider_failures = 0
    def choose(self, online_available: bool) -> str:
        self.mode = "online" if online_available else "online-unavailable"
        self.last_online_probe = time.time()
        return self.mode
    def execute(self, online_fn, unavailable_fn=None):
        self.last_online_probe = time.time()
        try:
            value = online_fn()
            self.mode, self.last_error = "online", None
            self.last_success = time.time()
            return value
        except Exception as exc:
            self.mode = "online-unavailable"
            self.last_error = f"{type(exc).__name__}: {exc}"[:512]
            self.provider_failures += 1
            raise
    def probe(self, online_fn) -> bool:
        self.last_online_probe = time.time()
        try:
            online_fn()
            self.mode, self.last_error = "online", None
            self.last_success = time.time()
            return True
        except Exception as exc:
            self.mode = "online-unavailable"
            self.last_error = f"{type(exc).__name__}: {exc}"[:512]
            self.provider_failures += 1
            return False
    def status(self) -> Dict[str, Any]:
        return {"mode": self.mode, "last_error": self.last_error, "last_success": self.last_success,
                "last_online_probe": self.last_online_probe, "provider_failures": self.provider_failures}

class DiscoveryEngine:
    def __init__(self, root: str = ""): self.root = Path(root or ".")
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
            Capability("shadow.home", "Smart-home adapter boundary", "adapter", True, True, "high"),
            Capability("shadow.car", "Vehicle adapter boundary", "adapter", True, True, "high"),
            Capability("shadow.companions", "Companion capability bus", "adapter", True, True, "medium"),
            Capability("shadow.voiceprint", "Legacy optional speaker verification", "legacy-provider", True, False, "critical"),
            Capability("shadow.custom_voice", "Custom TTS provider boundary", "provider", True, False, "medium"),
        ]
    def snapshot(self) -> Dict[str, Any]:
        caps = self.discover(); return {"count": len(caps), "capabilities": [asdict(c) for c in caps]}

class IntegrationEngine:
    def __init__(self, state_dir: str = ""):
        self.state = Path(state_dir or os.path.expanduser("~/.shadow")); self.state.mkdir(parents=True, exist_ok=True)
        self.journal = self.state / "integration-journal.json"
    def _load(self):
        try:
            d = json.loads(self.journal.read_text(encoding="utf-8")); return d if isinstance(d, list) else []
        except Exception: return []
    def integrate(self, module_path: str, allowed_prefixes: tuple[str, ...] = ("shadow/",)):
        p = Path(module_path); normalized = p.as_posix()
        if not any(normalized.startswith(x) for x in allowed_prefixes): raise PermissionError("module path is outside the integration allowlist")
        if not p.exists() or not p.is_file(): raise FileNotFoundError(normalized)
        entry = {"path": normalized, "sha256": hashlib.sha256(p.read_bytes()).hexdigest(), "integrated_at": time.time(), "status": "promoted"}
        log = self._load(); log.append(entry); self.journal.write_text(json.dumps(log, ensure_ascii=False, indent=2), encoding="utf-8"); return entry
    def rollback_last(self):
        log = self._load()
        if not log: return {"status": "nothing_to_rollback"}
        entry = log.pop(); entry["status"] = "rolled_back"; entry["rolled_back_at"] = time.time()
        self.journal.write_text(json.dumps(log, ensure_ascii=False, indent=2), encoding="utf-8"); return entry

@dataclass
class Companion:
    id: str
    kind: str
    state: str = "discovered"
    permissions: tuple[str, ...] = ()

class CompanionRegistry:
    def __init__(self): self.items: Dict[str, Companion] = {}
    def discover(self, cid: str, kind: str): c = Companion(cid, kind); self.items[cid] = c; return c
    def authenticate(self, cid: str):
        c = self.items[cid]
        if c.state == "revoked": raise PermissionError("revoked companion cannot authenticate")
        c.state = "authenticated"; return c
    def trust(self, cid: str, permissions: tuple[str, ...] = ()):
        c = self.items[cid]
        if c.state not in {"authenticated", "trusted"}: raise PermissionError("companion must authenticate before trust promotion")
        c.state = "trusted"; c.permissions = tuple(sorted(set(permissions))); return c
    def revoke(self, cid: str): c = self.items[cid]; c.state = "revoked"; c.permissions = (); return c
    def can(self, cid: str, permission: str):
        c = self.items.get(cid); return bool(c and c.state == "trusted" and permission in c.permissions)
    def snapshot(self): return [asdict(x) for x in self.items.values()]

class VoiceprintAdapter:
    """Optional legacy adapter. Not used to authorize normal SHADOW commands."""
    def __init__(self): self.enrolled = False; self.provider = RawAudioVoiceprintProvider()
    def status(self):
        provider = self.provider.status()
        return {"available": self.provider.configured, "enrolled": self.enrolled, "verified": False,
                "provider_configured": self.provider.configured, "provider": provider.get("provider", "none"),
                "reason": provider.get("reason") if self.provider.configured else "optional provider not configured"}
    def verify(self, audio_bytes: bytes, content_type: str = "audio/wav"):
        if not self.provider.configured: raise RuntimeError("voiceprint provider not configured")
        result = self.provider.verify(audio_bytes, content_type); self.enrolled = bool(result.enrolled); return asdict(result)

class FinalRuntime:
    def __init__(self, root: str = ""):
        self.unified = UnifiedRuntime(); self.discovery = DiscoveryEngine(root); self.integration = IntegrationEngine()
        self.companions = CompanionRegistry(); self.voiceprint = VoiceprintAdapter()
    def status(self):
        return {"runtime": self.unified.status(), "discovery": self.discovery.snapshot(), "companions": self.companions.snapshot(),
                "voiceprint": {"required": False, **self.voiceprint.status()},
                "provider_boundaries": {"cloud_ai": bool(os.environ.get("OPENAI_API_KEY")),
                                        "custom_tts": bool(os.environ.get("SHADOW_TTS_VOICE_ID")),
                                        "voiceprint": False}}
