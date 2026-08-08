#!/usr/bin/env python3
"""Fail-closed, read-only Noon report download transport probe."""
from __future__ import annotations


def build_dp_report_download_probe_shell() -> str:
    return r'''
assert_dp_report_probe_marker() {
  [ "$(secure_file_operation verify "$STAGED_JAR" "600,640,644" \
    "$EXPECTED_JAR_SHA256")" = "$EXPECTED_JAR_SHA256" ]
  python3 - "$STAGED_JAR" <<'PY'
import sys, zipfile
markers = {
    "BOOT-INF/classes/META-INF/nuono/dp-report-download-probe-v1": (
        b"NUONO_DP_REPORT_DOWNLOAD_PROBE_V1\n",
        "candidate Jar report download probe marker mismatch",
    ),
    "BOOT-INF/classes/META-INF/nuono/dp-report-validator-rejection-v1": (
        b"NUONO_DP_REPORT_VALIDATOR_REJECTION_V1\n",
        "candidate Jar validator rejection marker mismatch",
    ),
}
with zipfile.ZipFile(sys.argv[1]) as archive:
    for marker, (expected, error) in markers.items():
        if archive.read(marker) != expected:
            raise SystemExit(error)
PY
}
verify_dp_report_probe_json() {
  [ "$(secure_file_operation verify "$DP_REPORT_PROBE_EVIDENCE_FILE" 600 \
    "$DP_REPORT_PROBE_EVIDENCE_SHA256")" = "$DP_REPORT_PROBE_EVIDENCE_SHA256" ]
  python3 - "$DP_REPORT_PROBE_EVIDENCE_FILE" "$DP_REPORT_PROBE_SOURCE_FILE" \
    "$EXPECTED_COMMIT" "$EXPECTED_JAR_SHA256" "$DP_REPORT_PROBE_NONCE_SHA256" <<'PY'
import datetime as dt, hashlib, json, pathlib, re, sys
evidence_path, source_path = map(pathlib.Path, sys.argv[1:3])
commit, jar_sha, nonce_sha = sys.argv[3:]
fields = {
    "schema", "type", "nonce_sha256", "manifest_commit",
    "candidate_jar_sha256", "source_url_sha256", "final_endpoint_sha256",
    "validator_kind", "validator_sha256", "range_contract",
    "matching_if_range_contract", "stale_if_range_contract",
    "initial_status", "matching_status", "stale_status", "total_length",
    "verified_at", "expires_at",
}
data = json.loads(evidence_path.read_bytes())
if set(data) != fields or any(not isinstance(value, str) for value in data.values()):
    raise SystemExit("report transport evidence schema mismatch")
if data["schema"] != "nuono.dp-report-download-transport/v2":
    raise SystemExit("report transport evidence version mismatch")
if data["type"] != "DP_REPORT_DOWNLOAD_TRANSPORT_CONTRACT":
    raise SystemExit("report transport evidence type mismatch")
if data["manifest_commit"] != commit or data["candidate_jar_sha256"] != jar_sha:
    raise SystemExit("report transport candidate binding mismatch")
if data["nonce_sha256"] != nonce_sha:
    raise SystemExit("report transport nonce mismatch")
for name in ("nonce_sha256", "candidate_jar_sha256", "source_url_sha256",
             "final_endpoint_sha256", "validator_sha256"):
    if not re.fullmatch(r"[0-9a-f]{64}", data[name]):
        raise SystemExit("report transport fingerprint invalid")
raw_source = source_path.read_text(encoding="utf-8")
lines = raw_source.splitlines()
if len(lines) != 1 or not lines[0] or lines[0] != lines[0].strip():
    raise SystemExit("report transport source URL file invalid")
if hashlib.sha256(lines[0].encode()).hexdigest() != data["source_url_sha256"]:
    raise SystemExit("report transport source URL binding mismatch")
if data["validator_kind"] not in {"STRONG_ETAG", "LAST_MODIFIED"}:
    raise SystemExit("report transport validator is not resumable")
for name in ("range_contract", "matching_if_range_contract"):
    if data[name] != "CONTRACT_PROVEN":
        raise SystemExit("report transport contract not proven")
if (data["initial_status"], data["matching_status"]) != ("206", "206"):
    raise SystemExit("report transport status contract mismatch")
stale_contracts = {
    "200": "SERVER_FULL_BODY_PROVEN",
    "206": "CLIENT_VALIDATOR_REJECTION_PROVEN",
}
if stale_contracts.get(data["stale_status"]) != data["stale_if_range_contract"]:
    raise SystemExit("report transport stale If-Range contract mismatch")
if not data["total_length"].isdigit() or int(data["total_length"]) < 2:
    raise SystemExit("report transport total length invalid")
verified = dt.datetime.fromisoformat(data["verified_at"].replace("Z", "+00:00"))
expires = dt.datetime.fromisoformat(data["expires_at"].replace("Z", "+00:00"))
now = dt.datetime.now(dt.timezone.utc)
if expires - verified != dt.timedelta(minutes=10):
    raise SystemExit("report transport validity window invalid")
if verified > now + dt.timedelta(seconds=30) or expires <= now:
    raise SystemExit("report transport evidence expired")
PY
}
verify_dp_report_probe_state() {
  [ "$(secure_file_operation verify "$DP_REPORT_PROBE_SOURCE_FILE" 600 \
    "$DP_REPORT_PROBE_SOURCE_SHA256")" = "$DP_REPORT_PROBE_SOURCE_SHA256" ]
  [ ! -L "$DP_REPORT_PROBE_DIR" ]
  [ "$(stat -c '%a' "$DP_REPORT_PROBE_DIR")" = 700 ]
  verify_dp_report_probe_json
}
run_dp_report_download_probe() {
  DP_REPORT_PROBE_SOURCE_FILE="$APP_DIR/.dp-report-download-probe-url"
  DP_REPORT_PROBE_SOURCE_SHA256="$(secure_file_operation verify \
    "$DP_REPORT_PROBE_SOURCE_FILE" 600 -)"
  assert_dp_report_probe_marker
  secure_file_operation directory "$DP_REPORT_PROBE_DIR" 700 700 create-new
  DP_REPORT_PROBE_NONCE="$(python3 - <<'PY'
import secrets
print(secrets.token_hex(32))
PY
)"
  DP_REPORT_PROBE_NONCE_SHA256="$(printf '%s' "$DP_REPORT_PROBE_NONCE" | sha256sum | awk '{print $1}')"
  command -v timeout >/dev/null 2>&1
  timeout --signal=TERM --kill-after=5s 75s python3 - \
    "$DP_REPORT_PROBE_SOURCE_FILE" "$DP_REPORT_PROBE_EVIDENCE_FILE" \
    "$DP_REPORT_PROBE_NONCE" "$EXPECTED_COMMIT" "$EXPECTED_JAR_SHA256" <<'PY'
import datetime as dt, email.utils, hashlib, json, os, pathlib, re, ssl, sys
import urllib.error, urllib.parse, urllib.request
import certifi
source_file, evidence_file = map(pathlib.Path, sys.argv[1:3])
nonce, commit, jar_sha = sys.argv[3:]
lines = source_file.read_text(encoding="utf-8").splitlines()
if len(lines) != 1 or not lines[0] or lines[0] != lines[0].strip():
    raise SystemExit("report transport source URL file invalid")
url = lines[0]
parsed = urllib.parse.urlsplit(url)
if (parsed.scheme != "https" or parsed.hostname != "storage.googleapis.com"
        or not parsed.path.startswith("/noonprd-mp-gcs--partner-impex/")
        or parsed.username or parsed.password or parsed.fragment):
    raise SystemExit("report transport source is not a governed Noon report URL")
tls_context = ssl.create_default_context(cafile=certifi.where())
def request(byte_range, if_range=None, exact_body=False, exact_partial_body=False):
    headers = {"Accept": "application/octet-stream,*/*", "Accept-Encoding": "identity",
               "Range": byte_range, "User-Agent": "Nuono-DP-Report-Probe/1"}
    if if_range is not None:
        headers["If-Range"] = if_range
    response = urllib.request.urlopen(
        urllib.request.Request(url, headers=headers), timeout=20, context=tls_context)
    try:
        final = response.geturl()
        final_url = urllib.parse.urlsplit(final)
        if (final_url.scheme != "https" or final_url.hostname != "storage.googleapis.com"
                or not final_url.path.startswith("/noonprd-mp-gcs--partner-impex/")):
            raise SystemExit("report transport final endpoint left the Noon report bucket")
        body = response.read(2 if exact_body or exact_partial_body else 1)
        if exact_body and (len(body) != 1 or response.read(1)):
            raise SystemExit("report transport range body length mismatch")
        if exact_partial_body and response.status == 206 and (len(body) != 1 or response.read(1)):
            raise SystemExit("report transport stale partial body length mismatch")
        return response.status, response.headers, final
    finally:
        response.close()
first_status, first_headers, first_final = request("bytes=0-0", exact_body=True)
content_range = first_headers.get("Content-Range", "")
match = re.fullmatch(r"bytes 0-0/([1-9][0-9]*)", content_range)
if first_status != 206 or not match or int(match.group(1)) < 2:
    raise SystemExit("report transport initial Range contract failed")
if first_headers.get("Content-Length") != "1" or first_headers.get("Content-Encoding", "identity").lower() != "identity":
    raise SystemExit("report transport initial framing contract failed")
total = int(match.group(1))
etag = first_headers.get("ETag", "").strip()
last_modified = first_headers.get("Last-Modified", "").strip()
if etag and not etag.lower().startswith("w/") and re.fullmatch(r'"(?:[^"\\]|\\.)*"', etag):
    validator_kind, validator = "STRONG_ETAG", etag
    stale = '"nuono-probe-stale-' + hashlib.sha256(nonce.encode()).hexdigest() + '"'
elif last_modified:
    parsed_date = email.utils.parsedate_to_datetime(last_modified)
    if parsed_date is None or parsed_date.tzinfo is None:
        raise SystemExit("report transport Last-Modified invalid")
    validator_kind, validator = "LAST_MODIFIED", last_modified
    stale = "Thu, 01 Jan 1970 00:00:00 GMT"
else:
    raise SystemExit("report transport resumable validator absent")
matching_status, matching_headers, matching_final = request(
    "bytes=1-1", validator, exact_body=True)
selected_header = "ETag" if validator_kind == "STRONG_ETAG" else "Last-Modified"
if (matching_status != 206 or matching_headers.get("Content-Range") != f"bytes 1-1/{total}"
        or matching_headers.get("Content-Length") != "1"
        or matching_headers.get(selected_header, "").strip() != validator
        or matching_final != first_final):
    raise SystemExit("report transport matching If-Range contract failed")
stale_status, stale_headers, stale_final = request(
    "bytes=1-1", stale, exact_partial_body=True)
if stale_status == 200:
    if stale_final != first_final or stale_headers.get("Content-Range"):
        raise SystemExit("report transport stale If-Range full-body contract failed")
    stale_contract = "SERVER_FULL_BODY_PROVEN"
elif stale_status == 206:
    if (stale == validator or stale_headers.get("Content-Range") != f"bytes 1-1/{total}"
            or stale_headers.get("Content-Length") != "1"
            or stale_headers.get("Content-Encoding", "identity").lower() != "identity"
            or stale_headers.get(selected_header, "").strip() != validator
            or stale_final != first_final):
        raise SystemExit("report transport ignored stale If-Range contract failed")
    stale_contract = "CLIENT_VALIDATOR_REJECTION_PROVEN"
else:
    raise SystemExit("report transport stale If-Range contract failed")
now = dt.datetime.now(dt.timezone.utc)
fingerprint = lambda value: hashlib.sha256(value.encode()).hexdigest()
evidence = {
    "schema": "nuono.dp-report-download-transport/v2",
    "type": "DP_REPORT_DOWNLOAD_TRANSPORT_CONTRACT",
    "nonce_sha256": fingerprint(nonce), "manifest_commit": commit,
    "candidate_jar_sha256": jar_sha, "source_url_sha256": fingerprint(url),
    "final_endpoint_sha256": fingerprint(first_final),
    "validator_kind": validator_kind, "validator_sha256": fingerprint(validator),
    "range_contract": "CONTRACT_PROVEN",
    "matching_if_range_contract": "CONTRACT_PROVEN",
    "stale_if_range_contract": stale_contract,
    "initial_status": str(first_status), "matching_status": str(matching_status),
    "stale_status": str(stale_status), "total_length": str(total),
    "verified_at": now.isoformat().replace("+00:00", "Z"),
    "expires_at": (now + dt.timedelta(minutes=10)).isoformat().replace("+00:00", "Z"),
}
fd = os.open(evidence_file, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
with os.fdopen(fd, "w", encoding="utf-8") as output:
    json.dump(evidence, output, sort_keys=True, separators=(",", ":"))
    output.write("\n")
PY
  unset DP_REPORT_PROBE_NONCE
  DP_REPORT_PROBE_EVIDENCE_SHA256="$(secure_file_operation verify \
    "$DP_REPORT_PROBE_EVIDENCE_FILE" 600 -)"
  verify_dp_report_probe_state
  emit DP_REPORT_DOWNLOAD_TRANSPORT_CONTRACT CONTRACT_PROVEN
  emit DP_REPORT_DOWNLOAD_PROBE_EVIDENCE_SHA256 "$DP_REPORT_PROBE_EVIDENCE_SHA256"
}
'''


__all__ = ["build_dp_report_download_probe_shell"]
