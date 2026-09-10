from shadow.runtime.health import health_report

def test_health_report():
    h = health_report()
    assert h["service"] == "SHADOW"
    assert h["status"] == "ready"
    assert h["fail_closed"] is True
    assert h["capability_count"] >= 10
