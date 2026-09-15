from pathlib import Path


def test_mod57_documents_custom_voice_without_committing_audio():
    doc = Path("shadow/voice/README.md").read_text(encoding="utf-8")
    backend = Path("backend/server.mjs").read_text(encoding="utf-8")
    assert "SHADOW_TTS_VOICE_ID" in doc
    assert "SHADOW_TTS_VOICE_ID" in backend
    assert "tts_voice_mode" in backend
    assert "voice:ttsVoiceId?{id:ttsVoiceId}:ttsVoice" in backend
    assert "audio sample" in doc.lower()
