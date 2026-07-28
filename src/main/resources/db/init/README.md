# DB Init Migration Governance

`db/init/*.sql` is the local and release schema ledger for
`nuono-next/backend`.

## Boundaries

- File names use `NNN_lowercase_slug.sql`.
- New migration numbers are unique. Registered historical collisions remain in
  `migration-governance.tsv`; do not add another collision.
- `DbInitMigrationRegistry` validates the complete init-script inventory.
- Historical files `000`–`206` are not a replayable production chain. They mix
  local bootstrap, fixtures, data repair, and managed cutover operations.
- `release-migrations.tsv` is the immutable, forward-only production catalog
  beginning at `210`. The exact filename is the migration identity.
- The release-side Database Migration Module owns schema DDL. Application
  startup and business requests are read-only with respect to schema.

## Production entry point

Run forward migrations through the workspace's governed
`additive-schema-migrations` release action. That action already owns the shared
production-release lock and invokes this module before the new application Jar
can be cut over.

The release host must provide `$APP_DIR/.migration.cnf`:

- a regular, non-symlink file owned by the release user with mode `0600`;
- credentials for a dedicated migration account, not the Web runtime account;
- access to the same schema named by `NUONO_NEXT_DB_URL`;
- privileges required by the catalog, including `CREATE TEMPORARY TABLES`.

The action freezes both this file and the governed Jar into its private release
work directory. It overrides the defaults file's endpoint with the host, port,
and schema parsed from the application's JDBC URL. The module then verifies the
target schema and stable server identity, disables MySQL client reconnect,
acquires a fixed advisory lock, and performs every schema mutation on that same
persistent MySQL session. Lock acquisition and SQL execution have bounded
timeouts.

Do not run the production mutation command from an unpackaged checkout. A
mutating action requires the staged Jar plus either its verified release
manifest or the exact governed Jar hash from the shared-lock cutover. The SQL,
catalog, postchecks, and Python runner are all read from and checked against that
frozen Jar.

For an operator-only inspection, use the same dedicated defaults file and an
explicit expected schema:

```bash
python3 scripts/release_schema_migrations.py status \
  --mysql-defaults-file /release-secrets/nuono-migration.cnf \
  --expected-host db.internal \
  --expected-port 3306 \
  --expected-schema nuono_next

python3 scripts/release_schema_migrations.py plan \
  --mysql-defaults-file /release-secrets/nuono-migration.cnf \
  --expected-host db.internal \
  --expected-port 3306 \
  --expected-schema nuono_next \
  --staged-jar /staged/nuono-next-backend.jar
```

For a governed standalone mutation, the full contract is:

```bash
python3 scripts/release_schema_migrations.py apply \
  --mysql-defaults-file /release-secrets/nuono-migration.cnf \
  --expected-host db.internal \
  --expected-port 3306 \
  --expected-schema nuono_next \
  --release-manifest /staged/release-manifest.json \
  --staged-jar /staged/nuono-next-backend.jar \
  --release-commit <40-character-lowercase-git-sha> \
  --installed-by <release-identity>
```

If the pending catalog contains `MANAGED` entries, repeat
`--approve-managed <exact-migration-key>` once for every pending managed
migration. The supplied set must exactly match; stale, missing, or duplicate
approvals fail closed.

## State and recovery

Migration history has one current row and an append-only attempt ledger.
Supported states are `APPLYING`, `APPLIED`, `FAILED`, and the bootstrap-only
`BASELINED` state. A catalog is valid only when its completed history is a
continuous prefix with matching script and postcheck hashes.

Before applying anything new, the module reruns every completed migration's live
postcheck. It records `APPLIED` only after the bound postcheck returns true. A
failed SQL command is stored as a bounded, redacted error summary and leaves the
migration blocked.

After an interruption:

1. Preserve the failed attempt and release evidence.
2. Inspect the live schema and the failed operation.
3. Run `repair-forward --migration-key <exact-key>` from the same governed Jar.
4. If the postcheck already passes, the module appends a reconciliation attempt;
   it never overwrites the failed attempt.
5. If the postcheck fails, an idempotent rerun additionally requires `--rerun`.

Never delete a history row, rewrite a checksum, or mark an attempt successful by
hand.

## Application startup gate

Under the database-backed `local-db` profile,
`nuono.schema-release-gate.enabled` defaults to `true`. The application reads
the history tables before completing startup and refuses to start when a
catalog migration is missing, blocked, checksum-drifted, or disconnected from
its current attempt row. This makes a skipped schema-first release fail on the
standby slot before traffic cutover.

The Web account therefore needs read access to
`nuono_schema_migration` and `nuono_schema_migration_attempt`, but never writes
them. The gate may be disabled only for isolated tests or disposable developer
databases; production release configuration must leave it enabled.

After the no-runtime-DDL Jar has completed its observation window, revoke DDL
from the Web account. Keep a verified no-runtime-DDL rollback Jar: an older Jar
that still creates schema at request time is no longer a valid rollback once
those privileges are removed.

## Focused verification

Local Maven checks remain serial and use at most three explicit test classes per
command:

```bash
mvn -q \
  -Dtest=RuntimeDdlBoundaryTest,ReleaseSchemaReadinessGateTest,DbInitMigrationRegistryTest \
  test

python3 -m unittest \
  scripts/tests/test_release_schema_migrations.py \
  scripts/tests/test_mysql_migration_database.py \
  scripts/tests/test_schema_migration_artifact.py
```

The MySQL integration suite must also pass against the CI MySQL version using
the non-root migration account before release.
