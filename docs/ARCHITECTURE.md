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

The dependency direction for sales is `sales` toward `product`, `inventory`, and `party`; those packages do not depend on `sales`.

## Sale transaction

A sale is persisted as one local SQLite transaction. The transaction reloads every selected batch and its computed stock, rejects missing/expired/insufficient stock, reloads the active product's current sale price, and validates an optional or credit-required customer. It then allocates the next invoice number, inserts the immutable sale header and lines, and appends a positive-quantity `SALE` movement for each line. Any failure rolls back the invoice number, sale records, and movements.

Movement quantities remain positive. The `batch_stock` view applies direction by movement type: purchase receipts and sale returns add quantity, while sales and purchase returns subtract it. This keeps movement magnitude validation simple and makes movement intent explicit.

## Return transactions

Sales and purchase returns are append-only reversals tied to exact original transaction lines. A sales return reloads the original sale and each selected `sale_line` inside one SQLite transaction, verifies line ownership and the exact original batch, sums prior returns live, replaces draft pricing with the original line's immutable sale-price snapshot, allocates a sales-return number, inserts the return header and lines, and appends one positive-magnitude `SALE_RETURN` movement per line. The stock view interprets that movement as an addition.

A purchase return follows the same transaction shape against `purchase` and `purchase_line`. It additionally validates the copied supplier and reloads current `batch_stock` so the return cannot remove more units than are physically available after intervening sales or returns. It replaces draft cost with the original purchase-line snapshot, allocates a purchase-return number, persists the return, and appends positive-magnitude `PURCHASE_RETURN` movements that the stock view interprets as subtraction.

Both operations use the shared `TransactionRunner`. Header, lines, counter increment, and movements commit together or roll back together. The original purchase/sale and line rows are never updated or deleted. Separate `SALES_RETURN` and `PURCHASE_RETURN` rows in the existing `invoice_counter` table keep each document sequence independent. Consistent with the established sale-number implementation, an unsuccessful transaction rolls back its counter increment and therefore consumes no number.

## Inventory movement reference integrity

V4 removed the former single-table foreign key from `inventory_movement.reference_id` because movements can refer to different transaction tables. After every Flyway startup migration, `DatabaseBootstrap` runs a lightweight polymorphic-reference check. Every `PURCHASE_RECEIPT` must resolve to `purchase`, every `SALE` to `sale`, every `SALE_RETURN` to `sales_return`, and every `PURCHASE_RETURN` to `purchase_return`. Any unrecognized movement type fails the check. The query returns only the first violation and startup stops with its movement ID, type, and reference ID. All four coordinators reference their transaction header IDs consistently.
