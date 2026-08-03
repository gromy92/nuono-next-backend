"""Policy-level snapshot fixtures."""
from scripts.competitor_business_date.policy import (
    Snapshot,
    plan_daily_canonicalization,
)


def snapshot(
    snapshot_id,
    captured_at,
    *,
    clock="current",
    deleted=False,
    watch_product_id=10,
    subject_type="COMPETITOR",
    noon_product_code="N123",
    **values,
):
    return Snapshot(
        id=snapshot_id,
        watch_product_id=watch_product_id,
        subject_type=subject_type,
        noon_product_code=noon_product_code,
        captured_at=captured_at,
        clock=clock,
        is_deleted=deleted,
        values=values,
    )


def plan_by_id(*snapshots):
    return {
        item.snapshot.id: item
        for item in plan_daily_canonicalization(snapshots)
    }
