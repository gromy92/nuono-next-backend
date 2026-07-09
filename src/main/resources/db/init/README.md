# DB Init Migration Governance

`db/init/*.sql` files are the local and release migration ledger for `nuono-next/backend`.

Rules:

- File names must use `NNN_lowercase_slug.sql`.
- New migration numbers must be unique.
- Existing duplicate numbers are legacy-only and must be registered in `migration-governance.tsv`.
- Do not add a new duplicate number without first renumbering the new file.
- `DbInitMigrationRegistry` is the code source of truth for listing and validating init scripts.

Run the focused guard before shipping a DB init change:

```bash
mvn -q -Dtest=DbInitMigrationRegistryTest,SqlMigrationNamingTest,LocalDbBootstrapStatusServiceTest test
```
