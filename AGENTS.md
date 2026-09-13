# Pharmacy MVP engineering instructions

## Build and verification

- Target Java 17 and use Maven.
- Run `./mvnw test` after every behavioral change.
- Run `./mvnw verify` before calling a feature complete.
- Keep this as one deployable modular monolith; organize code by business capability.
- Add every database change as a new versioned migration. Never edit an applied migration.

## Business invariants

- Represent money exactly; never use `float` or `double` for money.
- Store inventory changes as append-only movements. Do not synchronize or overwrite a stock total.
- Track stock by product batch and expiry date.
- Reject expired batches at sale time and prefer FEFO when selecting a batch.
- Store quantities in the product's smallest saleable base unit.
- Treat completed invoices as immutable. Correct them using controlled cancellation or return transactions.
- Require audit records for cancellations, returns, stock adjustments, price overrides, and permission changes.
- Make Nepal tax and regulatory rules configurable and testable. Do not invent a legal rule when a requirement is unclear.

## Scope

- Optimize first for one independent retail pharmacy and one billing computer.
- Do not introduce microservices, cloud synchronization, multi-branch behavior, or full accounting unless the current task explicitly requires it.
- Prefer small vertical slices that include migration, domain rules, UI, and tests.

## Implementation history

- Update `docs/IMPLEMENTATION_HISTORY.md` in the same task as every project change, including minor code, schema, UI, test, configuration, build, and documentation changes.
- Record files added, modified, moved, or removed; database effects; behavior and validation effects; tests run; verification results; decisions; and explicit scope exclusions.
- Keep prior history entries intact. Append a new dated entry for later work; only edit an older entry to correct a factual error, and record that correction in the newest entry.
- Never claim a migration, test, commit, or behavior that cannot be verified from the repository or command output. State historical uncertainty explicitly.
