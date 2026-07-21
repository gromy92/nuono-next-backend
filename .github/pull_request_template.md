## System delivery contract

- Target issue: <required>
- Module: <required>
- Delivery type: <USER_CAPABILITY | HIDDEN_TECHNICAL_SLICE | BUG_FIX>
- Status before: <Defined | Ready | Implemented | Integrated | Accepted | Released | Observed>
- Status after: <Implemented | Integrated | Accepted | Released | Observed | Done>
- User-visible outcome: <required; say "No user-visible outcome" for a hidden slice>
- Production exposure: <HIDDEN | UNCHANGED | EXPOSE_AFTER_RELEASE>
- P0/P1 status: <NONE_OPEN | ADDRESSES_P0_P1 | CONTAINED_WITH_EVIDENCE>

## Capability integrity

- Complete journey and evidence: <required>
- System responsibility map: <frontend/backend/data/task/external/human boundaries>
- Authorization and data scope: <actor/owner/operator/store/site/object/business keys>
- Failure and recovery: <critical failures, retry, partial success, recovery, rollback>
- Hidden or exposure mechanism: <required>

## Bug integrity

- Root cause: <required for BUG_FIX; otherwise N/A>
- Similar-surface audit: <required for BUG_FIX; otherwise N/A>
- Regression protection: <required for BUG_FIX; otherwise N/A>

## Verification

- [ ] Focused tests: <commands/results>
- [ ] Compile/typecheck: <commands/results>
- [ ] API or authenticated browser journey: <evidence or explicit reason not yet at Accepted>
- [ ] Critical failure and recovery path: <evidence>
- [ ] Authorization isolation and unchanged-data proof: <evidence>
- [ ] Docs and acceptance evidence updated: <paths>

## Release and remaining work

- Known gaps: <required; use "None" only when the contract is fully satisfied>
- Configuration/migration/task dependencies: <required>
- Rollback: <required>
- Observation required after release: <required>
