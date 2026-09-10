from shadow.runtime.device_context import DeviceContext

def test_device_context_is_serializable():
    ctx = DeviceContext("phone-1", capabilities=["voice", "camera"], metadata={"app": "shadow"})
    data = ctx.as_context()
    assert data["device_id"] == "phone-1"
    assert "camera" in data["capabilities"]
