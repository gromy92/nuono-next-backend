import io
import sys
import unittest
from pathlib import Path
from unittest.mock import patch

SCRIPT_DIR = Path(__file__).parents[1]
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from competitor_business_date.governance import MysqlAdvisoryLock  # noqa: E402


class _FakeInput(io.StringIO):
    def __init__(self):
        super().__init__()
        self.was_closed = False

    def close(self):
        self.was_closed = True


class _FakeProcess:
    def __init__(self):
        self.stdin = _FakeInput()
        self.stdout = io.StringIO("LOCKED|41\nRELEASED|41\n")
        self.returncode = None
        self.terminated = False

    def poll(self):
        return self.returncode

    def wait(self, timeout=None):
        self.returncode = 0
        return 0

    def terminate(self):
        self.terminated = True
        self.returncode = -15

    def kill(self):
        self.returncode = -9


class _FakeMysql:
    command = ["mysql", "--defaults-file=/private/test.cnf", "--database=test"]


class MysqlAdvisoryLockTest(unittest.TestCase):
    def test_close_releases_lock_on_the_same_live_session(self):
        process = _FakeProcess()
        with patch("competitor_business_date.governance.subprocess.Popen", return_value=process):
            lock = MysqlAdvisoryLock(_FakeMysql())
            lock.__enter__()

            self.assertFalse(process.stdin.was_closed)
            lock.close()

        sent = process.stdin.getvalue()
        self.assertIn("GET_LOCK", sent)
        self.assertNotIn("SLEEP", sent)
        self.assertIn("RELEASE_LOCK", sent)
        self.assertTrue(process.stdin.was_closed)
        self.assertFalse(process.terminated)
        self.assertIsNone(lock.process)


if __name__ == "__main__":
    unittest.main()
