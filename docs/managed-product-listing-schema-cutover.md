# Managed product-listing schema cutover

This runbook governs migrations 182, 189, 204, 205, and the irreversible 206
cutover. It supplements the workspace production release runbook. It does not
authorize a production execution.

## Immutable inputs

Use one deployable backend artifact produced by a successful `master` push.
Its release manifest must bind the exact commit, Jar SHA-256, and these
migration files in this order:

1. `182_product_barcode_psku_identity.sql`
2. `189_product_barcode_store_identity_repair.sql`
3. `190_noon_shared_email_auth_recovery.sql`
4. `204_product_listing_workflow_attempt_claim.sql`
5. `205_product_listing_reauthentication_attempt.sql`
6. `206_product_barcode_store_uniqueness.sql`

The managed entry extracts migrations from the staged or active frozen Jar and
checks every file SHA against the same manifest. It reads the remote `.env`
without sourcing it and never prints database credentials.

## Phase A: reversible additive prerequisites

Run the workspace `additive-schema-migrations` action under the shared
production lock.

Migration 182 is never replayed solely because its migration number is old.
The entry dynamically classifies `product_barcode`:

- `READY`: all three nullable columns and both non-unique indexes have their
  exact type, length, order, and uniqueness contract. If its active-row
  backfill is also exact, skip it.
- `EXACT_LEGACY`: all three columns and both indexes are absent. Only after all
  barcode rows are provably mappable through variant and product master may
  the entry apply 182 and postcheck it.
- `PARTIAL_UNSAFE`: any partial column, wrong type, wrong nullability, or wrong
  index shape. Stop for reviewed repair; do not run 182.

If a `READY` schema has incomplete active-row backfill, the entry checks
mapping safety, reruns the idempotent 182 file from the frozen Jar, and
postchecks it. The entry then applies migration 189 from the same frozen Jar.
Migration 189 aligns `logical_store_id` from the related product variant for
every mappable barcode row, including deleted compatibility rows, and
postchecks that the full table has no missing variant, null store, or
variant/barcode store mismatch. It is idempotent and must pass on a second
test-database run with zero affected rows. The entry then proves migration
190's dependency structures, applies 204 and its postcheck, then 205 and its
postcheck. Release the lock after the action passes.

## Phase B: final backend becomes the only scheduler

Keep the old frontend and global unique `(barcode)` index. Use the existing
managed single-scheduler cutover to install the final frozen backend Jar as the
only JVM. Verify:

- active slot, PID, exact Jar SHA, internal and external health;
- the old and standby ports have no listener;
- exactly one scheduler-capable backend remains.

Before phase C, failure may use the existing managed rollback to the old Jar
because migration 206 has not started.

## Phase C: irreversible 206 window

Run only the workspace `irreversible-schema-cutover` action under the same
shared production lock namespace. The entry must prove before maintenance:

- the active process is the final frozen Jar from phase B;
- migrations 182, 189, and 206 were extracted from that active Jar and match the
  manifest;
- the exact pre-206 migration-182 columns and index order, plus migration 189's
  full-table store-identity postcheck;
- the old global unique `(barcode)` index and absence of the new store index;
- no orphan, null-store, store-mismatch, or projected duplicate barcode;
- schema-touching listing, publish, delete, rebuild, image, Noon PRODUCT pull,
  auth-recovery, reauth, and lease work is drained.

The drain is deliberately a writer/lease gate, not a demand that every durable
operational backlog row become terminal. It blocks:

- active listing, publish, delete, rebuild, and image work already covered by
  their governed task states and locks;
- a non-deleted Noon PRODUCT task that is `QUEUED` or `RUNNING`, because the
  first drain must also prevent a queued PRODUCT projection from being claimed
  before the final-new-Jar JVM stops;
