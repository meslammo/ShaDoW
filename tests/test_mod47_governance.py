from shadow.governance import (
    Adapter, Command, Companion, ErrorKind, EvidenceKind, Governance, Identity,
    Intent, LearningItem, Source, State,
)


def test_identity_and_conflict_policy():
    g = Governance()
    assert g.authenticate(Identity("mohamed", voice_verified=True, passphrase_verified=True, master=True))
    a = Command("a", Intent("open", "open device"), priority=5, created_at=1)
    b = Command("b", Intent("close", "close device"), priority=5, created_at=2)
    assert g.choose_command([a, b]).id == "b"


def test_state_checkpoint_resume_and_rollback():
    g = Governance(); g.transition(State.EXECUTING)
    cp = g.checkpoint("c1", {"power": "on"})
    g.record_result(cp, {"power": "off"})
    assert g.rollback(cp) == {"power": "on"}
    assert g.resume() == State.EXECUTING


def test_intent_risk_verification_and_errors():
    intent = Governance.intent("build", "build APK", ["offline-safe"], "APK exists")
    assert intent.goal == "build APK"
    assert Governance.risk(80, 20).level == "high"
    assert Governance.verify({"ok": True}, {"ok": True})
    assert Governance.error(ErrorKind.PERMISSION, "denied")["kind"] == "permission"


def test_bounded_recovery_deadline_and_source_resolution():
    g = Governance(); g.recovery.max_attempts = 1
    assert g.attempt(0, progressed=True)
    assert not g.attempt(0, progressed=True)
    d = Governance.deadline(100, 50)
    assert d["remaining"] == 50
    source = Governance.resolve_sources([Source("old", 90, 10), Source("fresh", 70, 90)])
    assert source and source.name == "fresh"


def test_adapter_companion_learning_and_emergency_shutdown():
    assert Governance.adapter_ready(Adapter("home", "1", compatible=True, healthy=True))
    c = Governance.companion_transition(Companion("watch"), "trust")
    assert c.lifecycle == "trust"
    assert Governance.learn(LearningItem(EvidenceKind.EVIDENCE, "safe"))
    assert not Governance.learn(LearningItem(EvidenceKind.FACT, "secret", sensitive=True))
    g = Governance(); g.transition(State.EXECUTING)
    result = g.emergency_shutdown("unsafe state")
    assert result["safe"] and result["state"] == "paused"
