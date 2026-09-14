# Architecture

## Deployment

The MVP is one local JavaFX desktop application backed by one SQLite database. It is designed for an independent pharmacy with one primary billing computer.

Cloud backup, remote dashboards, and synchronization are later capabilities. Domain identifiers use UUIDs and inventory uses append-only movements so those capabilities can be added without redesigning the stock model.

## Package boundaries

```text
com.nepalpharmacy
├── bootstrap       application startup and database initialization
├── product         medicine and product master
├── inventory       batches, expiry, and inventory movements
├── purchasing      purchase entry and purchase returns
├── sales           POS, invoices, payments, and sales returns
├── party           suppliers and customers
├── credit          derived customer receivables and supplier payables
├── compliance      prescriptions, pharmacy configuration, and audit
├── reporting       operational queries and exports
└── shared          IDs, money, time, and common validation
```

Dependencies should point toward the domain. JavaFX and JDBC remain implementation details at the edges.

## Data rules

- UUID text keys permit future offline synchronization.
- Dates and timestamps use ISO-8601 text in SQLite.
- Monetary database columns use integer paisa.
- Quantities use integer base units, such as individual tablets.
- Available batch stock is the sum of inventory movements, not an editable column.
- Historical financial transactions are reversed, not deleted.

## Product classification decision

Product category and unit of sale are Java enums mirrored by SQLite `CHECK` constraints. This keeps the first single-shop release simple and ensures invalid free-text values cannot enter the catalog. The tradeoff is intentional: adding another category or unit requires a small code change and versioned migration. Editable lookup tables can replace this when pilot feedback demonstrates that shop-specific classifications are necessary.

## Purchase receipt transaction

A purchase receipt is persisted as one local SQLite transaction. The purchase header is inserted first; each line then reuses or creates its product/batch/expiry identity, records its immutable purchase line, and appends a `PURCHASE_RECEIPT` inventory movement. Any failure rolls back the complete transaction.

The `batch_stock` view and FEFO repository query calculate stock from movements at read time. No batch or product row contains an editable on-hand quantity.

## Composition and transaction boundaries

`bootstrap.ApplicationContext` is the single production composition point. It constructs JDBC adapters and services once and exposes services to the JavaFX shell. Screens depend only on services, services depend only on narrow repository interfaces, and transaction coordinators depend on repository interfaces plus the shared `TransactionRunner` abstraction. JDBC connections remain inside infrastructure implementations.

The dependency direction for sales is `sales` toward `product`, `inventory`, and `party`; those packages do not depend on `sales`. The higher-level `credit` capability reads immutable transaction events from sales/purchasing plus party masters; none of those lower-level packages depends back on `credit`.

## Sale transaction

A sale is persisted as one local SQLite transaction. The transaction reloads every selected batch and its computed stock, rejects missing/expired/insufficient stock, reloads the active product's current sale price, and validates an optional or credit-required customer. It then allocates the next invoice number, inserts the immutable sale header and lines, and appends a positive-quantity `SALE` movement for each line. Any failure rolls back the invoice number, sale records, and movements.

Movement quantities remain positive. The `batch_stock` view applies direction by movement type: purchase receipts and sale returns add quantity, while sales and purchase returns subtract it. This keeps movement magnitude validation simple and makes movement intent explicit.

## Return transactions

Sales and purchase returns are append-only reversals tied to exact original transaction lines. A sales return reloads the original sale and each selected `sale_line` inside one SQLite transaction, verifies line ownership and the exact original batch, sums prior returns live, replaces draft pricing with the original line's immutable sale-price snapshot, allocates a sales-return number, inserts the return header and lines, and appends one positive-magnitude `SALE_RETURN` movement per line. The stock view interprets that movement as an addition.

A purchase return follows the same transaction shape against `purchase` and `purchase_line`. It additionally validates the copied supplier and reloads current `batch_stock` so the return cannot remove more units than are physically available after intervening sales or returns. It replaces draft cost with the original purchase-line snapshot, allocates a purchase-return number, persists the return, and appends positive-magnitude `PURCHASE_RETURN` movements that the stock view interprets as subtraction.

