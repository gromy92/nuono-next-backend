#!/usr/bin/env python3
"""Shell fragment for the candidate-Jar DP-10 OpenAPI execution-contract probe."""
from __future__ import annotations
from release_secure_slot_files import build_secure_file_shell


def build_dp10_openapi_probe_shell() -> str:
    return build_secure_file_shell() + r'''
assert_dp10_probe_marker() {
  [ "$(secure_file_operation verify "$STAGED_JAR" "600,640,644" \
    "$EXPECTED_JAR_SHA256")" = "$EXPECTED_JAR_SHA256" ]
  python3 - "$STAGED_JAR" <<'PY'
import sys, zipfile
marker = "BOOT-INF/classes/META-INF/nuono/dp10-openapi-probe-v1"
with zipfile.ZipFile(sys.argv[1]) as archive:
    if archive.read(marker) != b"NUONO_DP10_OPEN_API_PROBE_V1\n":
        raise SystemExit("candidate Jar DP10 probe marker mismatch")
PY
}
verify_dp10_probe_json() {
  local evidence_file="${1:-$DP10_PROBE_EVIDENCE_FILE}"
  [ "$(secure_file_operation verify "$evidence_file" 600 \
    "$DP10_PROBE_EVIDENCE_SHA256")" = "$DP10_PROBE_EVIDENCE_SHA256" ]
  python3 - "$evidence_file" "$DP10_PROBE_EVIDENCE_SHA256" \
    "$EXPECTED_COMMIT" "$EXPECTED_JAR_SHA256" "$DP10_PROBE_NONCE_SHA256" <<'PY'
import datetime as dt, hashlib, json, pathlib, re, sys
path = pathlib.Path(sys.argv[1])
expected_sha, commit, jar_sha, nonce_sha = sys.argv[2:]
fields = {
    "schema", "type", "nonce_sha256", "manifest_commit",
    "candidate_jar_sha256", "endpoint_fingerprint_sha256",
    "app_key_fingerprint_sha256", "current_list_contract",
    "history_list_contract", "detail_contract", "verified_at", "expires_at",
}
raw = path.read_bytes()
if hashlib.sha256(raw).hexdigest() != expected_sha:
    raise SystemExit("DP10 probe evidence SHA mismatch")
data = json.loads(raw)
if set(data) != fields or any(not isinstance(value, str) for value in data.values()):
    raise SystemExit("DP10 probe evidence schema mismatch")
if data["schema"] != "nuono.dp10-openapi-execution-contract/v1":
    raise SystemExit("DP10 probe evidence version mismatch")
if data["type"] != "DP10_OPEN_API_EXECUTION_CONTRACT":
    raise SystemExit("DP10 probe evidence type mismatch")
if data["manifest_commit"] != commit or data["candidate_jar_sha256"] != jar_sha:
    raise SystemExit("DP10 probe candidate binding mismatch")
if data["nonce_sha256"] != nonce_sha:
    raise SystemExit("DP10 probe nonce mismatch")
if not all(re.fullmatch(r"[0-9a-f]{64}", data[name]) for name in (
    "nonce_sha256", "candidate_jar_sha256", "endpoint_fingerprint_sha256",
    "app_key_fingerprint_sha256",
)):
    raise SystemExit("DP10 probe fingerprint invalid")
for name in ("current_list_contract", "history_list_contract", "detail_contract"):
    if data[name] != "CONTRACT_PROVEN":
        raise SystemExit("DP10 probe contract not proven")
verified = dt.datetime.fromisoformat(data["verified_at"].replace("Z", "+00:00"))
expires = dt.datetime.fromisoformat(data["expires_at"].replace("Z", "+00:00"))
now = dt.datetime.now(dt.timezone.utc)
if expires - verified != dt.timedelta(minutes=10):
    raise SystemExit("DP10 probe validity window invalid")
if verified > now + dt.timedelta(seconds=30) or expires <= now:
    raise SystemExit("DP10 probe evidence expired")
PY
}
verify_dp10_probe_state() {
  [ "$(secure_file_operation verify "$APP_DIR/.env" 600 \
    "$DP10_PROBE_SOURCE_ENV_SHA256")" = "$DP10_PROBE_SOURCE_ENV_SHA256" ]
  [ ! -L "$DP10_PROBE_DIR" ]
  [ "$(stat -c '%a' "$DP10_PROBE_DIR")" = 700 ]
  [ "$(secure_file_operation verify "$DP10_PROBE_EVIDENCE_FILE" 600 \
    "$DP10_PROBE_EVIDENCE_SHA256")" = "$DP10_PROBE_EVIDENCE_SHA256" ]
  verify_dp10_probe_json
  if [ -n "${DP10_SLOT_EVIDENCE_FILE:-}" ]; then
    [ ! -L "$(dirname "$DP10_SLOT_EVIDENCE_FILE")" ]
    [ "$(stat -c '%a' "$(dirname "$DP10_SLOT_EVIDENCE_FILE")")" = 700 ]
    [ "$(secure_file_operation verify "$DP10_SLOT_EVIDENCE_FILE" 600 \
      "$DP10_PROBE_EVIDENCE_SHA256")" = "$DP10_PROBE_EVIDENCE_SHA256" ]
    verify_dp10_probe_json "$DP10_SLOT_EVIDENCE_FILE"
  fi
  if [ -n "${TARGET_ENV_SHA256:-}" ]; then
    local expected_attestation_sha
    [ "$(secure_file_operation verify "$TARGET_SLOT_DIR/.env" 600 \
      "$TARGET_ENV_SHA256")" = "$TARGET_ENV_SHA256" ]
    [ "$(secure_file_operation verify "$TARGET_SLOT_DIR/$JAR_NAME" 600 \
      "$EXPECTED_JAR_SHA256")" = "$EXPECTED_JAR_SHA256" ]
    [ "$(secure_file_operation verify "$TARGET_SLOT_DIR/start-nuono-next-test.sh" 700 \
      "$SOURCE_START_SCRIPT_SHA256")" = "$SOURCE_START_SCRIPT_SHA256" ]
    [ "$(dirname "$DP10_RUNTIME_ENV_ATTESTATION_FILE")" = \
      "$(dirname "$DP10_SLOT_EVIDENCE_FILE")" ]
    [ "$DP10_SLOT_EVIDENCE_FILE" = \
      "$(dirname "$DP10_RUNTIME_ENV_ATTESTATION_FILE")/dp10-openapi-execution.json" ]
    [ "$DP10_RUNTIME_ENV_ATTESTATION_FILE" = \
      "$(dirname "$DP10_SLOT_EVIDENCE_FILE")/runtime-env.sha256" ]
    [ ! -L "$TARGET_SLOT_DIR/.release-evidence" ]
    [ ! -L "$(dirname "$DP10_RUNTIME_ENV_ATTESTATION_FILE")" ]
    [ "$(stat -c '%a' "$TARGET_SLOT_DIR/.release-evidence")" = 700 ]
    [ "$(stat -c '%a' "$(dirname "$DP10_RUNTIME_ENV_ATTESTATION_FILE")")" = 700 ]
    expected_attestation_sha="$(printf '%s\n' "$TARGET_ENV_SHA256" | sha256sum | awk '{print $1}')"
    [ "$(secure_file_operation verify "$DP10_RUNTIME_ENV_ATTESTATION_FILE" 600 \
      "$expected_attestation_sha")" = "$expected_attestation_sha" ]
    [ "$(grep -Fxc "NUONO_DP10_OPEN_API_EXECUTION_EVIDENCE_FILE=$DP10_SLOT_EVIDENCE_FILE" \
      "$TARGET_SLOT_DIR/.env")" = 1 ]
    [ "$(grep -Fxc "NUONO_DP10_OPEN_API_EXECUTION_EVIDENCE_SHA256=$DP10_PROBE_EVIDENCE_SHA256" \
      "$TARGET_SLOT_DIR/.env")" = 1 ]
    [ "$(grep -Fxc "NUONO_DP10_OPEN_API_EXECUTION_EXPECTED_COMMIT=$EXPECTED_COMMIT" \
      "$TARGET_SLOT_DIR/.env")" = 1 ]
    [ "$(grep -Fxc "NUONO_DP10_OPEN_API_EXECUTION_RUNTIME_ENV_SHA256_FILE=$DP10_RUNTIME_ENV_ATTESTATION_FILE" \
      "$TARGET_SLOT_DIR/.env")" = 1 ]
    [ "$(grep -Fxc "NUONO_DP_RUNTIME_RELEASE_EXPECTED_COMMIT=$EXPECTED_COMMIT" \
      "$TARGET_SLOT_DIR/.env")" = 1 ]
    [ "$(grep -Fxc "NUONO_DP_RUNTIME_RELEASE_SCHEMA_BINDING_SHA256=$DP_RUNTIME_SCHEMA_BINDING_SHA256" \
      "$TARGET_SLOT_DIR/.env")" = 1 ]
    [ "$(grep -Fxc "NUONO_DP_RUNTIME_RELEASE_CUTOVER_BINDING_SHA256=$DP_RUNTIME_CUTOVER_BINDING_SHA256" \
      "$TARGET_SLOT_DIR/.env")" = 1 ]
    [ "$(grep -Fxc "NUONO_DP_RUNTIME_RELEASE_ENV_SHA256_FILE=$DP10_RUNTIME_ENV_ATTESTATION_FILE" \
      "$TARGET_SLOT_DIR/.env")" = 1 ]
    [ "$(grep -Fxc "NUONO_MANAGED_DP_RELEASE=1" "$TARGET_SLOT_DIR/.env")" = 1 ]
    [ "$(grep -Fxc "NUONO_DATA_PULL_EXECUTION_MODE=RUNTIME" \
      "$TARGET_SLOT_DIR/.env")" = 1 ]
    ! grep -Eq '^NUONO_DATA_PULL_RUNTIME_ENABLED=' "$TARGET_SLOT_DIR/.env"
  fi
}
emit_dp10_probe_summary() {
  python3 - "$DP10_PROBE_EVIDENCE_FILE" <<'PY'
import json, sys
data = json.load(open(sys.argv[1], encoding="utf-8"))
mapping = (
    ("DP10_PROBE_SCHEMA", "schema"),
    ("DP10_PROBE_TYPE", "type"),
    ("DP10_PROBE_NONCE_SHA256", "nonce_sha256"),
    ("DP10_PROBE_MANIFEST_COMMIT", "manifest_commit"),
    ("DP10_PROBE_CANDIDATE_JAR_SHA256", "candidate_jar_sha256"),
    ("DP10_PROBE_ENDPOINT_FINGERPRINT_SHA256", "endpoint_fingerprint_sha256"),
    ("DP10_PROBE_APP_KEY_FINGERPRINT_SHA256", "app_key_fingerprint_sha256"),
    ("DP10_PROBE_CURRENT_LIST_CONTRACT", "current_list_contract"),
    ("DP10_PROBE_HISTORY_LIST_CONTRACT", "history_list_contract"),
    ("DP10_PROBE_DETAIL_CONTRACT", "detail_contract"),
    ("DP10_PROBE_VERIFIED_AT", "verified_at"),
    ("DP10_PROBE_EXPIRES_AT", "expires_at"),
)
for output, source in mapping:
    print(f"{output}={data[source]}")
PY
}
run_dp10_openapi_probe() {
  DP10_PROBE_SOURCE_ENV_SHA256="$(secure_file_operation verify "$APP_DIR/.env" 600 -)"
  runtime_env_has_forbidden_injection "$APP_DIR/.env"
  ! grep -Eq '^NUONO_DP10_OPEN_API_EXECUTION_(EVIDENCE_FILE|EVIDENCE_SHA256|EXPECTED_COMMIT|RUNTIME_ENV_SHA256_FILE)=' \
    "$APP_DIR/.env"
  ! grep -Eq '^NUONO_DP_RUNTIME_RELEASE_(EXPECTED_COMMIT|SCHEMA_BINDING_SHA256|CUTOVER_BINDING_SHA256)=' \
    "$APP_DIR/.env"
  ! grep -Eq '^NUONO_DP_RUNTIME_RELEASE_ENV_SHA256_FILE=' \
    "$APP_DIR/.env"
  ! grep -Eq '^NUONO_DATA_PULL_(EXECUTION_MODE|RUNTIME_ENABLED)=' "$APP_DIR/.env"
  assert_dp10_probe_marker
  secure_file_operation directory "$APP_DIR/backups" "700,750,755" 700 accept
  secure_file_operation directory "$BACKUP_DIR" 700 700 create-new
  secure_file_operation directory "$DP10_PROBE_DIR" 700 700 create-new
  [ "$(stat -c '%a' "$DP10_PROBE_DIR")" = 700 ]
  DP10_PROBE_NONCE="$(python3 - <<'PY'
import secrets
print(secrets.token_hex(32))
PY
)"
  DP10_PROBE_NONCE_SHA256="$(printf '%s' "$DP10_PROBE_NONCE" | sha256sum | awk '{print $1}')"
  command -v timeout >/dev/null 2>&1 || {
    echo "required timeout command unavailable" >&2
    return 1
  }
  /usr/bin/env -i PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin \
    LANG=C LC_ALL=C NUONO_DP10_OPEN_API_PROBE_NONCE="$DP10_PROBE_NONCE" \
    timeout --signal=TERM --kill-after=5s 95s java -jar \
    "$STAGED_JAR" dp10-openapi-contract-probe \
    --env-file "$APP_DIR/.env" \
    --candidate-jar "$STAGED_JAR" \
    --manifest-commit "$EXPECTED_COMMIT" \
    --expected-jar-sha256 "$EXPECTED_JAR_SHA256" \
    --evidence-file "$DP10_PROBE_EVIDENCE_FILE"
  unset DP10_PROBE_NONCE
  DP10_PROBE_EVIDENCE_SHA256="$(secure_file_operation verify \
    "$DP10_PROBE_EVIDENCE_FILE" 600 -)"
  verify_dp10_probe_state
  emit_dp10_probe_summary
  emit DP10_PROBE_EVIDENCE_SHA256 "$DP10_PROBE_EVIDENCE_SHA256"
}
persist_dp10_probe_for_target() {
  local evidence_dir
  evidence_dir="$TARGET_SLOT_DIR/.release-evidence/$EXPECTED_COMMIT-$DP10_PROBE_NONCE_SHA256"
  DP10_SLOT_EVIDENCE_FILE="$evidence_dir/dp10-openapi-execution.json"
  DP10_RUNTIME_ENV_ATTESTATION_FILE="$evidence_dir/runtime-env.sha256"
  secure_file_operation directory "$TARGET_SLOT_DIR/.release-evidence" 700 700 accept
  secure_file_operation directory "$evidence_dir" 700 700 create-new
  [ "$(stat -c '%a' "$TARGET_SLOT_DIR/.release-evidence")" = 700 ]
  [ "$(stat -c '%a' "$evidence_dir")" = 700 ]
  [ "$(secure_file_operation install "$DP10_PROBE_EVIDENCE_FILE" \
    "$DP10_SLOT_EVIDENCE_FILE" 600 600 600 "$DP10_PROBE_EVIDENCE_SHA256" \
    create-new "")" = "$DP10_PROBE_EVIDENCE_SHA256" ]
  verify_dp10_probe_json "$DP10_SLOT_EVIDENCE_FILE"
}
prepare_dp10_probe_runtime_environment() {
  local runtime_format runtime_suffix attestation_payload expected_attestation_sha
  [ -n "$DP10_SLOT_EVIDENCE_FILE" ] && [ -n "$DP10_RUNTIME_ENV_ATTESTATION_FILE" ]
  runtime_format='\nNUONO_NEXT_APP_DIR=%s\nNUONO_NEXT_PORT=%s\n'
  runtime_format+='NUONO_NEXT_JAR=%s/%s\nNUONO_NEXT_LOG_FILE=%s/nuono-next.log\n'
  runtime_format+='NUONO_NEXT_PID_FILE=%s/nuono-next.pid\n'
  runtime_format+='NUONO_NEXT_AUTH_SESSION_SECRET_FILE=%s/.auth-session-secret\n'
  runtime_format+='NUONO_MANAGED_DP_RELEASE=1\n'
  runtime_format+='NUONO_DATA_PULL_EXECUTION_MODE=RUNTIME\n'
  runtime_format+='NUONO_DP10_OPEN_API_EXECUTION_EVIDENCE_FILE=%s\n'
  runtime_format+='NUONO_DP10_OPEN_API_EXECUTION_EVIDENCE_SHA256=%s\n'
  runtime_format+='NUONO_DP10_OPEN_API_EXECUTION_EXPECTED_COMMIT=%s\n'
  runtime_format+='NUONO_DP10_OPEN_API_EXECUTION_RUNTIME_ENV_SHA256_FILE=%s\n'
  runtime_format+='NUONO_DP_RUNTIME_RELEASE_EXPECTED_COMMIT=%s\n'
  runtime_format+='NUONO_DP_RUNTIME_RELEASE_SCHEMA_BINDING_SHA256=%s\n'
  runtime_format+='NUONO_DP_RUNTIME_RELEASE_CUTOVER_BINDING_SHA256=%s\n'
  runtime_format+='NUONO_DP_RUNTIME_RELEASE_ENV_SHA256_FILE=%s\n'
  printf -v runtime_suffix "$runtime_format" \
    "$TARGET_SLOT_DIR" "$TARGET_PORT" "$TARGET_SLOT_DIR" "$JAR_NAME" \
    "$TARGET_SLOT_DIR" "$TARGET_SLOT_DIR" "$TARGET_SLOT_DIR" \
    "$DP10_SLOT_EVIDENCE_FILE" "$DP10_PROBE_EVIDENCE_SHA256" \
    "$EXPECTED_COMMIT" "$DP10_RUNTIME_ENV_ATTESTATION_FILE" \
    "$EXPECTED_COMMIT" "$DP_RUNTIME_SCHEMA_BINDING_SHA256" \
    "$DP_RUNTIME_CUTOVER_BINDING_SHA256" \
    "$DP10_RUNTIME_ENV_ATTESTATION_FILE"
  TARGET_ENV_SHA256="$(secure_file_operation install "$APP_DIR/.env" \
    "$TARGET_SLOT_DIR/.env" 600 600 600 "$DP10_PROBE_SOURCE_ENV_SHA256" \
    replace "$runtime_suffix")"
  runtime_env_has_forbidden_injection "$TARGET_SLOT_DIR/.env"
  printf -v attestation_payload '%s\n' "$TARGET_ENV_SHA256"
  expected_attestation_sha="$(printf '%s' "$attestation_payload" | sha256sum | awk '{print $1}')"
  [ "$(secure_file_operation write "$DP10_RUNTIME_ENV_ATTESTATION_FILE" \
    600 600 create-new utf8 "$attestation_payload")" = "$expected_attestation_sha" ]
}
prepare_target_runtime_payloads() {
  [ "$(secure_file_operation install "$APP_DIR/start-nuono-next-test.sh" \
    "$TARGET_SLOT_DIR/start-nuono-next-test.sh" "700,750,755" 700 \
    "700,750,755" "$SOURCE_START_SCRIPT_SHA256" replace "")" = \
    "$SOURCE_START_SCRIPT_SHA256" ]
  [ "$(secure_file_operation install "$STAGED_JAR" "$TARGET_SLOT_DIR/$JAR_NAME" \
    "600,640,644" 600 "600,640,644" "$EXPECTED_JAR_SHA256" replace "")" = \
    "$EXPECTED_JAR_SHA256" ]
}
'''


__all__ = ["build_dp10_openapi_probe_shell"]
