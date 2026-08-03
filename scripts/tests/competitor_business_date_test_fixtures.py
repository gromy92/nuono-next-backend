"""Small pure-data fixtures shared by competitor correction tests."""
from __future__ import annotations

from datetime import datetime


def snapshot(
    row_id,
    captured_at,
    *,
    updated_at=None,
    watch=10,
    title="Title",
    title_ar=None,
    tags=None,
    brand=None,
    price="10.00",
    deleted=0,
):
    captured = datetime.fromisoformat(captured_at)
    updated = datetime.fromisoformat(updated_at or captured_at)
    return {
        "id": row_id,
        "owner_user_id": 307,
        "watch_product_id": watch,
        "competitor_product_id": 20,
        "subject_type": "COMPETITOR",
        "site_code": "SA",
        "noon_product_code": "N123",
        "code_type": "N_CODE",
        "fact_date": captured.date().isoformat(),
        "captured_at": captured.isoformat(sep=" "),
        "title_en": title,
        "title_ar": title_ar,
        "badges_json": tags,
        "brand": brand,
        "price_amount": price,
        "currency_code": "SAR",
        "rating": "4.00",
        "review_count": 10,
        "main_image_asset_key": "a.jpg",
        "main_image_url_normalized": "https://cdn/a.jpg",
        "is_deleted": deleted,
        "created_by": 307,
        "updated_by": 307,
        "gmt_create": updated.isoformat(sep=" "),
        "gmt_updated": updated.isoformat(sep=" "),
    }


def event(row_id, snapshot_id, field_key, *, deleted=0):
    return {
        "id": row_id,
        "snapshot_id": snapshot_id,
        "previous_snapshot_id": snapshot_id - 1,
        "owner_user_id": 307,
        "watch_product_id": 10,
        "competitor_product_id": 20,
        "subject_type": "COMPETITOR",
        "site_code": "SA",
        "noon_product_code": "N123",
        "fact_date": "2026-07-28",
        "field_key": field_key,
        "field_label": "旧标签",
        "change_type": "VALUE_CHANGED",
        "old_value_json": '"old"',
        "new_value_json": '"new"',
        "severity": "INFO",
        "is_deleted": deleted,
        "created_by": 307,
        "updated_by": 307,
        "gmt_create": "2026-07-28 12:00:00",
        "gmt_updated": "2026-07-28 12:00:00",
    }


def rank(row_id=900000, run_id=700):
    return {
        "id": row_id,
        "watch_product_id": 10,
        "keyword_id": 800,
        "keyword_run_id": run_id,
        "search_run_id": 600,
        "fact_time": "2026-07-27 18:30:00",
        "fact_date": "2026-07-27",
        "updated_by": 307,
        "gmt_create": "2026-07-27 18:31:00",
        "gmt_updated": "2026-07-27 18:31:00",
    }


def keyword_run(run_id=700):
    return {
        "id": run_id,
        "search_run_id": 600,
        "keyword_id": 800,
        "captured_at": "2026-07-27 18:30:00",
        "is_deleted": 0,
        "updated_by": 307,
        "gmt_create": "2026-07-27 18:31:00",
        "gmt_updated": "2026-07-27 18:31:00",
    }


def sequence():
    return {
        "sequence_name": "operations_competitor_product_change_event",
        "next_id": 5000,
        "gmt_create": "2026-06-01 00:00:00",
        "gmt_updated": "2026-07-01 00:00:00",
    }
