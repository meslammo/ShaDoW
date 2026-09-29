import os
import pytest

from shadow.supernice.online_brain import RealOnlineBrainAdapter
from shadow.supernice.orchestrator import Unified150Orchestrator


class StubBrain:
    def run(self, request, *, context=None, **_kwargs):
        return {
            "answer": "online stub response",
            "provider": "stub",
            "model": "test",
            "verified": True,
            "actions": [],
        }


class FailingBrain:
    def run(self, request, *, context=None, **_kwargs):
        return {
            "answer": "provider failed",
            "provider": "openai",
            "model": "test",
            "online_error": "provider_not_configured",
            "verified": False,
            "actions": [],
        }


def test_real_online_brain_adapter_rejects_provider_failure():
    adapter = RealOnlineBrainAdapter(FailingBrain())
    with pytest.raises(RuntimeError, match="provider_not_configured"):
        adapter("hello", {})


def test_unified_orchestrator_uses_real_online_adapter_interface(tmp_path):
    adapter = RealOnlineBrainAdapter(StubBrain())
    orchestrator = Unified150Orchestrator(str(tmp_path), online_brain=adapter)

    result = orchestrator.run("اكتبلي خطة للمشروع", confirmed=True)

    assert result.ok is True
    assert result.status == "completed"
    assert result.answer == "online stub response"
    assert result.metadata["online_only"] is True
    assert any(
        event.stage == "online_brain"
        and event.status == "completed"
        and event.detail == "stub"
        for event in result.events
    )


@pytest.mark.skipif(
    os.getenv("SHADOW_E2E_REAL") != "1"
    or not os.getenv("OPENAI_API_KEY", "").strip(),
    reason="set SHADOW_E2E_REAL=1 and OPENAI_API_KEY to run the real provider E2E",
)
def test_real_online_brain_e2e(tmp_path):
    orchestrator = Unified150Orchestrator(str(tmp_path))
    result = orchestrator.run(
        "Respond with exactly: SHADOW ONLINE E2E OK",
        confirmed=True,
    )

    assert result.ok is True
    assert result.status == "completed"
    assert "SHADOW ONLINE E2E OK" in result.answer
    stages = [(e.stage, e.status) for e in result.events]
    assert ("online_brain", "completed") in stages
    assert ("execute", "executed") in stages
    assert ("verify", "executed") in stages
    assert ("audit", "executed") in stages
