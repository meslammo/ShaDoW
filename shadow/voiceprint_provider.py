"""Raw-audio speaker verification provider boundary for SHADOW MOD-67.

The module intentionally delegates biometric matching to an externally configured
provider. It never treats ASR text as biometric evidence and fails closed when
no provider is configured.
"""
from __future__ import annotations

import base64
import json
import os
import urllib.error
import urllib.request
from dataclasses import dataclass
from typing import Any, Dict, Optional


@dataclass(frozen=True)
class VoiceprintResult:
    verified: bool
    enrolled: bool
    provider: str
    reason: str
    score: Optional[float] = None


class RawAudioVoiceprintProvider:
    """HTTP adapter for a real raw-audio speaker-embedding service.

    Expected response JSON:
      {"verified": true, "score": 0.91, "enrolled": true}

    Optional bearer authentication is read from SHADOW_VOICEPRINT_PROVIDER_TOKEN.
    """

    def __init__(self, endpoint: Optional[str] = None, timeout_s: float = 12.0):
        self.endpoint = (endpoint or os.environ.get("SHADOW_VOICEPRINT_PROVIDER_URL", "")).strip()
        self.timeout_s = float(timeout_s)

    @property
    def configured(self) -> bool:
        return bool(self.endpoint)

    def status(self) -> Dict[str, Any]:
        return {
            "configured": self.configured,
            "provider": "raw-audio-http" if self.configured else "none",
            "endpoint_configured": bool(self.endpoint),
            "enrolled": False,
            "verified": False,
            "reason": "provider endpoint configured but enrollment status is unknown"
            if self.configured
            else "raw-audio speaker embedding provider is not configured",
        }

    def verify(self, audio_bytes: bytes, content_type: str = "audio/wav") -> VoiceprintResult:
        if not audio_bytes:
            return VoiceprintResult(False, False, "none", "empty_audio")
        if not self.configured:
            return VoiceprintResult(False, False, "none", "VOICEPRINT_PROVIDER_NOT_CONFIGURED")

        payload = json.dumps(
            {
                "audio_base64": base64.b64encode(audio_bytes).decode("ascii"),
                "content_type": content_type,
            }
        ).encode("utf-8")
        headers = {"Content-Type": "application/json", "Accept": "application/json"}
        token = os.environ.get("SHADOW_VOICEPRINT_PROVIDER_TOKEN", "").strip()
        if token:
            headers["Authorization"] = f"Bearer {token}"

        request = urllib.request.Request(self.endpoint, data=payload, headers=headers, method="POST")
        try:
            with urllib.request.urlopen(request, timeout=self.timeout_s) as response:
                raw = response.read().decode("utf-8")
            body = json.loads(raw)
        except urllib.error.HTTPError as exc:
            return VoiceprintResult(False, False, "raw-audio-http", f"provider_http_{exc.code}")
        except (urllib.error.URLError, TimeoutError):
            return VoiceprintResult(False, False, "raw-audio-http", "provider_unreachable")
        except (UnicodeDecodeError, json.JSONDecodeError, ValueError):
            return VoiceprintResult(False, False, "raw-audio-http", "provider_invalid_response")

        verified = body.get("verified") is True
        enrolled = body.get("enrolled") is True
        score = body.get("score")
        try:
            score = float(score) if score is not None else None
        except (TypeError, ValueError):
            score = None
        if not isinstance(score, (int, float)):
            score = None
        reason = str(body.get("reason") or ("verified" if verified else "not_verified"))[:256]
        return VoiceprintResult(
            verified=verified,
            enrolled=enrolled,
            provider="raw-audio-http",
            reason=reason,
            score=score,
        )
