"""Capabilities advertised to clients without granting them automatically."""
CAPABILITIES = {
    "chat": {"confirmation": False},
    "web_search": {"confirmation": False},
    "files.read": {"confirmation": False},
    "microphone": {"confirmation": True},
    "camera": {"confirmation": True},
    "screen.read": {"confirmation": True},
    "notifications": {"confirmation": True},
    "contacts.read": {"confirmation": True},
    "calendar.write": {"confirmation": True},
    "device.control": {"confirmation": True},
    "home.control": {"confirmation": True},
    "car.control": {"confirmation": True},
    "location.read": {"confirmation": True},
}

def requires_confirmation(capability: str) -> bool:
    return bool(CAPABILITIES.get(capability, {"confirmation": True})["confirmation"])
