trap handle_irreversible_failure ERR
validate_irreversible_cutover
start_maintenance_responder
switch_nginx_to_maintenance
assert_drained
stop_pid "$ACTIVE_PID"
[ -z "$(pid_for_port "$ACTIVE_PORT")" ]
[ -z "$(pid_for_port "$STANDBY_PORT")" ]
[ "$(backend_jvm_count)" = 0 ]
RUNTIME_STOPPED=1
assert_drained
assert_database_idle
precheck_migration_206
IRREVERSIBLE_STARTED=1
apply_migration "$MIGRATION_206"
postcheck_migration_206
restart_same_new_runtime
switch_nginx_to_active
stop_maintenance_responder
rm -f -- "$MYSQL_CNF"
trap - ERR
emit IRREVERSIBLE_SCHEMA_RESULT PASS
emit MAINTENANCE_STATUS RELEASED
emit EXPECTED_COMMIT "$EXPECTED_COMMIT"
emit MIGRATION_182_SHA256 "$EXPECTED_182_SHA256"
emit MIGRATION_189_SHA256 "$EXPECTED_189_SHA256"
emit MIGRATION_206_SHA256 "$EXPECTED_206_SHA256"
emit ACTIVE_JAR_SHA256 "$EXPECTED_JAR_SHA256"
emit ACTIVE_PID "$NEW_PID"
emit ACTIVE_PORT "$ACTIVE_PORT"
emit SINGLE_SCHEDULER_GUARD PASS
