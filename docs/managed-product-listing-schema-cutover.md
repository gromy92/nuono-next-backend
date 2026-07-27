# Managed product-listing schema cutover

This runbook governs migrations 182, 204, 205, and the irreversible 206
cutover. It supplements the workspace production release runbook. It does not
authorize a production execution.

## Immutable inputs

Use one deployable backend artifact produced by a successful `master` push.
Its release manifest must bind the exact commit, Jar SHA-256, and these
migration files in this order:

1. `182_product_barcode_psku_identity.sql`
2. `190_noon_shared_email_auth_recovery.sql`
3. `204_product_listing_workflow_attempt_claim.sql`
4. `205_product_listing_reauthentication_attempt.sql`
5. `206_product_barcode_store_uniqueness.sql`

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
postchecks it. It then proves migration 190's dependency structures, applies
204 and its postcheck, then 205 and its postcheck. Release the lock after the
action passes.

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
- migration 182 and 206 were extracted from that active Jar and match the
  manifest;
- the exact pre-206 migration-182 columns, index order, and backfill contract;
- the old global unique `(barcode)` index and absence of the new store index;
- no orphan, null-store, store-mismatch, or projected duplicate barcode;
- listing, publish, delete, rebuild, image, pull, auth-recovery, reauth, and
  lease work is drained.

The managed sequence is:

1. Start loopback JSON 503 and verify the same 503 externally.
2. Recheck the drain.
3. Stop the only already-new backend JVM.
4. Recheck both application ports, the drain, and database lock waits.
5. Mark the irreversible boundary, apply 206, and postcheck it.
6. Restart the exact same new Jar and verify SHA, health, and one scheduler.
7. Restore backend traffic and stop the maintenance responder.
8. Only then may the matching frontend be published.

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
for 182, drain counts, maintenance checks, Jar/PID/port results, and every
postcheck. Test environments that are single-instance rather than managed
blue-green may validate migration idempotency and generated scripts, but must
fail closed instead of pretending to rehearse the production topology.