- a `QUEUED` or `RUNNING` task with a null or unknown data domain. Only the known
  non-product domains SALES, ORDER, FINANCE_TRANSACTION, NOON_ADVERTISING,
  OFFICIAL_WAREHOUSE_INVENTORY, and OFFICIAL_WAREHOUSE_FBN_RECEIVED are
  eligible for preservation;
- a live auth-recovery lease, malformed lease evidence, a recovery item in
  `VALIDATING`, or a listing reauthentication attempt in `VERIFYING`.

It preserves without updating or deleting:

- Noon `BLOCKED_AUTH` backlog, including historical `locked_by` audit markers;
- terminal Noon `FAILED`, `PARTIAL`, `SUCCEEDED`, `SKIPPED`, and `CANCELLED`
  history even if `locked_by` remains populated;
- non-PRODUCT `RUNNING` backlog, including report exports with persisted export
  identity and queued sales, order, finance, advertising, or inventory work;
- inactive pending auth-recovery items and listing reauthentication attempts;
- an expired auth-recovery lease whose owner/token shape is internally
  consistent and can no longer pass the application write fence.

The entry emits `SCHEMA_WRITE_BLOCKERS`, `PRESERVED_NOON_BACKLOG`, and the
backward-compatible `DRAIN_BLOCKERS` value. The last value is equal to
`SCHEMA_WRITE_BLOCKERS`; a non-zero preserved backlog is expected and is not a
release failure. This classification does not mark tasks complete, clear
locks, delete exports, or otherwise mutate backlog state.

The managed sequence is:

1. Start loopback JSON 503 and verify the same 503 externally.
2. Recheck the schema-writer/lease drain while leaving unrelated durable Noon
   backlog intact.
3. Stop the only already-new backend JVM.
4. Recheck both application ports and the schema-writer/lease drain. The
   stopped JVM prevents its scheduler from claiming preserved backlog. Query accessible
   `information_schema` activity and optional `performance_schema` lock-wait
   evidence. If `performance_schema` is denied, that absence is not green
   evidence.
5. With `autocommit=0` and five-second row-lock and metadata-lock timeouts,
   acquire `LOCK TABLES product_barcode WRITE, product_variant READ`. The same
   session emits `ACQUIRED` only when `@@innodb_table_locks=1`, then runs
   `COMMIT` and `UNLOCK TABLES`. Permission denial, timeout, disabled InnoDB
   table locks, or malformed evidence fails before the irreversible marker.
   The probe is a fresh session with no pre-existing transaction:
   `LOCK TABLES`' implicit commit cannot commit application work, `COMMIT`
   releases InnoDB's internal table locks, and `UNLOCK TABLES` releases the
   server-layer table locks.
6. Mark the irreversible boundary, apply 206, and postcheck it. Migration 206's
   own session sets the same bounded `innodb_lock_wait_timeout` and
   `lock_wait_timeout`; a race after the proof probe fails into repair-forward
   rather than waiting indefinitely.
7. Restart the exact same new Jar and verify SHA, health, and one scheduler.
8. Restore backend traffic and stop the maintenance responder.
9. Only then may the matching frontend be published.

## Failure boundary

Before the irreversible marker, failure may restart only the same final new
Jar and restore traffic. It never starts an old Jar.

Once 206 starts, the error trap emits
`IRREVERSIBLE_SCHEMA_RESULT=REPAIR_FORWARD_REQUIRED`, keeps JSON 503 routed,
and emits `SAFE_OLD_JAR_ROLLBACK=FORBIDDEN`. Operators repair forward under the
governed release process; ad hoc production commands are not an equivalent
entry.

## Evidence and rehearsal

Preserve the generated plan, manifest, migration hashes, state/action emitted
for 182, schema-write blocker and preserved-backlog counts, maintenance checks,
Jar/PID/port results, and every postcheck. Test environments that are
single-instance rather than managed
blue-green may validate migration idempotency and generated scripts, but must
fail closed instead of pretending to rehearse the production topology.
