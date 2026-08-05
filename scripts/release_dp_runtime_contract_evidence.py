#!/usr/bin/env python3
"""Fail-closed import of externally governed DP provider-contract evidence."""
from __future__ import annotations


def build_dp_runtime_contract_evidence_shell() -> str:
    return r'''
validate_dp_runtime_contract_evidence() {
  local evidence_file="$1" policy_jar="${2:-$STAGED_JAR}"
  python3 - "$evidence_file" "$policy_jar" "$EXPECTED_COMMIT" \
    "$EXPECTED_JAR_SHA256" <<'PY'
import datetime as dt, hashlib, json, pathlib, re, sys, zipfile
path = pathlib.Path(sys.argv[1])
jar_path = pathlib.Path(sys.argv[2])
commit, jar_sha = sys.argv[3:]
root_fields = {
    "schema", "type", "manifest_commit", "candidate_jar_sha256", "evidence",
}
item_fields = {
    "requirement", "status", "source_kind", "source_identity_sha256",
    "verified_at", "expires_at",
}
sources = {
    "DP04_STABLE_SNAPSHOT": "PROVIDER_SNAPSHOT_AUTHORITY",
    "DP06_COMPLETE_CAMPAIGN_ENUMERATION": "PROVIDER_COMPLETE_CAMPAIGN_ENUMERATION",
    "DP07A_STABLE_SNAPSHOT": "PROVIDER_SNAPSHOT_AUTHORITY",
    "DP10_MODIFIED_TIME_VISIBILITY_CONTRACT": "PROVIDER_MODIFIED_TIME_VISIBILITY",
}
policy_entry = (
    "BOOT-INF/classes/META-INF/nuono/"
    "dp-runtime-provider-contract-policy-v1.json"
)
policy_root_fields = {"schema", "requirements"}
policy_item_fields = {"requirement", "source_kind", "approved_source_sha256"}
if hashlib.sha256(jar_path.read_bytes()).hexdigest() != jar_sha:
    raise SystemExit("DP runtime contract policy Jar SHA mismatch")
with zipfile.ZipFile(jar_path) as jar:
    policy = json.loads(jar.read(policy_entry))
if not isinstance(policy, dict) or set(policy) != policy_root_fields:
    raise SystemExit("DP runtime contract policy schema mismatch")
if policy["schema"] != "nuono.dp-runtime-provider-contract-policy/v1":
    raise SystemExit("DP runtime contract policy version mismatch")
policy_items = policy["requirements"]
if not isinstance(policy_items, list) or len(policy_items) != len(sources):
    raise SystemExit("DP runtime contract policy cohort mismatch")
approved = {}
for item in policy_items:
    if not isinstance(item, dict) or set(item) != policy_item_fields:
        raise SystemExit("DP runtime contract policy item mismatch")
    requirement = item["requirement"]
    digests = item["approved_source_sha256"]
    if (requirement in approved or sources.get(requirement) != item["source_kind"] or
            not isinstance(digests, list) or digests != sorted(set(digests)) or
            any(not isinstance(value, str) or not re.fullmatch(r"[0-9a-f]{64}", value)
                for value in digests)):
        raise SystemExit("DP runtime contract policy allowlist invalid")
    approved[requirement] = set(digests)
if set(approved) != set(sources):
    raise SystemExit("DP runtime contract policy incomplete")
data = json.loads(path.read_bytes())
if not isinstance(data, dict) or set(data) != root_fields:
    raise SystemExit("DP runtime contract evidence schema mismatch")
if data["schema"] != "nuono.dp-runtime-provider-contracts/v1":
    raise SystemExit("DP runtime contract evidence version mismatch")
if data["type"] != "DP_RUNTIME_PROVIDER_CONTRACTS":
    raise SystemExit("DP runtime contract evidence type mismatch")
if data["manifest_commit"] != commit or data["candidate_jar_sha256"] != jar_sha:
    raise SystemExit("DP runtime contract candidate identity mismatch")
items = data["evidence"]
if not isinstance(items, list) or len(items) != len(sources):
    raise SystemExit("DP runtime contract evidence cohort mismatch")
seen = set()
now = dt.datetime.now(dt.timezone.utc)
for item in items:
    if not isinstance(item, dict) or set(item) != item_fields:
        raise SystemExit("DP runtime contract item schema mismatch")
    requirement = item["requirement"]
    if requirement in seen or sources.get(requirement) != item["source_kind"]:
        raise SystemExit("DP runtime contract source identity mismatch")
    seen.add(requirement)
    if item["status"] != "CONTRACT_PROVEN":
        raise SystemExit("DP runtime contract status not proven")
    if not re.fullmatch(r"[0-9a-f]{64}", item["source_identity_sha256"]):
        raise SystemExit("DP runtime contract source digest invalid")
    if item["source_identity_sha256"] not in approved[requirement]:
        raise SystemExit("DP runtime contract source digest is not candidate-approved")
    verified = dt.datetime.fromisoformat(item["verified_at"].replace("Z", "+00:00"))
    expires = dt.datetime.fromisoformat(item["expires_at"].replace("Z", "+00:00"))
    validity = expires - verified
    if not dt.timedelta(0) < validity <= dt.timedelta(days=30):
        raise SystemExit("DP runtime contract validity window invalid")
    if verified > now + dt.timedelta(seconds=30) or expires <= now:
        raise SystemExit("DP runtime contract evidence expired")
if seen != set(sources):
    raise SystemExit("DP runtime contract requirements incomplete")
PY
}
load_dp_runtime_contract_evidence() {
  DP_RUNTIME_CONTRACT_SOURCE_FILE="$APP_DIR/.dp-runtime-provider-contracts.json"
  DP_RUNTIME_CONTRACT_EVIDENCE_SHA256="$(secure_file_operation verify \
    "$DP_RUNTIME_CONTRACT_SOURCE_FILE" 600 -)"
  [[ "$DP_RUNTIME_CONTRACT_EVIDENCE_SHA256" =~ ^[0-9a-f]{64}$ ]]
  validate_dp_runtime_contract_evidence "$DP_RUNTIME_CONTRACT_SOURCE_FILE"
}
persist_dp_runtime_contract_evidence_for_target() {
  local evidence_dir
  evidence_dir="$(dirname "$DP10_SLOT_EVIDENCE_FILE")"
  DP_RUNTIME_CONTRACT_SLOT_FILE="$evidence_dir/dp-runtime-contract-evidence.json"
  [ "$(secure_file_operation install "$DP_RUNTIME_CONTRACT_SOURCE_FILE" \
    "$DP_RUNTIME_CONTRACT_SLOT_FILE" 600 600 600 \
    "$DP_RUNTIME_CONTRACT_EVIDENCE_SHA256" create-new "")" = \
    "$DP_RUNTIME_CONTRACT_EVIDENCE_SHA256" ]
  validate_dp_runtime_contract_evidence "$DP_RUNTIME_CONTRACT_SLOT_FILE"
}
verify_dp_runtime_contract_evidence_state() {
  [ "$(secure_file_operation verify "$DP_RUNTIME_CONTRACT_SOURCE_FILE" 600 \
    "$DP_RUNTIME_CONTRACT_EVIDENCE_SHA256")" = \
    "$DP_RUNTIME_CONTRACT_EVIDENCE_SHA256" ]
  validate_dp_runtime_contract_evidence "$DP_RUNTIME_CONTRACT_SOURCE_FILE"
  if [ -n "${DP_RUNTIME_CONTRACT_SLOT_FILE:-}" ]; then
    [ "$(secure_file_operation verify "$DP_RUNTIME_CONTRACT_SLOT_FILE" 600 \
      "$DP_RUNTIME_CONTRACT_EVIDENCE_SHA256")" = \
      "$DP_RUNTIME_CONTRACT_EVIDENCE_SHA256" ]
    local policy_jar="$STAGED_JAR"
    if [ -n "${TARGET_ENV_SHA256:-}" ]; then
      policy_jar="$TARGET_SLOT_DIR/$JAR_NAME"
    fi
    validate_dp_runtime_contract_evidence "$DP_RUNTIME_CONTRACT_SLOT_FILE" "$policy_jar"
  fi
  if [ -n "${TARGET_ENV_SHA256:-}" ]; then
    [ "$(dirname "$DP_RUNTIME_CONTRACT_SLOT_FILE")" = \
      "$(dirname "$DP10_RUNTIME_ENV_ATTESTATION_FILE")" ]
    [ "$(grep -Fxc "NUONO_DP_RUNTIME_CONTRACT_EVIDENCE_FILE=$DP_RUNTIME_CONTRACT_SLOT_FILE" \
      "$TARGET_SLOT_DIR/.env")" = 1 ]
    [ "$(grep -Fxc "NUONO_DP_RUNTIME_CONTRACT_EVIDENCE_SHA256=$DP_RUNTIME_CONTRACT_EVIDENCE_SHA256" \
      "$TARGET_SLOT_DIR/.env")" = 1 ]
    [ "$(grep -Fxc "NUONO_DP_RUNTIME_RELEASE_ENV_SHA256_FILE=$DP10_RUNTIME_ENV_ATTESTATION_FILE" \
      "$TARGET_SLOT_DIR/.env")" = 1 ]
  fi
}
'''


__all__ = ["build_dp_runtime_contract_evidence_shell"]