Both operations use the shared `TransactionRunner`. Header, lines, counter increment, and movements commit together or roll back together. The original purchase/sale and line rows are never updated or deleted. Separate `SALES_RETURN` and `PURCHASE_RETURN` rows in the existing `invoice_counter` table keep each document sequence independent. Consistent with the established sale-number implementation, an unsuccessful transaction rolls back its counter increment and therefore consumes no number.

## Inventory movement reference integrity

V4 removed the former single-table foreign key from `inventory_movement.reference_id` because movements can refer to different transaction tables. After every Flyway startup migration, `DatabaseBootstrap` runs a lightweight polymorphic-reference check. Every `PURCHASE_RECEIPT` must resolve to `purchase`, every `SALE` to `sale`, every `SALE_RETURN` to `sales_return`, and every `PURCHASE_RETURN` to `purchase_return`. Any unrecognized movement type fails the check. The query returns only the first violation and startup stops with its movement ID, type, and reference ID. All four coordinators reference their transaction header IDs consistently.

## Historical document reads

Sales and purchase history are strict read-only slices following `UI -> Service -> Repository interface -> JDBC -> SQLite`. `ApplicationContext` constructs both history repositories and services. JavaFX screens receive only their services and navigation callbacks; they contain no SQL, stock calculations, return validation, or persistence logic.

History uses purpose-built summary and detail read models rather than the POS-only `SaleReceipt` confirmation type or mutable master-data projections. Summary queries join party data without active-only filters, retain walk-in sales through a display fallback, and are bounded at the database: 50 rows for the default recent view and 101 rows for a capped 100-result search so the service can report truncation. Sales return status is derived by a grouped query from original sale-line quantities and aggregated sales-return-line quantities; it is never stored or queried once per list row. Detail queries run only for the selected document and read the immutable price/cost and line-total snapshots from `sale_line` or `purchase_line`. Purchase detail also joins movement-derived `batch_stock` to expose current physical availability.

The transaction repositories retain their V5 transaction-scoped original-document methods and now also expose ordinary connection-owning read variants. Each JDBC implementation shares one private SQL/mapping path between both variants, so read-only browsing does not require a write transaction and does not duplicate the established query logic.

No V6 schema migration is required. Sales already have unique invoice lookup plus date and customer indexes from V4, and purchases already have the supplier/date index from V3. The implemented customer, supplier, and supplier-invoice filters are case-insensitive substring searches with leading wildcards, for which ordinary B-tree indexes would not help. V1 through V5 remain unchanged, and the inventory-movement reference diagnostic remains unchanged because history creates no movement or transaction row.

## Operational inventory alerts

Application phase V7 is a strict read-only projection over the V5 schema. `InventoryAlertService` supplies one explicit operational date—obtained from its injected `Clock` or provided directly—to the summary and both bounded repository queries. `JdbcInventoryAlertRepository` aggregates products and batches in SQL through `batch_stock`; it does not loop over rows to issue per-product or per-batch queries. Results use the established N+1 cap pattern: the repository receives a limit of 151, while the service returns at most 150 rows plus a truncation flag.

Physical stock is the raw `batch_stock.quantity_base_units` value, including expired batches. Sellable stock includes only a positive batch-stock value whose `expiry_date >= asOfDate`. Expired stock includes only a positive batch-stock value whose `expiry_date < asOfDate`. The positive-stock and date predicates are single-sourced in `inventory.infrastructure.InventoryStockSql`; both `JdbcBatchRepository` FEFO availability and the alert aggregation reuse the same sellable predicate. Consequently, a batch expiring on the operational date is sellable in POS and belongs to the dashboard's 0-30-day bucket for that same date. Low-stock calculations use sellable non-expired stock, not total physical stock.

