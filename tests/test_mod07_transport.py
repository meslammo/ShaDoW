from shadow.runtime.transport import ShadowRequest, response_from_result
from shadow.runtime.runtime import RuntimeResult

def test_transport_round_trip():
    req = ShadowRequest("hello", session_id="s1", device_id="phone")
    result = RuntimeResult(request="hello", answer="ok", confidence=.9, verified=True)
    out = response_from_result(req, result)
    assert out.request_id == req.request_id
    assert out.text == "ok"
    assert out.verified is True
