#!/usr/bin/env python3
"""Candidate-bound, fail-closed DP-06 campaign enumeration verifier."""
from __future__ import annotations

import argparse
import datetime as dt
import json
import re
import sys
import zipfile
from pathlib import Path

try:
    from scripts.dp06_campaign_probe_support import (
        ProbeFailure,
        canonical as _canonical,
        instant as _instant,
        parse_instant as _parse_instant,
        read_json as _read_json,
        require_private_file as _require_private_file,
        sha256 as _sha256,
        utc as _utc,
        write_new as _write_new,
    )
except ModuleNotFoundError:
    from dp06_campaign_probe_support import (  # type: ignore
        ProbeFailure,
        canonical as _canonical,
        instant as _instant,
        parse_instant as _parse_instant,
        read_json as _read_json,
        require_private_file as _require_private_file,
        sha256 as _sha256,
        utc as _utc,
        write_new as _write_new,
    )


CONTRACT_ENTRY = (
    "BOOT-INF/classes/META-INF/nuono/"
    "dp06-campaign-enumeration-native-contract-v1.json"
)
CONTRACT_SCHEMA = "nuono.dp06-campaign-enumeration-native-contract/v1"
CAPTURE_SCHEMA = "nuono.dp06-dashboard-boundary-capture/v1"
PROOF_SCHEMA = "nuono.dp06-complete-campaign-enumeration/v1"
ENDPOINT = "/_svc/productads/v2/noon/metrics"
ACTIVE = {"active", "enabled", "live", "on", "running"}
INACTIVE = {
    "archived", "completed", "deleted", "disabled", "draft", "ended", "inactive",
    "off", "paused", "rejected", "stopped",
}
HEX40 = re.compile(r"[0-9a-f]{40}")
HEX64 = re.compile(r"[0-9a-f]{64}")


def verify_probe(
        candidate_jar: Path,
        release_manifest: Path,
        capture_file: Path,
        evidence_file: Path,
        *,
        now: dt.datetime | None = None,
) -> dict[str, object]:
    candidate = Path(candidate_jar)
    contract, contract_payload = _load_contract(candidate)
    candidate_sha, commit = _verify_candidate(candidate, Path(release_manifest))
    _verify_contract(contract)
    if contract.get("status") != "PROVEN":
        raise ProbeFailure("DP06_UPSTREAM_AUTHORITY_UNPROVEN")
    _require_private_file(Path(capture_file))
    capture = _read_json(Path(capture_file), "DP06_CAPTURE_INVALID")
    clock = _utc(now or dt.datetime.now(dt.timezone.utc))
    captured_at = _verify_capture(capture, commit, candidate_sha, clock, contract)
    body = capture["response"]["body"]
    generation, provider_as_of, declared, complete = _authority(body, contract)
    campaigns = body.get("campaigns")
    metrics = body.get("current", {}).get("campaignMetrics")
    if not isinstance(campaigns, list) or not isinstance(metrics, dict):
        raise ProbeFailure("DP06_DASHBOARD_CONTAINER_INVALID")
    if not complete or declared != len(campaigns):
        raise ProbeFailure("DP06_CAMPAIGN_COUNT_UNPROVEN")
    if provider_as_of > captured_at + dt.timedelta(seconds=30) \
            or provider_as_of < captured_at - dt.timedelta(minutes=5):
        raise ProbeFailure("DP06_PROVIDER_AS_OF_INVALID")
    active_count = _active_campaign_count(campaigns)
    request = capture["dashboard_call"]["request_body"]
    proof = {
        "schema": PROOF_SCHEMA,
        "type": "DP06_COMPLETE_CAMPAIGN_ENUMERATION",
        "requirement": "DP06_COMPLETE_CAMPAIGN_ENUMERATION",
        "status": "CONTRACT_PROVEN",
        "source_kind": "PROVIDER_COMPLETE_CAMPAIGN_ENUMERATION",
        "source_identity_sha256": _sha256(contract_payload),
        "manifest_commit": commit,
        "candidate_jar_sha256": candidate_sha,
        "scope_sha256": capture["scope_sha256"],
        "report_date": request["startDate"],
        "dashboard_endpoint_sha256": _sha256(ENDPOINT.encode()),
        "dashboard_request_sha256": _sha256(_canonical(request)),
        "dashboard_response_sha256": _sha256(_canonical(body)),
        "provider_generation_sha256": _sha256(generation.encode()),
        "provider_as_of_utc": _instant(provider_as_of),
        "declared_campaign_count": declared,
        "active_campaign_count": active_count,
        "dashboard_call_count": 1,
        "verified_at": _instant(clock),
        "expires_at": _instant(clock + dt.timedelta(minutes=10)),
    }
    _write_new(Path(evidence_file), proof)
    return proof


