"""MOD-35.7: SHADOW live process indicators.

One canonical icon/label vocabulary shared by Android UI and runtime logs.
"""
from __future__ import annotations
from dataclasses import dataclass


@dataclass(frozen=True)
class ProcessIndicator:
    key: str
    icon: str
    label: str


INDICATORS = {
    "thinking": ProcessIndicator("thinking", "🧠", "THINKING"),
    "reading": ProcessIndicator("reading", "📖", "READING"),
    "searching": ProcessIndicator("searching", "🔎", "SEARCHING"),
    "analyzing": ProcessIndicator("analyzing", "📐", "ANALYZING"),
    "writing": ProcessIndicator("writing", "✍️", "WRITING"),
    "executing": ProcessIndicator("executing", "🛠️", "EXECUTING"),
    "testing": ProcessIndicator("testing", "🧪", "TESTING"),
    "designing": ProcessIndicator("designing", "🎨", "DESIGNING"),
    "listening": ProcessIndicator("listening", "🎙️", "LISTENING"),
    "speaking": ProcessIndicator("speaking", "🔊", "SPEAKING"),
    "done": ProcessIndicator("done", "✅", "DONE"),
}


def render(key: str, detail: str = "") -> str:
    item = INDICATORS[key]
    return f"{item.icon} {item.label}" + (f" • {detail}" if detail else "")
