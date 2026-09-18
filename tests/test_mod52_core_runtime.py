import unittest

from shadow.core import GovernanceRuntime, Intent, Risk, ShadowOrchestrator, State


class TestMod52CoreRuntime(unittest.TestCase):
    def test_risk_gate_fails_closed_without_auth(self):
        gov = GovernanceRuntime()
        decision = gov.decide("github write", authenticated=False, authorized=False)
        self.assertFalse(decision.allowed)
        self.assertTrue(decision.confirmation_required)
        self.assertEqual(decision.risk, Risk.HIGH)

    def test_low_risk_executes_and_verifies(self):
        gov = GovernanceRuntime()
        runtime = ShadowOrchestrator(gov)
        task = runtime.submit("t1", Intent("calculator", "calculate", expected_result="4"))
        result = runtime.run(
            "t1",
            authenticated=True,
            authorized=True,
            executor=lambda intent: 4,
            verifier=lambda intent, result: result == 4,
        )
        self.assertEqual(result.state, State.DONE)
        self.assertEqual(result.result, 4)

    def test_verification_failure_is_bounded(self):
        gov = GovernanceRuntime(retry_budget=1, time_budget_s=10)
        runtime = ShadowOrchestrator(gov)
        runtime.submit("t2", Intent("test", "verify"))
        result = runtime.run(
            "t2",
            authenticated=True,
            authorized=True,
            executor=lambda intent: "bad",
            verifier=lambda intent, result: False,
        )
        self.assertEqual(result.state, State.FAILED)
        self.assertGreaterEqual(result.attempts, 1)


if __name__ == "__main__":
    unittest.main()
