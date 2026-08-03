"""Deterministic policy for repairing competitor snapshot business dates.

Snapshot ``values`` use database column names, for example ``title_en``,
``price_amount`` and ``main_image_asset_key``.  This module plans target
state only; it performs no database or filesystem writes.
"""
from collections import defaultdict
from dataclasses import dataclass, field
from datetime import date, datetime, timedelta
from decimal import Decimal, InvalidOperation
from typing import Any, Callable, Iterable, Mapping, Optional, Tuple

LEGACY_CLOCK = "legacy"
CURRENT_CLOCK = "current"
LEGACY_CONTRACT = "legacy"
LIST_V1_CONTRACT = "list_v1"

BusinessKey = Tuple[int, str, str]


@dataclass(frozen=True)
class Snapshot:
    """One source row plus its explicit historical clock classification."""

    id: int
    watch_product_id: int
    subject_type: str
    noon_product_code: str
    captured_at: datetime
    clock: str
    is_deleted: bool = False
    values: Mapping[str, Any] = field(default_factory=dict)

    @property
    def business_key(self) -> BusinessKey:
        return (
            self.watch_product_id,
            self.subject_type,
            self.noon_product_code,
        )


@dataclass(frozen=True)
class PlannedSnapshot:
    """Target date/deletion state for a source snapshot."""

    snapshot: Snapshot
    effective_captured_at: datetime
    fact_date: date
    is_deleted: bool
    canonical_snapshot_id: Optional[int]

    @property
    def is_canonical(self) -> bool:
        return (
            not self.is_deleted
            and self.canonical_snapshot_id == self.snapshot.id
        )


@dataclass(frozen=True)
class ExpectedEvent:
    """One field-level event expected on a canonical snapshot."""

    snapshot_id: int
    previous_snapshot_id: int
    fact_date: date
    field_key: str
    field_label: str
    severity: str
    old_value: Any
    new_value: Any
    change_type: str = "VALUE_CHANGED"


@dataclass(frozen=True)
class _Field:
    key: str
    label: str
    source: str
    kind: str = "text"
    severity: str = "INFO"


_CONTRACTS = {
    LEGACY_CONTRACT: (
        _Field("title", "标题", "title_en"),
        _Field("brand", "品牌", "brand"),
        _Field("price", "价格", "price_amount", "decimal", "WARNING"),
        _Field("currency", "币种", "currency_code"),
        _Field("rating", "评分", "rating", "decimal"),
        _Field("reviewCount", "评论数", "review_count", "decimal"),
        _Field("mainImage", "主图资产", "", "legacy_image"),
    ),
    LIST_V1_CONTRACT: (
        _Field("title", "标题", "title_en"),
        _Field("titleAr", "阿语标题", "title_ar"),
        _Field("tags", "标签", "badges_json"),
        _Field("price", "价格", "price_amount", "decimal", "WARNING"),
        _Field("currency", "币种", "currency_code"),
        _Field("mainImage", "主图资产", "", "list_image"),
    ),
}


def effective_captured_at(snapshot: Snapshot) -> datetime:
    """Return Shanghai wall-clock time without guessing classification."""

    if snapshot.clock == LEGACY_CLOCK:
        return snapshot.captured_at + timedelta(hours=8)
    if snapshot.clock == CURRENT_CLOCK:
        return snapshot.captured_at
    raise ValueError(
        f"unsupported snapshot clock classification: {snapshot.clock!r}"
    )


def plan_daily_canonicalization(
        snapshots: Iterable[Snapshot],
) -> Tuple[PlannedSnapshot, ...]:
    """Choose one active row for every corrected business-key/date group.

    Active rows are ordered by effective ``captured_at DESC, id DESC``.
    Non-winners are soft-deleted.  Rows already deleted remain deleted.
    The result is deterministic and ordered by snapshot id.
    """

    rows = tuple(snapshots)
    ids = [row.id for row in rows]
    if len(ids) != len(set(ids)):
        raise ValueError("snapshot ids must be unique")

    prepared = []
    groups = defaultdict(list)
    for row in rows:
        effective = effective_captured_at(row)
        item = (row, effective, effective.date())
        prepared.append(item)
        groups[(row.business_key, effective.date())].append(item)

    canonical_by_group = {}
    for group_key, items in groups.items():
        active = [item for item in items if not item[0].is_deleted]
        canonical_by_group[group_key] = (
            max(active, key=lambda item: (item[1], item[0].id))
            if active
            else None
        )

    planned = []
    for row, effective, fact_date in prepared:
        canonical = canonical_by_group[(row.business_key, fact_date)]
        canonical_id = canonical[0].id if canonical else None
        planned.append(
            PlannedSnapshot(
                snapshot=row,
                effective_captured_at=effective,
                fact_date=fact_date,
                is_deleted=(
                    row.is_deleted
                    or canonical_id is None
                    or row.id != canonical_id
                ),
                canonical_snapshot_id=canonical_id,
            )
        )
    return tuple(sorted(planned, key=lambda item: item.snapshot.id))


