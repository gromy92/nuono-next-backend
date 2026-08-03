import unittest
from datetime import date, datetime
from decimal import Decimal

from scripts.competitor_business_date.policy import (
    build_expected_events,
    effective_captured_at,
    plan_daily_canonicalization,
)
from scripts.tests.competitor_policy_test_fixtures import plan_by_id, snapshot


class EffectiveBusinessTimeTest(unittest.TestCase):
    def test_legacy_crosses_midnight_but_current_is_unchanged(self):
        legacy = snapshot(
            1,
            datetime(2026, 7, 27, 18, 30, 15, 123456),
            clock="legacy",
        )
        current = snapshot(
            2,
            datetime(2026, 7, 28, 2, 30, 15, 123456),
        )

        self.assertEqual(
            datetime(2026, 7, 28, 2, 30, 15, 123456),
            effective_captured_at(legacy),
        )
        self.assertEqual(current.captured_at, effective_captured_at(current))

    def test_unknown_clock_classification_fails_closed(self):
        row = snapshot(1, datetime(2026, 7, 28, 1), clock="ambiguous")

        with self.assertRaisesRegex(ValueError, "clock"):
            effective_captured_at(row)


class DailyCanonicalizationTest(unittest.TestCase):
    def test_legacy_and_current_collision_keeps_latest_active_row(self):
        legacy = snapshot(
            101,
            datetime(2026, 7, 27, 18),
            clock="legacy",
            title_en="legacy",
        )
        current = snapshot(
            102,
            datetime(2026, 7, 28, 3),
            title_en="current",
        )
        already_deleted = snapshot(
            103,
            datetime(2026, 7, 28, 4),
            deleted=True,
            title_en="deleted",
        )

        planned = plan_by_id(legacy, current, already_deleted)

        self.assertEqual(date(2026, 7, 28), planned[101].fact_date)
        self.assertTrue(planned[101].is_deleted)
        self.assertFalse(planned[101].is_canonical)
        self.assertFalse(planned[102].is_deleted)
        self.assertTrue(planned[102].is_canonical)
        self.assertEqual(102, planned[101].canonical_snapshot_id)
        self.assertEqual(102, planned[103].canonical_snapshot_id)
        self.assertTrue(planned[103].is_deleted)
        self.assertFalse(planned[103].is_canonical)

    def test_equal_time_uses_highest_id_and_preserves_whole_latest_row(self):
        captured = datetime(2026, 7, 27, 18)
        old = snapshot(
            201,
            captured,
            clock="legacy",
            title_en="old",
            brand="old brand",
            price_amount="10",
        )
        latest = snapshot(
            202,
            captured,
            clock="legacy",
            title_en="new",
            brand="new brand",
            price_amount="12",
        )

        planned = plan_by_id(old, latest)

        self.assertTrue(planned[201].is_deleted)
        self.assertTrue(planned[202].is_canonical)
        self.assertEqual(
            {
                "title_en": "new",
                "brand": "new brand",
                "price_amount": "12",
            },
            dict(planned[202].snapshot.values),
        )

    def test_groups_are_isolated_by_complete_business_key(self):
        captured = datetime(2026, 7, 28, 8)
        rows = (
            snapshot(1, captured, watch_product_id=1),
            snapshot(2, captured, watch_product_id=2),
            snapshot(3, captured, subject_type="SELF"),
            snapshot(4, captured, noon_product_code="N999"),
        )

        planned = plan_by_id(*rows)

        self.assertTrue(all(item.is_canonical for item in planned.values()))


