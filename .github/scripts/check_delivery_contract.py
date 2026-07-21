#!/usr/bin/env python3
"""Validate the system-delivery contract in a pull request body."""

from __future__ import annotations

import argparse
import os
import re
import sys

DELIVERY_TYPES = {"USER_CAPABILITY", "HIDDEN_TECHNICAL_SLICE", "BUG_FIX"}
STATUSES = {"Defined", "Ready", "Implemented", "Integrated", "Accepted", "Released", "Observed", "Done"}
EXPOSURES = {"HIDDEN", "UNCHANGED", "EXPOSE_AFTER_RELEASE"}
P0_P1_STATES = {"NONE_OPEN", "ADDRESSES_P0_P1", "CONTAINED_WITH_EVIDENCE"}
BASE_FIELDS = (
    "Target issue",
    "Module",
    "Delivery type",
    "Status before",
    "Status after",
    "User-visible outcome",
    "Production exposure",
    "P0/P1 status",
    "Complete journey and evidence",
    "System responsibility map",
    "Authorization and data scope",
    "Failure and recovery",
    "Hidden or exposure mechanism",
    "Known gaps",
    "Configuration/migration/task dependencies",
    "Rollback",
    "Observation required after release",
)
VERIFICATION_ITEMS = (
    "Focused tests",
    "Compile/typecheck",
    "API or authenticated browser journey",
    "Critical failure and recovery path",
    "Authorization isolation and unchanged-data proof",
    "Docs and acceptance evidence updated",
)
CLAIMED_COMPLETE = {"Accepted", "Released", "Observed", "Done"}


def field(body: str, label: str) -> str | None:
    match = re.search(rf"^- {re.escape(label)}:\s*(.+?)\s*$", body, re.MULTILINE)
    return match.group(1).strip() if match else None


def missing_or_placeholder(value: str | None) -> bool:
    if not value:
        return True
    lowered = value.strip().lower()
    return value.startswith("<") or lowered in {"required", "todo", "tbd", "n/a", "na", "-"}


def checked(body: str, label: str) -> bool:
    return re.search(rf"^- \[[xX]\] {re.escape(label)}:", body, re.MULTILINE) is not None


def validate(body: str) -> list[str]:
    errors: list[str] = []
    required_sections = (
        "System delivery contract",
        "Capability integrity",
        "Bug integrity",
        "Verification",
        "Release and remaining work",
    )
    for section in required_sections:
        if f"## {section}" not in body:
            errors.append(f"missing section: {section}")

    values = {label: field(body, label) for label in BASE_FIELDS}
    for label, value in values.items():
        if missing_or_placeholder(value):
            errors.append(f"missing or placeholder field: {label}")

    target_issue = values["Target issue"] or ""
    if not re.search(r"(?:[\w.-]+/[\w.-]+)?#\d+|https://github\.com/.+/issues/\d+", target_issue):
        errors.append("Target issue must contain an issue reference such as owner/repo#123")

    delivery_type = values["Delivery type"]
    status_before = values["Status before"]
    status_after = values["Status after"]
    exposure = values["Production exposure"]
    p0_p1_status = values["P0/P1 status"]
    if delivery_type not in DELIVERY_TYPES:
        errors.append(f"invalid Delivery type: {delivery_type}")
    if status_before not in STATUSES:
        errors.append(f"invalid Status before: {status_before}")
    if status_after not in STATUSES:
        errors.append(f"invalid Status after: {status_after}")
    if exposure not in EXPOSURES:
        errors.append(f"invalid Production exposure: {exposure}")
    if p0_p1_status not in P0_P1_STATES:
        errors.append(f"invalid P0/P1 status: {p0_p1_status}")

    if delivery_type == "HIDDEN_TECHNICAL_SLICE":
        if exposure != "HIDDEN":
            errors.append("a hidden technical slice must use Production exposure: HIDDEN")
        if status_after not in {"Implemented", "Integrated"}:
            errors.append("a hidden technical slice cannot claim Accepted, Released, Observed, or Done")
        outcome = values["User-visible outcome"] or ""
        if "no user-visible outcome" not in outcome.lower():
            errors.append('a hidden technical slice must state "No user-visible outcome"')

    if delivery_type == "USER_CAPABILITY" and exposure == "EXPOSE_AFTER_RELEASE" and status_after not in CLAIMED_COMPLETE:
        errors.append("a capability marked EXPOSE_AFTER_RELEASE must reach at least Accepted")

    if delivery_type == "BUG_FIX":
        for label in ("Root cause", "Similar-surface audit", "Regression protection"):
            if missing_or_placeholder(field(body, label)):
                errors.append(f"BUG_FIX requires: {label}")

    if status_after in CLAIMED_COMPLETE:
        for item in VERIFICATION_ITEMS:
            if not checked(body, item):
                errors.append(f"{status_after} requires checked verification: {item}")

    return errors


