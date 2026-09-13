# Initial architecture

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
