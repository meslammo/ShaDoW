from shadow.runtime.action_executor import ActionExecutor


def test_unknown_action_fails_closed():
    r = ActionExecutor().execute("unknown.capability")
    assert not r.success
    assert r.requires_confirmation


def test_confirmed_registered_action_executes():
    ex = ActionExecutor()
    ex.register("app.launch", lambda: "launched")
    r = ex.execute("app.launch", confirmed=True)
    assert r.success
    assert r.output == "launched"


def test_registered_action_requires_confirmation():
    ex = ActionExecutor()
    ex.register("message.send", lambda: "sent")
    r = ex.execute("message.send")
    assert not r.success
    assert r.requires_confirmation
