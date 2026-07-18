import importlib.util
import subprocess
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "source_size_gate.py"
SPEC = importlib.util.spec_from_file_location("source_size_gate", SCRIPT)
gate = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(gate)


class SourceSizeGatePolicyTest(unittest.TestCase):
    def test_bootstrap_requires_exact_ceiling_for_every_oversized_file(self):
        counts = {"src/main/java/Big.java": 301, "src/main/java/Small.java": 300}

        self.assertEqual(
            gate.policy_errors(counts, {"src/main/java/Big.java": 301}, {}, False),
            [],
        )
        errors = gate.policy_errors(counts, {}, {}, False)
        self.assertIn("missing bootstrap entry", errors[0])

    def test_bootstrap_rejects_new_debt_and_growth_against_base_sources(self):
        old = "src/main/java/Old.java"
        new = "src/main/java/New.java"
        counts = {old: 321, new: 301}
        baseline = {old: 321, new: 301}

        errors = gate.policy_errors(
            counts,
            baseline,
            {},
            False,
            {old: 320, new: None},
        )

        self.assertTrue(any("grew from 320 to 321" in error for error in errors))
        self.assertTrue(any("not eligible for bootstrap" in error for error in errors))

    def test_new_oversized_file_and_new_baseline_entry_both_fail(self):
        path = "src/main/java/New.java"
        missing_errors = gate.policy_errors({path: 301}, {}, {"Old.java": 400}, True)
        added_errors = gate.policy_errors({path: 301}, {path: 301}, {"Old.java": 400}, True)

        self.assertTrue(any("new oversized file" in error for error in missing_errors))
        self.assertTrue(any("baseline entry is new" in error for error in added_errors))

    def test_growth_and_ceiling_growth_fail(self):
        path = "src/main/java/Big.java"
        errors = gate.policy_errors({path: 321}, {path: 321}, {path: 320}, True)

        self.assertTrue(any("ceiling increased" in error for error in errors))

        errors = gate.policy_errors({path: 321}, {path: 320}, {path: 320}, True)
        self.assertTrue(any("grew to 321" in error for error in errors))

    def test_shrink_must_lower_ceiling_and_then_passes(self):
        path = "src/main/java/Big.java"
        errors = gate.policy_errors({path: 310}, {path: 320}, {path: 320}, True)
        self.assertTrue(any("lower its ceiling" in error for error in errors))

        self.assertEqual(gate.policy_errors({path: 310}, {path: 310}, {path: 320}, True), [])

    def test_small_or_deleted_file_must_leave_baseline(self):
        path = "src/main/java/Big.java"

        small = gate.policy_errors({path: 300}, {path: 320}, {path: 320}, True)
        deleted = gate.policy_errors({}, {path: 320}, {path: 320}, True)

        self.assertTrue(any("obsolete baseline" in error for error in small))
        self.assertTrue(any("deleted file still has baseline" in error for error in deleted))
        self.assertEqual(gate.policy_errors({path: 300}, {}, {path: 320}, True), [])
        self.assertEqual(gate.policy_errors({}, {}, {path: 320}, True), [])

    def test_rename_is_treated_as_new_debt(self):
        old = "src/main/java/Old.java"
        new = "src/main/java/New.java"
        errors = gate.policy_errors({new: 350}, {new: 350}, {old: 350}, True)

        self.assertTrue(any("baseline entry is new" in error for error in errors))

    def test_physical_line_count_includes_blank_and_comment_lines(self):
        self.assertEqual(gate.physical_line_count(b"code\n\n// comment\nlast"), 4)
        self.assertEqual(gate.physical_line_count(b""), 0)

    def test_baseline_parser_rejects_duplicates_and_small_ceilings(self):
        with self.assertRaises(gate.GateError):
            gate.parse_baseline_lines(["A.java\t301", "A.java\t302"], "fixture")
        with self.assertRaises(gate.GateError):
            gate.parse_baseline_lines(["A.java\t300"], "fixture")


class SourceSizeGateRepositoryTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.git("init", "-q")
        self.git("config", "user.email", "source-size@example.invalid")
        self.git("config", "user.name", "Source Size Test")

    def tearDown(self):
        self.temporary.cleanup()

    def git(self, *args):
        return subprocess.run(
            ["git", "-C", str(self.root), *args],
            check=True,
            capture_output=True,
        )

    def write_lines(self, relative, count):
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text("line\n" * count, encoding="utf-8")

    def test_scans_tracked_and_untracked_sources_but_excludes_generated_assets(self):
        self.write_lines("src/main/java/Tracked.java", 2)
        self.write_lines("src/test/java/Untracked.java", 3)
        self.write_lines("scripts/untracked-tool.mjs", 4)
        self.write_lines("Dockerfile.dev", 5)
        self.write_lines("Makefile", 6)
        self.write_lines(".env.local", 7)
        self.write_lines("docker-compose.yml", 8)
        tool = self.root / "scripts/shebang-tool"
        tool.write_text("#!/usr/bin/env python3\n" + "line\n" * 8, encoding="utf-8")
        self.write_lines("src/main/resources/static/assets/generated.js", 500)
        self.write_lines("notes.txt", 900)
        self.git("add", "src/main/java/Tracked.java", "src/main/resources/static/assets/generated.js")

        counts = gate.repository_source_counts(self.root)

        self.assertEqual(counts["src/main/java/Tracked.java"], 2)
        self.assertEqual(counts["src/test/java/Untracked.java"], 3)
        self.assertEqual(counts["scripts/untracked-tool.mjs"], 4)
        self.assertEqual(counts["Dockerfile.dev"], 5)
        self.assertEqual(counts["Makefile"], 6)
        self.assertEqual(counts[".env.local"], 7)
        self.assertEqual(counts["docker-compose.yml"], 8)
        self.assertEqual(counts["scripts/shebang-tool"], 9)
        self.assertNotIn("src/main/resources/static/assets/generated.js", counts)
        self.assertNotIn("notes.txt", counts)

    def test_base_without_baseline_enters_bootstrap_mode(self):
        self.write_lines("src/main/java/Big.java", 301)
        self.git("add", ".")
        self.git("commit", "-qm", "base")

        entries, exists = gate.baseline_at_ref(self.root, "HEAD")

        self.assertEqual(entries, {})
        self.assertFalse(exists)

    def test_reads_baseline_from_base_commit(self):
        self.write_lines("src/main/java/Big.java", 301)
        baseline = self.root / ".github/source-size-baseline/main.tsv"
        baseline.parent.mkdir(parents=True)
        baseline.write_text("src/main/java/Big.java\t301\n", encoding="utf-8")
        self.git("add", ".")
        self.git("commit", "-qm", "base")

        entries, exists = gate.baseline_at_ref(self.root, "HEAD")

        self.assertEqual(entries, {"src/main/java/Big.java": 301})
        self.assertTrue(exists)

    def test_rejects_a_baseline_shard_above_300_physical_lines(self):
        shard = self.root / ".github/source-size-baseline/main.tsv"
        shard.parent.mkdir(parents=True)
        shard.write_text("# baseline\n" * 301, encoding="utf-8")

        with self.assertRaisesRegex(gate.GateError, "baseline shard has 301 lines"):
            gate.current_baseline(self.root)


if __name__ == "__main__":
    unittest.main()
