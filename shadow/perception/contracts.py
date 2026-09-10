"""Provider-neutral perception contracts."""
from __future__ import annotations
from dataclasses import dataclass, field
from typing import Any, Dict, Optional, Protocol

@dataclass
class AudioInput:
    pcm: bytes
    sample_rate: int = 16000
    channels: int = 1
    language: Optional[str] = None

@dataclass
class Transcript:
    text: str
    confidence: float = 0.0
    language: Optional[str] = None
    metadata: Dict[str, Any] = field(default_factory=dict)

@dataclass
class ImageInput:
    data: bytes
    mime_type: str = "image/jpeg"
    metadata: Dict[str, Any] = field(default_factory=dict)

class STTProvider(Protocol):
    def transcribe(self, audio: AudioInput) -> Transcript: ...

class TTSProvider(Protocol):
    def synthesize(self, text: str, *, voice: Optional[str] = None) -> bytes: ...

class VisionProvider(Protocol):
    def analyze(self, image: ImageInput, *, prompt: str = "Describe this image accurately.") -> str: ...
