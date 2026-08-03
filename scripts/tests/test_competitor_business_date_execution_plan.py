import hashlib
import sys
import unittest
from pathlib import Path
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "scripts"))

from competitor_business_date.apply_sql import APPLY  # noqa: E402
from competitor_business_date.apply_validation import BatchDescriptor  # noqa: E402
from competitor_business_date.execution_plan import (  # noqa: E402
    ExecutionPlanError,
    execution_plan_digest,
    prevalidate_batches,
    resolve_max_sql_bytes,
)
from competitor_business_date.manifest import ManifestChange  # noqa: E402


def _change(ordinal, kind, key):
    return ManifestChange(
        ordinal, kind, key, "table", str(ordinal), "UPDATE",
        {"id": ordinal}, {"id": ordinal}, "a" * 64, "b" * 64,
    )


def _validate(changes, direction):
    rows = tuple(changes)
    kind = rows[0].group_kind
    keys = tuple(dict.fromkeys(row.group_key for row in rows))
    digest = hashlib.sha256(f"{kind}|{'|'.join(keys)}".encode()).hexdigest()
    return BatchDescriptor(kind, keys, digest, len(rows))


def _sql(changes, *, direction):
    return "x" * (20 * len(tuple(changes)))


class ExecutionPlanTest(unittest.TestCase):
    def test_packs_complete_groups_by_kind_and_keeps_sequence_alone(self):
        rows = {
            ("event_sequence", "event"): (_change(4, "event_sequence", "event"),),
            ("snapshot_chain", "chain"): (_change(3, "snapshot_chain", "chain"),),
            ("rank_run", "10"): (_change(1, "rank_run", "10"),),
            ("rank_run", "20"): (_change(2, "rank_run", "20"),),
        }
        groups = tuple(rows)
        with (
            patch(
                "competitor_business_date.execution_plan.read_group",
                side_effect=lambda _, kind, key, __: rows[(kind, key)],
            ),
            patch(
                "competitor_business_date.execution_plan.validate_batch",
                side_effect=_validate,
            ),
            patch(
                "competitor_business_date.execution_plan.build_batch_sql",
                side_effect=_sql,
            ),
        ):
            batches, total, max_group, max_group_sql = prevalidate_batches(
                object(),
                groups,
                batch_size=5,
                max_sql_bytes=50,
                direction=APPLY,
            )

        self.assertEqual(3, len(batches))
        self.assertEqual(("10", "20"), batches[-1].descriptor.group_keys)
        self.assertEqual([20, 20, 40], [batch.sql_bytes for batch in batches])
        self.assertEqual((4, 1, 20), (total, max_group, max_group_sql))
        self.assertEqual(64, len(execution_plan_digest(batches)))

    def test_single_group_sql_over_limit_fails_before_descriptors_return(self):
        row = (_change(1, "rank_run", "10"),)
        with (
            patch(
                "competitor_business_date.execution_plan.read_group",
                return_value=row,
            ),
            patch(
                "competitor_business_date.execution_plan.validate_batch",
                side_effect=_validate,
            ),
            patch(
                "competitor_business_date.execution_plan.build_batch_sql",
                side_effect=_sql,
            ),
        ):
            with self.assertRaisesRegex(ExecutionPlanError, "cannot be split"):
                prevalidate_batches(
                    object(),
                    (("rank_run", "10"),),
                    batch_size=5,
                    max_sql_bytes=10,
                    direction=APPLY,
                )

    def test_sql_limit_defaults_from_packet_and_only_allows_tightening(self):
        metadata = {
            "database_identity": {"max_allowed_packet": 64 * 1024 * 1024}
        }
        self.assertEqual(16 * 1024 * 1024, resolve_max_sql_bytes(metadata, None))
        self.assertEqual(8 * 1024 * 1024, resolve_max_sql_bytes(
            metadata, 8 * 1024 * 1024
        ))
        with self.assertRaisesRegex(ExecutionPlanError, "safe limit"):
            resolve_max_sql_bytes(metadata, 17 * 1024 * 1024)
        with self.assertRaisesRegex(ExecutionPlanError, "packet"):
            resolve_max_sql_bytes({}, None)


if __name__ == "__main__":
    unittest.main()
