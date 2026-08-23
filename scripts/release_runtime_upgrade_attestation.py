#!/usr/bin/env python3
"""Candidate-bound evidence fragments for a zero-data-write RUNTIME upgrade."""
from __future__ import annotations

from dataclasses import dataclass

from release_dp10_openapi_probe import build_dp10_openapi_probe_shell
from release_dp_runtime_cutover import build_dp_runtime_cutover_shell


@dataclass(frozen=True)
class RuntimeUpgradeAttestation:
    probe_shell: str = ""
    variables: str = ""
    target_payload_override: str = ""
    prepare_target: str = ""
    recheck: str = ""


def build_runtime_upgrade_attestation(enabled: bool) -> RuntimeUpgradeAttestation:
    if not enabled:
        return RuntimeUpgradeAttestation()
    return RuntimeUpgradeAttestation(
        probe_shell=build_dp10_openapi_probe_shell() + build_dp_runtime_cutover_shell(),
        variables=r'''DP10_PROBE_DIR="$BACKUP_DIR/dp10-openapi-probe"
DP10_PROBE_EVIDENCE_FILE="$DP10_PROBE_DIR/evidence.json"
DP10_SLOT_EVIDENCE_FILE="" DP10_RUNTIME_ENV_ATTESTATION_FILE=""''',
        target_payload_override=r'''
prepare_target_runtime_payloads() {
  [ "$(secure_file_operation install "$APP_DIR/start-nuono-next-test.sh" \
    "$TARGET_SLOT_DIR/start-nuono-next-test.sh" "700,750,755" 700 \
    "700,750,755" "$SOURCE_START_SCRIPT_SHA256" replace "")" = \
    "$SOURCE_START_SCRIPT_SHA256" ]
  [ "$(secure_file_operation install "$STAGED_JAR" "$TARGET_SLOT_DIR/$JAR_NAME" \
    "600,640,644" 600 "600,640,644" "$EXPECTED_JAR_SHA256" replace "")" = \
    "$EXPECTED_JAR_SHA256" ]
}
''',
        prepare_target=r'''
command -v mysql >/dev/null
secure_file_operation verify "$DP_RUNTIME_MYSQL_CNF" 600 - >/dev/null
prepare_dp_runtime_database_target
[ "$(dp_runtime_db_scalar 'SELECT DATABASE();')" = "$DP_RUNTIME_DB_SCHEMA" ]
capture_dp_runtime_database_binding
run_dp10_openapi_probe
secure_file_operation directory "$APP_DIR/blue-green" "700,750,755" 700 accept
secure_file_operation directory "$TARGET_SLOT_DIR" "700,750,755" 700 accept
persist_dp10_probe_for_target
prepare_target_runtime_payloads
prepare_dp10_probe_runtime_environment
verify_dp10_probe_state
verify_dp_runtime_database_binding
LEGACY_CANARY_DISPOSITION=RUNTIME_REATTESTED
''',
        recheck="verify_dp10_probe_state\nverify_dp_runtime_database_binding",
    )


__all__ = ["RuntimeUpgradeAttestation", "build_runtime_upgrade_attestation"]