def build_expected_events(
    plans: Iterable[PlannedSnapshot],
    contract: str | Mapping[int, str] | Callable[[PlannedSnapshot], str],
) -> Tuple[ExpectedEvent, ...]:
    """Build the active event chain under explicit historical contracts.

    A single contract remains convenient for one-era fixtures.  Production
    correction passes an ID mapping or resolver so the policy active when each
    current snapshot was written governs that event without breaking the chain
    across a contract cutover.
    """

    series = defaultdict(list)
    for plan in plans:
        if plan.is_canonical:
            series[plan.snapshot.business_key].append(plan)

    events = []
    for business_key in sorted(series):
        ordered = sorted(
            series[business_key],
            key=lambda item: (
                item.effective_captured_at,
                item.snapshot.id,
            ),
        )
        for previous, current in zip(ordered, ordered[1:]):
            contract_name = _resolve_contract(contract, current)
            fields = _CONTRACTS.get(contract_name)
            if fields is None:
                raise ValueError(
                    f"unsupported event contract: {contract_name!r}"
                )
            for field_spec in fields:
                old_value = _field_value(previous.snapshot, field_spec)
                new_value = _field_value(current.snapshot, field_spec)
                if old_value == new_value:
                    continue
                events.append(
                    ExpectedEvent(
                        snapshot_id=current.snapshot.id,
                        previous_snapshot_id=previous.snapshot.id,
                        fact_date=current.fact_date,
                        field_key=field_spec.key,
                        field_label=field_spec.label,
                        severity=field_spec.severity,
                        old_value=old_value,
                        new_value=new_value,
                    )
                )
    return tuple(events)


def _resolve_contract(
    contract: str | Mapping[int, str] | Callable[[PlannedSnapshot], str],
    current: PlannedSnapshot,
) -> str:
    if isinstance(contract, str):
        return contract
    if callable(contract):
        return contract(current)
    try:
        return contract[current.snapshot.id]
    except KeyError as error:
        raise ValueError(
            f"missing event contract for snapshot {current.snapshot.id}"
        ) from error


def normalize_image_url(value: Any) -> Optional[str]:
    """Trim a URL and remove query/fragment identity noise."""

    normalized = _normalize_text(value)
    if normalized is None:
        return None
    query_index = normalized.find("?")
    if query_index >= 0:
        normalized = normalized[:query_index]
    fragment_index = normalized.find("#")
    if fragment_index >= 0:
        normalized = normalized[:fragment_index]
    return normalized or None


def _field_value(snapshot: Snapshot, spec: _Field) -> Any:
    if spec.kind == "text":
        return _normalize_text(snapshot.values.get(spec.source))
    if spec.kind == "decimal":
        return _normalize_decimal(snapshot.values.get(spec.source), spec.source)
    if spec.kind == "legacy_image":
        return _normalize_text(snapshot.values.get("main_image_asset_key"))
    if spec.kind == "list_image":
        asset_key = _normalize_text(
            snapshot.values.get("main_image_asset_key")
        )
        if asset_key is not None:
            return asset_key
        normalized_url = snapshot.values.get("main_image_url_normalized")
        if _normalize_text(normalized_url) is None:
            normalized_url = snapshot.values.get("main_image_url_raw")
        return normalize_image_url(normalized_url)
    raise AssertionError(f"unknown field kind: {spec.kind}")


def _normalize_text(value: Any) -> Optional[str]:
    if value is None:
        return None
    if not isinstance(value, str):
        raise TypeError(f"text value must be str or None, got {type(value)}")
    normalized = value.strip()
    return normalized or None


def _normalize_decimal(value: Any, field_name: str) -> Optional[Decimal]:
    if value is None:
        return None
    if isinstance(value, str):
        value = value.strip()
        if not value:
            return None
    try:
        normalized = value if isinstance(value, Decimal) else Decimal(str(value))
    except (InvalidOperation, ValueError) as error:
        raise ValueError(
            f"invalid decimal value for {field_name}: {value!r}"
        ) from error
    if not normalized.is_finite():
        raise ValueError(
            f"invalid decimal value for {field_name}: {value!r}"
        )
    return normalized
