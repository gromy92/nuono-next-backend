#!/usr/bin/env python3
"""Candidate-Jar read-only scope/anchor manifest fragment for the DP cutover."""
from __future__ import annotations


def build_dp_runtime_manifest_shell() -> str:
    return r'''
DP_RUNTIME_MANIFEST_DIR="$BACKUP_DIR/dp-runtime-cutover-manifest"
DP_RUNTIME_BASELINE_MANIFEST="$DP_RUNTIME_MANIFEST_DIR/baseline.json"
DP_RUNTIME_RECHECK_MANIFEST="$DP_RUNTIME_MANIFEST_DIR/stopped-jvm-recheck.json"
DP_RUNTIME_BASELINE_MANIFEST_SHA256=""
DP_RUNTIME_RECHECK_MANIFEST_SHA256=""
DP_RUNTIME_MANIFEST_COHORT_SHA256=""

assert_dp_runtime_manifest_marker() {
  [ "$(secure_file_operation verify "$STAGED_JAR" "600,640,644" \
    "$EXPECTED_JAR_SHA256")" = "$EXPECTED_JAR_SHA256" ]
  python3 - "$STAGED_JAR" <<'PY'
import sys, zipfile
marker = "BOOT-INF/classes/META-INF/nuono/dp-runtime-cutover-manifest-v1"
with zipfile.ZipFile(sys.argv[1]) as archive:
    if archive.read(marker) != b"NUONO_DP_RUNTIME_CUTOVER_MANIFEST_V1\n":
        raise SystemExit("candidate Jar DP runtime cutover marker mismatch")
PY
}
verify_dp_runtime_manifest_json() {
  local path="$1" expected_sha="$2" expected_cohort="${3:-}"
  [ "$(secure_file_operation verify "$path" 600 "$expected_sha")" = "$expected_sha" ]
  python3 - "$path" "$expected_sha" "$EXPECTED_COMMIT" \
    "$EXPECTED_JAR_SHA256" "$expected_cohort" <<'PY'
import datetime as dt, hashlib, json, pathlib, re, sys
from zoneinfo import ZoneInfo
path = pathlib.Path(sys.argv[1])
expected_sha, commit, jar_sha, expected_cohort = sys.argv[2:]
raw = path.read_bytes()
if hashlib.sha256(raw).hexdigest() != expected_sha:
    raise SystemExit("DP cutover manifest file digest mismatch")
root = json.loads(raw)
root_fields = {
    "schema", "type", "manifestCommit", "candidateJarSha256", "cutoverKey",
    "sourceObservedAtUtc", "generatedAtUtc", "expiresAtUtc", "boundaryPolicy",
    "operationCount", "cohortSha256", "operations",
}
if set(root) != root_fields:
    raise SystemExit("DP cutover manifest root schema mismatch")
if root["schema"] != "nuono.dp-runtime-cutover-manifest/v1" or \
        root["type"] != "DP_RUNTIME_CUTOVER_MANIFEST":
    raise SystemExit("DP cutover manifest version mismatch")
if root["manifestCommit"] != commit or root["candidateJarSha256"] != jar_sha or \
        root["cutoverKey"] != "dp-runtime-" + commit:
    raise SystemExit("DP cutover manifest candidate binding mismatch")
if root["boundaryPolicy"] != "SAFE_PREDECESSOR_OR_FALLBACK_BOUNDARY":
    raise SystemExit("DP cutover boundary policy mismatch")
hex64 = re.compile(r"[0-9a-f]{64}")
cohort_raw = json.dumps(
    root["operations"], ensure_ascii=False, separators=(",", ":"), sort_keys=True,
).encode()
cohort_sha = hashlib.sha256(cohort_raw).hexdigest()
if not hex64.fullmatch(root["cohortSha256"]) or root["cohortSha256"] != cohort_sha:
    raise SystemExit("DP cutover cohort digest mismatch")
if expected_cohort and cohort_sha != expected_cohort:
    raise SystemExit("DP cutover stopped-JVM cohort drift")
def instant(value):
    if not isinstance(value, str) or not value.endswith("Z"):
        raise SystemExit("DP cutover UTC timestamp invalid")
    parsed = dt.datetime.fromisoformat(value[:-1] + "+00:00")
    if parsed.microsecond % 1000:
        raise SystemExit("DP cutover timestamp exceeds DATETIME(3)")
    return parsed
observed = instant(root["sourceObservedAtUtc"])
generated = instant(root["generatedAtUtc"])
expires = instant(root["expiresAtUtc"])
now = dt.datetime.now(dt.timezone.utc)
if generated != observed or expires - generated != dt.timedelta(minutes=30) or \
        generated > now + dt.timedelta(seconds=30) or expires <= now:
    raise SystemExit("DP cutover manifest freshness invalid")
shanghai = ZoneInfo("Asia/Shanghai")
expected_boundary = (observed.astimezone(shanghai).date() - dt.timedelta(days=1))
expected_boundary = dt.datetime.combine(
    expected_boundary, dt.time.min, tzinfo=shanghai
).astimezone(dt.timezone.utc)
operations = root["operations"]
expected_ops = {
    "DP01", "DP02", "DP03", "DP04", "DP05", "DP06",
    "DP07A", "DP07B", "DP08A", "DP08B", "DP10",
}
if root["operationCount"] != 11 or not isinstance(operations, list) or \
        {item.get("operationCode") for item in operations} != expected_ops or \
        len(operations) != 11:
    raise SystemExit("DP cutover operation cohort mismatch")
operation_fields = {
    "operationCode", "expectedScopeCount", "anchorManifestSha256", "scopes",
}
scope_fields = {
    "scopeKey", "scopeNamespace", "ownerUserId", "logicalStoreId", "accountKey",
    "egressKey", "projectCode", "storeCode", "siteCode", "sourceBindingSha256",
    "reconcileAfterUtc", "boundaryKind", "boundaryEvidenceSha256",
    "anchorEvidenceSha256", "binding",
}
binding_fields = {
    "bindingId", "payloadType", "payloadSha256", "payload", "effectiveFromUtc",
}
admissions = {}
for operation in operations:
    if set(operation) != operation_fields or not hex64.fullmatch(
            str(operation["anchorManifestSha256"])):
        raise SystemExit("DP cutover operation schema invalid")
    scopes = operation["scopes"]
    if not isinstance(scopes, list) or operation["expectedScopeCount"] != len(scopes):
        raise SystemExit("DP cutover scope count mismatch")
    keys = [scope.get("scopeKey") for scope in scopes]
    if keys != sorted(keys) or len(keys) != len(set(keys)):
        raise SystemExit("DP cutover scope ordering/uniqueness invalid")
    for scope in scopes:
        if set(scope) != scope_fields:
            raise SystemExit("DP cutover scope schema invalid")
        if not isinstance(scope["ownerUserId"], int) or scope["ownerUserId"] <= 0 or \
                not isinstance(scope["scopeKey"], str) or \
                not scope["scopeKey"].startswith(scope["scopeNamespace"] + "-"):
            raise SystemExit("DP cutover scope identity invalid")
        for field in ("sourceBindingSha256", "boundaryEvidenceSha256", "anchorEvidenceSha256"):
            if not hex64.fullmatch(str(scope[field])):
                raise SystemExit("DP cutover scope digest invalid")
        boundary = instant(scope["reconcileAfterUtc"])
        if scope["boundaryKind"] != root["boundaryPolicy"] or \
                boundary > expected_boundary or \
                boundary.astimezone(shanghai).time() != dt.time.min:
            raise SystemExit("DP cutover safe predecessor boundary invalid")
        identity = tuple(scope[name] for name in (
            "scopeNamespace", "ownerUserId", "logicalStoreId", "accountKey", "egressKey",
            "projectCode", "storeCode", "siteCode", "sourceBindingSha256",
        ))
        if scope["scopeKey"] in admissions and admissions[scope["scopeKey"]] != identity:
            raise SystemExit("DP cutover shared admission identity drift")
        admissions[scope["scopeKey"]] = identity
        binding = scope["binding"]
        needs_binding = operation["operationCode"] in {"DP08A", "DP08B"}
        if needs_binding != isinstance(binding, dict):
            raise SystemExit("DP cutover DP08 binding closure mismatch")
        if binding is not None:
            if set(binding) != binding_fields or not binding["payload"] or \
                    not all(hex64.fullmatch(str(binding[name])) for name in (
                        "bindingId", "payloadSha256"
                    )) or instant(binding["effectiveFromUtc"]) > observed:
                raise SystemExit("DP cutover binding invalid")
print(cohort_sha)
PY
}
run_dp_runtime_cutover_manifest() {
  assert_dp_runtime_manifest_marker
  secure_file_operation directory "$DP_RUNTIME_MANIFEST_DIR" 700 700 create-new
  timeout --signal=TERM --kill-after=5s 90s java -jar "$STAGED_JAR" \
    dp-runtime-cutover-manifest --env-file "$APP_DIR/.env" \
    --candidate-jar "$STAGED_JAR" --manifest-commit "$EXPECTED_COMMIT" \
    --expected-jar-sha256 "$EXPECTED_JAR_SHA256" \
    --evidence-file "$DP_RUNTIME_BASELINE_MANIFEST"
  DP_RUNTIME_BASELINE_MANIFEST_SHA256="$(secure_file_operation verify \
    "$DP_RUNTIME_BASELINE_MANIFEST" 600 -)"
  DP_RUNTIME_MANIFEST_COHORT_SHA256="$(verify_dp_runtime_manifest_json \
    "$DP_RUNTIME_BASELINE_MANIFEST" "$DP_RUNTIME_BASELINE_MANIFEST_SHA256")"
  emit DP_RUNTIME_MANIFEST_COHORT_SHA256 "$DP_RUNTIME_MANIFEST_COHORT_SHA256"
}
recheck_dp_runtime_cutover_manifest() {
  assert_no_backend_jvms
  timeout --signal=TERM --kill-after=5s 90s java -jar "$STAGED_JAR" \
    dp-runtime-cutover-manifest --env-file "$APP_DIR/.env" \
    --candidate-jar "$STAGED_JAR" --manifest-commit "$EXPECTED_COMMIT" \
    --expected-jar-sha256 "$EXPECTED_JAR_SHA256" \
    --evidence-file "$DP_RUNTIME_RECHECK_MANIFEST" \
    --baseline-manifest "$DP_RUNTIME_BASELINE_MANIFEST"
  DP_RUNTIME_RECHECK_MANIFEST_SHA256="$(secure_file_operation verify \
    "$DP_RUNTIME_RECHECK_MANIFEST" 600 -)"
  [ "$(verify_dp_runtime_manifest_json "$DP_RUNTIME_RECHECK_MANIFEST" \
    "$DP_RUNTIME_RECHECK_MANIFEST_SHA256" "$DP_RUNTIME_MANIFEST_COHORT_SHA256")" = \
    "$DP_RUNTIME_MANIFEST_COHORT_SHA256" ]
}
'''


__all__ = ["build_dp_runtime_manifest_shell"]
