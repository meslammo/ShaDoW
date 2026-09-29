from shadow.supernice.catalog import CORE_SPECS,CORE_BY_ID
from shadow.supernice.runtime import CoreRuntime
from shadow.supernice.contracts import CoreRequest

def test_exactly_150_unique_cores():
    assert len(CORE_SPECS)==150
    assert len(CORE_BY_ID)==150

def test_original_twelve_are_preserved():
    assert [s.id for s in CORE_SPECS[:12]]==[f"CORE-{i:03d}" for i in range(1,13)]

def test_sensitive_core_requires_confirmation():
    r=CoreRuntime().execute(CoreRequest("CORE-012","deploy",confirmed=False))
    assert r.status=="confirmation_required"

def test_free_first_has_no_internal_meter():
    h=CoreRuntime().health()
    assert h["subscription_gate"] is False
    assert h["credit_meter"] is False
    assert h["online_ai_mode"] is True

def test_unknown_core_fails_closed():
    r=CoreRuntime().execute(CoreRequest("CORE-999","noop"))
    assert r.ok is False and r.status=="unknown_core"