Product status is computed for active products only. Sellable stock at or below zero is `OUT_OF_STOCK`, independent of the configured reorder threshold. Positive sellable stock is `LOW_STOCK` only when the threshold is positive and stock is at or below it; all other positive-stock cases are `OK`. A left join from active products ensures products without batches still become out-of-stock. Expiry rows instead include active and inactive products when their batch has positive physical stock, because discontinued medicine can still require operational attention. Zero and negative batch balances are displayed nowhere in expiry alerts and are not silently repaired.

Expiry buckets use exact day arithmetic: before the operational date is expired, then 0-30, 31-60, and 61-90 inclusive; later batches are excluded from the default alert result. The six summary values are non-overlapping. Text filtering uses the same case-insensitive SQLite `instr(lower(...), lower(?))` technique as POS product search.

The dependency path remains `JavaFX -> InventoryAlertService -> InventoryAlertRepository -> JDBC -> SQLite`. `ApplicationContext` is still the only production composition point, the screen contains no SQL or stock classification rules, and the repository is read-only. Refresh always reruns the aggregate queries against persisted product data and the movement-derived view, so purchase receipts, sales, sales returns, purchase returns, and threshold edits require no V7 bookkeeping. Expiry alerts do not modify inventory.

No V7 schema migration is required. Existing batch/product and inventory-movement indexes support the joins and bounded ordering, while substring search cannot benefit from a normal B-tree index. Application phase V7 therefore leaves Flyway at schema version V5 and leaves `DatabaseBootstrap.verifyInventoryMovementReferences()` unchanged.

## Customer and supplier credit subledgers

Application phase V8 adds a `credit` capability while keeping the system a modular monolith. Its dependency path is `CreditAccountsScreen -> CustomerAccountService/SupplierAccountService -> narrow account and account-entry repository interfaces -> JDBC -> SQLite`. `ApplicationContext` remains the sole production composition root. JavaFX performs formatting, filter capture, and feedback only; balance queries and validation remain below the UI.

Balances are derived, never stored. Customer balance is opening balance plus `CREDIT` sales, less customer-linked `CREDIT` sales returns and payments received. Supplier balance is opening balance plus `CREDIT` purchases, less `CREDIT` purchase returns and payments made. The transaction headers themselves are ledger events; only genuine manual opening/payment events are inserted into `customer_account_entry` or `supplier_account_entry`. Negative balances remain meaningful party credit and are never clamped.

Ledger detail uses deterministic chronological ordering by business date, creation timestamp, event type, and source ID. Java-side running totals and payment-time current totals use `Math.addExact`; persisted amounts and SQL aggregation use SQLite `INTEGER` paisa. Account lists follow the bounded N+1 convention: repositories request at most 151 rows, services expose 150 plus a truncation flag. Inactive parties remain eligible for list/search/detail projections.

V6 rebuilds `purchase` and `purchase_return` so `payment_method` and `settlement_method` are required, checked, and have no database default. Existing rows are copied as `LEGACY_UNSPECIFIED`; new application writes must explicitly choose Cash, QR/digital, or Credit/Udharo. A rebuild was chosen over `ALTER TABLE ... DEFAULT` so a future omitted field fails loudly instead of silently producing fake legacy data. The new classification is inserted within the established atomic purchase/return transactions.

Manual openings and payments use the shared `TransactionRunner`: party existence, current derived balance, opening uniqueness, validation, and insert occur in one transaction. Only Cash or QR may settle a balance, and a manual payment cannot exceed a positive outstanding amount. Account-entry repositories offer insertion only; there is no update/delete path. A new Credit sales return is rejected when its original sale has no customer, while historical customerless Credit refunds are excluded from the ledger join without mutation.

This is a party subledger only. It does not introduce a chart of accounts, double-entry journals, cash book, P&L, balance sheet, tax accounting, due dates, aging, interest, credit limits, or any inventory movement/view/type change. Application phase V8 advances Flyway from schema V5 to schema V6 through `V6__create_credit_udharo_ledger.sql`.