class ExpectedEventsTest(unittest.TestCase):
    def test_first_snapshot_has_no_events(self):
        plans = plan_daily_canonicalization(
            [snapshot(1, datetime(2026, 7, 28, 8), title_en="only")]
        )

        self.assertEqual((), build_expected_events(plans, "list_v1"))

    def test_legacy_and_list_v1_contracts_are_explicit_and_distinct(self):
        previous = snapshot(
            1,
            datetime(2026, 7, 27, 8),
            title_en="Old",
            title_ar="قديم",
            badges_json='["old"]',
            brand="Brand A",
            price_amount="10.00",
            currency_code="AED",
            rating="4.1",
            review_count=8,
            main_image_asset_key="old.jpg",
        )
        current = snapshot(
            2,
            datetime(2026, 7, 28, 8),
            title_en="New",
            title_ar="جديد",
            badges_json='["new"]',
            brand="Brand B",
            price_amount="12",
            currency_code="SAR",
            rating="4.5",
            review_count=9,
            main_image_asset_key="new.jpg",
        )
        plans = plan_daily_canonicalization([previous, current])

        legacy = build_expected_events(plans, "legacy")
        list_v1 = build_expected_events(plans, "list_v1")

        self.assertEqual(
            {
                "title",
                "brand",
                "price",
                "currency",
                "rating",
                "reviewCount",
                "mainImage",
            },
            {event.field_key for event in legacy},
        )
        self.assertEqual(
            {"title", "titleAr", "tags", "price", "currency", "mainImage"},
            {event.field_key for event in list_v1},
        )
        price = next(event for event in list_v1 if event.field_key == "price")
        self.assertEqual(Decimal("10.00"), price.old_value)
        self.assertEqual(Decimal("12"), price.new_value)
        self.assertEqual("WARNING", price.severity)

    def test_list_image_falls_back_to_normalized_url_but_legacy_does_not(self):
        previous = snapshot(
            1,
            datetime(2026, 7, 27, 8),
            main_image_asset_key=" ",
            main_image_url_normalized=" https://cdn/a.jpg?x=1 ",
        )
        query_only = snapshot(
            2,
            datetime(2026, 7, 28, 8),
            main_image_url_raw="https://cdn/a.jpg#other",
        )
        changed = snapshot(
            3,
            datetime(2026, 7, 29, 8),
            main_image_url_raw="https://cdn/b.jpg?size=large#hero",
        )
        plans = plan_daily_canonicalization([previous, query_only, changed])

        legacy = build_expected_events(plans, "legacy")
        list_v1 = build_expected_events(plans, "list_v1")

        self.assertNotIn("mainImage", {event.field_key for event in legacy})
        image_events = [
            event for event in list_v1 if event.field_key == "mainImage"
        ]
        self.assertEqual(1, len(image_events))
        self.assertEqual("https://cdn/a.jpg", image_events[0].old_value)
        self.assertEqual("https://cdn/b.jpg", image_events[0].new_value)

    def test_decimal_scale_and_text_whitespace_do_not_create_changes(self):
        previous = snapshot(
            1,
            datetime(2026, 7, 27, 8),
            title_en=" Same ",
            title_ar=" ",
            badges_json=None,
            price_amount=Decimal("10.0000"),
            currency_code=" AED ",
        )
        equivalent = snapshot(
            2,
            datetime(2026, 7, 28, 8),
            title_en="Same",
            title_ar=None,
            badges_json="   ",
            price_amount="10",
            currency_code="AED",
        )
        case_changed = snapshot(
            3,
            datetime(2026, 7, 29, 8),
            title_en="same",
            price_amount="10.0",
            currency_code="AED",
        )
        events = build_expected_events(
            plan_daily_canonicalization(
                [previous, equivalent, case_changed]
            ),
            "list_v1",
        )

        self.assertEqual(["title"], [event.field_key for event in events])
        self.assertEqual("Same", events[0].old_value)
        self.assertEqual("same", events[0].new_value)

    def test_unknown_contract_fails_closed(self):
        plans = plan_daily_canonicalization(
            [
                snapshot(1, datetime(2026, 7, 27, 8)),
                snapshot(2, datetime(2026, 7, 28, 8), title_en="changed"),
            ]
        )
        with self.assertRaisesRegex(ValueError, "contract"):
            build_expected_events(plans, "latest")

    def test_contract_can_change_without_breaking_the_snapshot_chain(self):
        plans = plan_daily_canonicalization(
            [
                snapshot(1, datetime(2026, 7, 27, 8), brand="A"),
                snapshot(2, datetime(2026, 7, 28, 8), brand="B"),
                snapshot(
                    3,
                    datetime(2026, 7, 29, 18),
                    brand="C",
                    title_ar="جديد",
                ),
            ]
        )

        events = build_expected_events(
            plans,
            {2: "legacy", 3: "list_v1"},
        )

        self.assertIn(
            (2, "brand"),
            {(event.snapshot_id, event.field_key) for event in events},
        )
        self.assertIn(
            (3, "titleAr"),
            {(event.snapshot_id, event.field_key) for event in events},
        )
        self.assertNotIn(
            (3, "brand"),
            {(event.snapshot_id, event.field_key) for event in events},
        )


if __name__ == "__main__":
    unittest.main()
