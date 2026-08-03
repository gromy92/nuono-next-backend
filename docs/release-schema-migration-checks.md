# Release schema migration checks

The release migration catalog separates two different contracts:

- `postcheck_path` is the immutable, one-time acceptance check for the exact
  migration script. Bootstrap, apply, and repair-forward always use it. Its
  SHA-256 is stored in migration history and published bytes must not change.
- `livecheck_path` is the durable invariant checked on later releases before
  any pending migration starts. It must describe facts that remain true while
  normal business data changes.

The strict default is to point `livecheck_path` at the same file as
`postcheck_path`. A separate file under `db/livecheck/` is allowed only when a
reviewed migration contains one-time seed or snapshot acceptance that is not a
permanent live invariant.

Every live-check is loaded from the frozen Jar. The protected CI release
manifest binds its path, SHA-256, and size alongside the migration script and
postcheck. The production runner does not accept a CLI skip, local override,
or unbound live-check file.

When a separate live-check is necessary:

1. Keep the published script and postcheck bytes unchanged.
2. Add a regression test proving a failing live-check blocks before pending
   SQL and a passing live-check does not rerun the one-time postcheck.
3. Preserve durable table, column, index, constraint, writer-fence,
   referential, normalization, and sequence invariants.
4. Remove only assertions over business rows that are expected to change
   during normal operation, such as exact seed counts or snapshot hashes.
5. Verify the separate file is packaged and manifest-bound, then execute it
   against an isolated real MySQL fixture in protected CI.

If a later forward migration intentionally changes an owned durable
invariant, that reviewed release may update the corresponding live-check in
the same frozen artifact. It must not rewrite the historical postcheck or
migration history.
