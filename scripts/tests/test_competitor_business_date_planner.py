from __future__ import annotations

import sys
import unittest
from datetime import datetime
from pathlib import Path


SCRIPT_DIR = Path(__file__).parents[1]
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from competitor_business_date.classification import (  # noqa: E402
    AmbiguousClockError,
)
from competitor_business_date.planner import CorrectionPlanner  # noqa: E402
from scripts.tests.competitor_business_date_test_fixtures import (  # noqa: E402
    event,
    keyword_run,
    rank,
    sequence,
    snapshot,
)


class CompetitorBusinessDatePlannerTest(unittest.TestCase):
    def planner(self):
        return CorrectionPlanner(
            actor_user_id=307,
            correction_time=datetime(2026, 7, 31, 9),
        )

    def build(self, snapshots, events=(), ranks=None, runs=None):
        return self.planner().build(
            snapshots=snapshots,
            events=events,
            rank_facts=[rank()] if ranks is None else ranks,
            keyword_runs=[keyword_run()] if runs is None else runs,
            event_sequence=sequence(),
            run_id="competitor-date-20260731",
            fence_generation=7,
            source_fingerprint={"schema_sha256": "a" * 64},
        )

    def test_mixed_clock_collision_keeps_latest_complete_current_row(self):
        old = snapshot(
            100,
            "2026-07-26 18:00:00",
            title="Earlier",
        )
        legacy_collision = snapshot(
            101,
            "2026-07-27 18:00:00",
            title="Legacy",
            brand="Legacy brand",
        )
        current = snapshot(
            400000,
            "2026-07-28 21:00:00",
            updated_at="2026-07-30 09:00:00",
            title="Current",
            title_ar="الحالي",
            tags='["fast"]',
            brand="Current brand",
        )

        plan = self.build(
            [old, legacy_collision, current],
            events=[event(4900, 101, "brand")],
        )
        snapshot_changes = {
            int(change.primary_key): change
            for change in plan.changes
            if change.table_name
            == "operations_competitor_product_snapshot"
        }

        self.assertEqual(
            "2026-07-28 02:00:00",
            snapshot_changes[101].post["captured_at"],
        )
        self.assertEqual(1, snapshot_changes[101].post["is_deleted"])
        self.assertNotIn(400000, snapshot_changes)
        self.assertEqual("Legacy", snapshot_changes[101].post["title_en"])
        self.assertIn(
            "operations_competitor_search_result",
            plan.metadata["non_scope_tables"],
        )

    def test_rebuilds_mixed_event_contract_and_retires_obsolete_event(self):
        legacy_day = snapshot(
            100,
            "2026-07-26 18:00:00",
            title="Old",
            brand="A",
        )
        pre_list_current = snapshot(
            400000,
            "2026-07-28 21:00:00",
            updated_at="2026-07-28 21:01:00",
            title="Middle",
            brand="B",
        )
        list_current = snapshot(
            400001,
            "2026-07-30 09:00:00",
            updated_at="2026-07-30 09:01:00",
            title="New",
            title_ar="جديد",
            tags='["new"]',
            brand="C",
        )
        obsolete = event(4900, 400001, "brand")

        plan = self.build(
            [legacy_day, pre_list_current, list_current],
            events=[obsolete],
        )
        event_changes = [
            change
            for change in plan.changes
            if change.table_name
            == "operations_competitor_product_change_event"
        ]
        active_posts = [
            change.post
            for change in event_changes
            if change.post["is_deleted"] == 0
        ]

        self.assertIn(
            (400000, "brand"),
            {(row["snapshot_id"], row["field_key"]) for row in active_posts},
        )
        self.assertIn(
            (400001, "titleAr"),
            {(row["snapshot_id"], row["field_key"]) for row in active_posts},
        )
        source_create_times = {
            row["id"]: row["gmt_create"]
            for row in (legacy_day, pre_list_current, list_current)
        }
        for inserted in (
            change for change in event_changes if change.action == "INSERT"
        ):
            self.assertEqual(
                source_create_times[inserted.post["snapshot_id"]],
                inserted.post["gmt_create"],
            )
        self.assertNotIn(
            (400001, "brand"),
            {(row["snapshot_id"], row["field_key"]) for row in active_posts},
        )
        retired = next(
            change for change in event_changes if change.primary_key == "4900"
        )
        self.assertEqual(1, retired.post["is_deleted"])
        sequence_change = next(
            change
            for change in plan.changes
            if change.group_kind == "event_sequence"
        )
        self.assertGreater(sequence_change.post["next_id"], 5000)

    def test_retires_event_whose_deleted_snapshot_is_outside_active_scope(self):
        orphaned_from_active_chain = event(4900, 99, "brand")

        plan = self.build(
            [snapshot(100, "2026-07-26 18:00:00")],
            events=[orphaned_from_active_chain],
        )

        retired = next(
            change for change in plan.changes if change.primary_key == "4900"
        )
        self.assertEqual("snapshot_chain", retired.group_kind)
        self.assertEqual("10|COMPETITOR|N123", retired.group_key)
        self.assertEqual(1, retired.post["is_deleted"])

    def test_rank_and_parent_move_atomically_by_keyword_run(self):
        plan = self.build(
            [snapshot(100, "2026-07-26 18:00:00")],
            ranks=[rank()],
            runs=[keyword_run()],
        )
        group = plan.changes_for_group("rank_run", "700")

        self.assertEqual(2, len(group))
        self.assertEqual(
            "2026-07-28 02:30:00",
            next(
                change.post["captured_at"]
                for change in group
                if change.table_name.endswith("keyword_run")
            ),
        )
        fact = next(
            change.post
            for change in group
            if change.table_name.endswith("rank_fact")
        )
        self.assertEqual("2026-07-28 02:30:00", fact["fact_time"])
        self.assertEqual("2026-07-28", fact["fact_date"])

    def test_ambiguous_post_cutover_update_fails_closed(self):
        ambiguous = snapshot(
            100,
            "2026-07-26 18:00:00",
            updated_at="2026-07-29 09:00:00",
        )

        with self.assertRaisesRegex(AmbiguousClockError, "ambiguous"):
            self.build([ambiguous])

    def test_bounded_row_that_looks_current_still_requires_override(self):
        bounded = snapshot(
            100,
            "2026-07-29 09:00:00",
            updated_at="2026-07-29 09:01:00",
        )

        with self.assertRaisesRegex(AmbiguousClockError, "audited override"):
            self.build([bounded])

    def test_parent_rank_mismatch_fails_closed(self):
        parent = keyword_run()
        parent["captured_at"] = "2026-07-27 18:31:00"

        with self.assertRaisesRegex(ValueError, "differs"):
            self.build(
                [snapshot(100, "2026-07-26 18:00:00")],
                ranks=[rank()],
                runs=[parent],
            )

    def test_deleted_or_identity_mismatched_rank_parent_fails_closed(self):
        deleted = keyword_run()
        deleted["is_deleted"] = 1
        with self.assertRaisesRegex(ValueError, "is deleted"):
            self.build(
                [snapshot(100, "2026-07-26 18:00:00")],
                ranks=[rank()],
                runs=[deleted],
            )

        wrong_identity = rank()
        wrong_identity["search_run_id"] = 999
        with self.assertRaisesRegex(ValueError, "search_run_id differs"):
            self.build(
                [snapshot(100, "2026-07-26 18:00:00")],
                ranks=[wrong_identity],
                runs=[keyword_run()],
            )


if __name__ == "__main__":
    unittest.main()