def _load_contract(candidate_jar: Path) -> tuple[dict[str, object], bytes]:
    try:
        with zipfile.ZipFile(candidate_jar) as archive:
            payload = archive.read(CONTRACT_ENTRY)
        value = json.loads(payload)
    except (OSError, KeyError, ValueError, zipfile.BadZipFile) as failure:
        raise ProbeFailure("DP06_CANDIDATE_CONTRACT_INVALID") from failure
    if not isinstance(value, dict):
        raise ProbeFailure("DP06_CANDIDATE_CONTRACT_INVALID")
    return value, payload


def _verify_candidate(candidate: Path, manifest_path: Path) -> tuple[str, str]:
    manifest = _read_json(manifest_path, "DP06_RELEASE_MANIFEST_INVALID")
    commit = manifest.get("commit")
    if manifest.get("schema_version") != 1 or manifest.get("component") != "backend" \
            or manifest.get("deployable") is not True or not isinstance(commit, str) \
            or not HEX40.fullmatch(commit):
        raise ProbeFailure("DP06_RELEASE_MANIFEST_INVALID")
    files = manifest.get("files")
    if not isinstance(files, list):
        raise ProbeFailure("DP06_RELEASE_MANIFEST_INVALID")
    matches = [item for item in files if isinstance(item, dict)
               and item.get("path") == "nuono-next-backend.jar"]
    digest = _sha256(candidate.read_bytes())
    if len(matches) != 1 or matches[0].get("sha256") != digest \
            or matches[0].get("size") != candidate.stat().st_size:
        raise ProbeFailure("DP06_CANDIDATE_BINDING_MISMATCH")
    return digest, commit


def _verify_contract(contract: dict[str, object]) -> None:
    expected = {"schema", "status", "endpoint", "response_paths", "blockers"}
    if set(contract) != expected or contract.get("schema") != CONTRACT_SCHEMA \
            or contract.get("endpoint") != ENDPOINT \
            or contract.get("status") not in {"BLOCKED", "PROVEN"}:
        raise ProbeFailure("DP06_CANDIDATE_CONTRACT_INVALID")
    paths = contract.get("response_paths")
    path_names = {
        "generation_token", "provider_as_of_utc", "declared_campaign_count", "complete",
    }
    blockers = contract.get("blockers")
    if not isinstance(paths, dict) or set(paths) != path_names \
            or not isinstance(blockers, list) or not all(isinstance(v, str) for v in blockers):
        raise ProbeFailure("DP06_CANDIDATE_CONTRACT_INVALID")
    if contract["status"] == "PROVEN":
        values = list(paths.values())
        if blockers or len(set(values)) != len(values) or any(
                not isinstance(value, str) or not value.startswith("/")
                or value.startswith("/campaignCollectionAuthority") for value in values
        ):
            raise ProbeFailure("DP06_CANDIDATE_CONTRACT_INVALID")
    elif not blockers or any(paths.values()):
        raise ProbeFailure("DP06_CANDIDATE_CONTRACT_INVALID")


