import datetime as dt
import unittest

from scripts.tests.test_release_dp10_openapi_probe import ReleaseDp10OpenApiProbeTest


class ReleaseDp10AuthWaitIsolationTest(unittest.TestCase):
    def test_fresh_gate_accepts_exact_isolation_but_rejects_mixed_claims(self):
        harness = ReleaseDp10OpenApiProbeTest(methodName="runTest")
        now = dt.datetime.now(dt.timezone.utc)
        isolated = harness.probe_evidence(now - dt.timedelta(minutes=1))
        isolated["release_disposition"] = "AUTH_WAIT_ISOLATED"
        isolated["current_list_contract"] = "NOT_EXECUTED_AUTH_WAIT"
        isolated["history_list_contract"] = "NOT_EXECUTED_AUTH_WAIT"
        isolated["detail_contract"] = "NOT_EXECUTED_AUTH_WAIT"
        mixed = dict(isolated)
        mixed["detail_contract"] = "CONTRACT_PROVEN"

        self.assertEqual(0, harness.verify_evidence(isolated).returncode)
        self.assertNotEqual(0, harness.verify_evidence(mixed).returncode)


if __name__ == "__main__":
    unittest.main()
