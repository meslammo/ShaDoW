import os
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from shadow.voiceprint_provider import RawAudioVoiceprintProvider  # noqa: E402


def test_voiceprint_fails_closed_without_provider(monkeypatch):
    monkeypatch.delenv("SHADOW_VOICEPRINT_PROVIDER_URL", raising=False)
    provider = RawAudioVoiceprintProvider()
    assert provider.configured is False
    result = provider.verify(b"sample-audio", "audio/wav")
    assert result.verified is False
    assert result.reason == "VOICEPRINT_PROVIDER_NOT_CONFIGURED"


def test_voiceprint_status_is_explicit(monkeypatch):
    monkeypatch.delenv("SHADOW_VOICEPRINT_PROVIDER_URL", raising=False)
    status = RawAudioVoiceprintProvider().status()
    assert status["configured"] is False
    assert status["provider"] == "none"