def self_test() -> int:
    base = """## System delivery contract
- Target issue: owner/repo#123
- Module: Product Management
- Delivery type: HIDDEN_TECHNICAL_SLICE
- Status before: Ready
- Status after: Integrated
- User-visible outcome: No user-visible outcome
- Production exposure: HIDDEN
- P0/P1 status: NONE_OPEN
## Capability integrity
- Complete journey and evidence: Internal contract tests pass
- System responsibility map: Backend adapter only
- Authorization and data scope: Existing scope unchanged
- Failure and recovery: Existing behavior unchanged
- Hidden or exposure mechanism: No route or menu exposure
## Bug integrity
- Root cause: N/A
- Similar-surface audit: N/A
- Regression protection: N/A
## Verification
- [ ] Focused tests: pending
- [ ] Compile/typecheck: pending
- [ ] API or authenticated browser journey: not at Accepted
- [ ] Critical failure and recovery path: not at Accepted
- [ ] Authorization isolation and unchanged-data proof: not at Accepted
- [ ] Docs and acceptance evidence updated: pending
## Release and remaining work
- Known gaps: User capability remains incomplete and hidden
- Configuration/migration/task dependencies: None
- Rollback: Revert the PR
- Observation required after release: None while hidden
"""
    if validate(base):
        print("self-test failed: valid hidden slice was rejected", file=sys.stderr)
        return 1
    invalid = base.replace("Production exposure: HIDDEN", "Production exposure: UNCHANGED")
    if not validate(invalid):
        print("self-test failed: exposed hidden slice was accepted", file=sys.stderr)
        return 1
    accepted = (
        base.replace("HIDDEN_TECHNICAL_SLICE", "USER_CAPABILITY")
        .replace("Status after: Integrated", "Status after: Accepted")
        .replace("No user-visible outcome", "Operator can complete and read back the workflow")
        .replace("Production exposure: HIDDEN", "Production exposure: EXPOSE_AFTER_RELEASE")
        .replace("- [ ]", "- [x]")
    )
    if validate(accepted):
        print("self-test failed: accepted capability was rejected", file=sys.stderr)
        return 1
    unchecked = accepted.replace("- [x] Focused tests:", "- [ ] Focused tests:")
    if not validate(unchecked):
        print("self-test failed: accepted capability without verification was accepted", file=sys.stderr)
        return 1
    bug_fix = (
        accepted.replace("USER_CAPABILITY", "BUG_FIX")
        .replace("Production exposure: EXPOSE_AFTER_RELEASE", "Production exposure: UNCHANGED")
        .replace("Root cause: N/A", "Root cause: State transition omitted the failure branch")
        .replace("Similar-surface audit: N/A", "Similar-surface audit: All sibling transitions inspected")
        .replace("Regression protection: N/A", "Regression protection: Failure regression test added")
    )
    if validate(bug_fix):
        print("self-test failed: valid bug fix was rejected", file=sys.stderr)
        return 1
    incomplete_bug = bug_fix.replace("Root cause: State transition omitted the failure branch", "Root cause: TODO")
    if not validate(incomplete_bug):
        print("self-test failed: bug fix without root cause was accepted", file=sys.stderr)
        return 1
    print("delivery-contract self-test passed")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--body-file")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        return self_test()
    if args.body_file:
        with open(args.body_file, encoding="utf-8") as handle:
            body = handle.read()
    else:
        body = os.environ.get("PR_BODY", "")
    errors = validate(body)
    if errors:
        print("System delivery contract is incomplete:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1
    print("System delivery contract is valid")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
