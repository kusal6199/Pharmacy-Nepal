# Nepal Pharmacy MVP

Local-first pharmacy management application scaffolded for supervised, incremental development.

## Current foundation

- Java 17
- JavaFX desktop UI
- Maven build
- SQLite local database
- Flyway schema migrations
- JUnit 5 tests

The executable opens a dashboard, product master, purchase-entry screen, point of sale, inventory-alert dashboard, sales and purchase history screens, both return screens, and one combined Udharo/Credit Accounts screen. A pharmacist can maintain products, receive supplier stock by batch and expiry, complete FEFO-guided transactions, find persisted documents after navigation or restart, record batch-aware returns, review expiry/replenishment risks, and settle derived customer receivables or supplier payables.

## Prerequisites

Install:

1. A Java 17 JDK
2. Git
3. IntelliJ IDEA Community Edition (recommended) or another Java IDE

Maven is included through the project wrapper, so a global Maven installation is optional.

Check the terminal:

```bash
java -version
./mvnw -version
git --version
```

Both `java -version` and the Java version shown by `./mvnw -version` should point to a compatible JDK. On this Mac, use the Homebrew Java 17 installation when running Maven:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
```

## Run the project

```bash
./mvnw test
./mvnw javafx:run
```

The development database is written to `data/pharmacy.db`. To keep data elsewhere:

```bash
PHARMACY_DATA_DIR=/absolute/path/to/data ./mvnw javafx:run
```

## IDE setup

1. Open this folder as a project in IntelliJ IDEA.
2. Import `pom.xml` when prompted.
3. Select a Java 17 Project SDK.
4. Enable automatic Maven import.
5. Run the `PharmacyApplication` Maven/JavaFX configuration or use `./mvnw javafx:run`.

## Codex setup

Open this folder as the primary local project in Codex. Codex will automatically discover `AGENTS.md`, which records the build commands and non-negotiable pharmacy rules.

In the Codex desktop settings, configure local project actions:

- **Test:** `./mvnw test`
- **Verify:** `./mvnw verify`
- **Run desktop app:** `./mvnw javafx:run`

If the action does not inherit Java 17, prefix it with:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
```

## Completed vertical slices

### Product master

The product master includes:

1. Medicine/product name, generic name, and manufacturer
2. Controlled category and base unit selections
3. Optional pack size
4. Exact purchase, sale, and MRP values stored in paisa
5. Configurable tax rate
6. Reorder threshold
7. Active/inactive status instead of deletion
8. Add, list, and edit workflows in JavaFX

### Purchase entry

The purchase slice adds:

1. Active suppliers with optional phone, address, and PAN
2. Purchase headers with one or more base-unit line items
3. Product batches with required expiry and optional manufacturing date
4. Exact batch purchase prices stored in paisa
5. Append-only `PURCHASE_RECEIPT` movements as the stock source of truth
6. Matching-batch reuse and FEFO-ordered available-stock queries
7. One SQLite transaction for the header, lines, batches, and movements
8. A recent-purchases reference table in JavaFX
9. Required Cash, QR/digital, or Credit/Udharo classification for every new purchase

### Point of sale

The POS slice adds:

1. Fast active-product name search
2. Automatic FEFO batch suggestion with manual batch override
3. Live stock and expiry validation inside the save transaction
4. Cash, QR/digital, and customer-linked credit/Udharo payment methods
5. Inline customer creation for credit sales
6. Sequential, transactionally allocated invoice numbers
7. Immutable sale-price snapshots and append-only `SALE` movements
8. An on-screen invoice confirmation

### Sales and purchase returns

The V5 returns slice adds:

1. Sales returns found by the original sequential sale invoice number
2. Purchase returns selected from recent purchase receipts
3. Exact original-line and original-batch traceability
4. Multiple partial returns with live remaining-quantity validation
5. Refund/value totals from the original immutable sale-price or purchase-cost snapshot
6. Current on-hand stock protection for supplier returns
7. Append-only `SALE_RETURN` and `PURCHASE_RETURN` movements
8. Separate sequential return-number counters allocated inside each atomic return transaction
9. Startup integrity validation for all four movement reference-owner types

### Sales and purchase history

The V6 history slice adds:

1. A latest-50 Sales History list with invoice, inclusive date, customer, and payment filters
2. Derived `NONE`, `PARTIAL`, and `FULL` sales-return status from original and returned quantities
3. Read-only sale detail with original saved prices, batch/expiry, and return availability
4. A latest-50 Purchase History list with inclusive date, supplier, and non-unique supplier-invoice text filters
5. Read-only purchase detail with original saved costs, return quantities, and current batch stock
6. Direct navigation from either historical document to its existing return workflow
7. Search result caps of 100 with clear truncation feedback
8. Historical visibility for inactive parties/products and walk-in sales

V6 is read-only and adds no migration. Completed sales and purchases remain immutable; returns remain the only correction mechanism.

### Expiry and low-stock operational dashboard

The application-phase V7 dashboard adds:

1. Six non-overlapping counts for expired, 0-30, 31-60, and 61-90-day batches plus low-stock and out-of-stock active products
2. Expiry rows for positive physical stock, including clearly marked inactive products that still have stock
3. Product-level physical, sellable, and expired stock totals derived from the existing `batch_stock` view
4. Exact day-window and case-insensitive product/generic/manufacturer filters
5. Active-product stock-status and product-name filters
6. Deterministic ordering, 150-row caps, and explicit truncation feedback
7. A Refresh action that rereads persisted products, batches, and all four existing movement types
8. Navigation and a dashboard action that open the dedicated operational view

Low-stock calculations use sellable non-expired stock, not total physical stock. Expiry alerts do not modify inventory. Application phase V7 adds no database migration, so the Flyway schema remains V5.

### Customer and supplier Udharo / Credit accounts

Application phase V8 adds:

1. Customer receivables derived from opening balances, credit sales, credit-refund returns, and payments received
2. Supplier payables derived from opening balances, explicitly classified credit purchases/returns, and payments made
3. No writable or cached balance column: every current and running balance comes from immutable ordered events
4. Cash/QR-only manual settlements, single positive opening balances, and overpayment protection
5. Honest `LEGACY_UNSPECIFIED` classification for purchases and purchase returns created before V8
6. One searchable, bounded JavaFX screen with customer and supplier tabs, balance filters, account detail, running history, and immutable entry forms
7. Human-readable owed/settled/party-credit labels instead of unexplained signed amounts
8. Required settlement method selectors on new purchases and purchase returns, plus purchase-history display of the saved classification

V8 adds Flyway migration `V6__create_credit_udharo_ledger.sql`; application phase V8 therefore runs on database schema V6. This remains a party subledger, not general-ledger accounting.

Stock adjustments/write-offs, automatic purchase orders, reports and analytics, due dates/aging/interest, credit limits, split payments, RBAC/audit, Nepal invoice/PAN/VAT formatting, printing, barcode workflows, scheduled notifications, cloud sync, multi-branch support, and CBMS integration remain outside V8.

See `docs/MVP_SCOPE.md` for the scope boundary and `docs/ARCHITECTURE.md` for the initial design.

## Detailed implementation history

See `docs/IMPLEMENTATION_HISTORY.md` for the field-by-field and file-by-file record of V1 through V8, their tests, migration behavior, and every later project change. `AGENTS.md` requires future work to update that report in the same task.
