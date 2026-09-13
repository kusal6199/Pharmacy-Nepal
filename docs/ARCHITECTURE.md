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

Movement quantities remain positive. The `batch_stock` view applies direction by movement type: purchase receipts and future sale returns add quantity, while sales subtract it. This keeps movement magnitude validation simple and makes movement intent explicit.