def _verify_capture(
        capture: dict[str, object],
        commit: str,
        candidate_sha: str,
        now: dt.datetime,
        contract: dict[str, object],
) -> dt.datetime:
    fields = {
        "schema", "manifest_commit", "candidate_jar_sha256", "captured_at",
        "scope_sha256", "dashboard_call", "response",
    }
    if set(capture) != fields or capture.get("schema") != CAPTURE_SCHEMA \
            or capture.get("manifest_commit") != commit \
            or capture.get("candidate_jar_sha256") != candidate_sha \
            or not isinstance(capture.get("scope_sha256"), str) \
            or not HEX64.fullmatch(capture["scope_sha256"]):
        raise ProbeFailure("DP06_CAPTURE_BINDING_MISMATCH")
    captured_at = _parse_instant(capture.get("captured_at"), "DP06_CAPTURE_TIME_INVALID")
    if captured_at > now + dt.timedelta(seconds=30) \
            or captured_at < now - dt.timedelta(minutes=5):
        raise ProbeFailure("DP06_CAPTURE_TIME_INVALID")
    call = capture.get("dashboard_call")
    response = capture.get("response")
    if not isinstance(call, dict) or set(call) != {"count", "method", "path", "request_body"} \
            or call.get("count") != 1 or call.get("method") != "POST" \
            or call.get("path") != contract["endpoint"]:
        raise ProbeFailure("DP06_DASHBOARD_CALL_CONTRACT_INVALID")
    request = call.get("request_body")
    if not isinstance(request, dict) or set(request) != {
        "startDate", "endDate", "campaignFilters", "isNamshi",
    } or request.get("startDate") != request.get("endDate") \
            or request.get("campaignFilters") != {} or request.get("isNamshi") is not False:
        raise ProbeFailure("DP06_DASHBOARD_REQUEST_INVALID")
    if not isinstance(response, dict) or set(response) != {"status", "content_type", "body"} \
            or response.get("status") != 200 \
            or response.get("content_type") != "application/json" \
            or not isinstance(response.get("body"), dict):
        raise ProbeFailure("DP06_DASHBOARD_RESPONSE_INVALID")
    if "campaignCollectionAuthority" in response["body"]:
        raise ProbeFailure("DP06_SYNTHETIC_AUTHORITY_REJECTED")
    return captured_at


def _authority(
        body: dict[str, object],
        contract: dict[str, object],
) -> tuple[str, dt.datetime, int, bool]:
    paths = contract["response_paths"]
    generation = _pointer(body, paths["generation_token"])
    as_of = _pointer(body, paths["provider_as_of_utc"])
    declared = _pointer(body, paths["declared_campaign_count"])
    complete = _pointer(body, paths["complete"])
    if not isinstance(generation, str) or not generation.strip() \
            or isinstance(declared, bool) or not isinstance(declared, int) or declared < 0 \
            or not isinstance(complete, bool):
        raise ProbeFailure("DP06_PROVIDER_AUTHORITY_INVALID")
    return generation, _parse_instant(as_of, "DP06_PROVIDER_AS_OF_INVALID"), declared, complete


def _pointer(root: object, pointer: str) -> object:
    value = root
    for raw in pointer.split("/")[1:]:
        key = raw.replace("~1", "/").replace("~0", "~")
        if not isinstance(value, dict) or key not in value:
            raise ProbeFailure("DP06_PROVIDER_AUTHORITY_MISSING")
        value = value[key]
    return value


def _active_campaign_count(campaigns: list[object]) -> int:
    identities: dict[str, bool] = {}
    for row in campaigns:
        if not isinstance(row, dict):
            raise ProbeFailure("DP06_CAMPAIGN_ROW_INVALID")
        code = row.get("campaignCode")
        status = row.get("effectiveStatus", row.get("status"))
        if not isinstance(code, str) or not code.strip() or not isinstance(status, str):
            raise ProbeFailure("DP06_CAMPAIGN_ROW_INVALID")
        normalized = status.strip().lower()
        if normalized not in ACTIVE | INACTIVE:
            raise ProbeFailure("DP06_CAMPAIGN_STATUS_UNKNOWN")
        active = normalized in ACTIVE
        if code in identities and identities[code] != active:
            raise ProbeFailure("DP06_CAMPAIGN_STATUS_DRIFT")
        identities.setdefault(code, active)
    return sum(identities.values())


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--candidate-jar", type=Path, required=True)
    parser.add_argument("--release-manifest", type=Path, required=True)
    parser.add_argument("--capture-file", type=Path, required=True)
    parser.add_argument("--evidence-file", type=Path, required=True)
    options = parser.parse_args(argv)
    try:
        verify_probe(
            options.candidate_jar,
            options.release_manifest,
            options.capture_file,
            options.evidence_file,
        )
        print("DP06_COMPLETE_CAMPAIGN_ENUMERATION=CONTRACT_PROVEN")
        return 0
    except ProbeFailure as failure:
        print(
            "DP06_COMPLETE_CAMPAIGN_ENUMERATION=FAIL:" + failure.code,
            file=sys.stderr,
        )
        return 22
    except Exception:
        print(
            "DP06_COMPLETE_CAMPAIGN_ENUMERATION=FAIL:DP06_PROBE_EXECUTION_FAILED",
            file=sys.stderr,
        )
        return 22


__all__ = ["ProbeFailure", "verify_probe"]


if __name__ == "__main__":
    raise SystemExit(main())
