# Nepal Pharmacy MVP

Local-first pharmacy management application scaffolded for supervised, incremental development.

## Current foundation

- Java 17
- JavaFX desktop UI
- Maven build
- SQLite local database
- Flyway schema migrations
- JUnit 5 tests

The executable opens a dashboard, product master, purchase-entry screen, and point of sale. A pharmacist can maintain products, receive supplier stock by batch and expiry, and complete FEFO-guided cash, QR, or customer-linked credit sales.

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

Discounts, split payments, returns, cancellation, credit-ledger reporting, barcode scanning, physical printing, authentication, and CBMS integration remain outside V4.

See `docs/MVP_SCOPE.md` for the scope boundary and `docs/ARCHITECTURE.md` for the initial design.

## Detailed implementation history

See `docs/IMPLEMENTATION_HISTORY.md` for the field-by-field and file-by-file record of V1 through V4, their tests, migration behavior, and every later project change. `AGENTS.md` requires future work to update that report in the same task.
