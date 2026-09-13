# Pharmacy MVP implementation history

Last updated: 2026-09-13  
Current database schema: V4  
Current completed vertical slices: product master, purchase entry, and POS/sales

## Purpose and maintenance rule

This is the detailed, append-only implementation report for the project. It records database migrations, source files, domain behavior, validation, persistence behavior, JavaFX behavior, tests, documentation, build configuration, architectural decisions, and deliberate scope exclusions.

Every future project change must update this file in the same task. That requirement is also written into `AGENTS.md`. A future entry must include even small changes such as a renamed field, moved class, new index, changed label, altered default value, test adjustment, CSS rule, documentation correction, or dependency version change.

For each future change, record:

1. Date and intended schema/application version.
2. Files added, modified, moved, or removed.
3. Exact database effect, including data migration and rollback implications.
4. Domain and validation behavior added, changed, or removed.
5. Repository, transaction, and query behavior.
6. UI and navigation behavior.
7. Tests added or changed and the behavior each test proves.
8. Commands run and their outcomes.
9. Design decisions, assumptions, known limitations, and excluded scope.

## Source-history note

The first Git commit is `fc9ad7a` (`Initial pharmacy MVP scaffold: JavaFX + SQLite + Flyway foundation`, dated 2026-09-13). That commit contains both the V1 foundation migration and the V2 product-master migration together with the product-master application code. Therefore, V1 and V2 below are a logical migration-by-migration reconstruction from the repository, not two independently verifiable Git commits. This report does not invent a commit boundary that does not exist.

V3 is the purchase-entry slice built on V2. V4 is the POS/sales slice built on V3. Database version numbers refer to Flyway schema migrations; they are not semantic application-release numbers.

---

## V1 — JavaFX, SQLite, Flyway, and inventory schema foundation

Date: 2026-09-13  
Migration: `src/main/resources/db/migration/V1__create_inventory_foundation.sql`  
Purpose: establish the local application stack and the preliminary product, supplier, batch, movement, and computed-stock schema.

### V1-001 — Project and build scaffold

- Created a single Maven project with coordinates:
  - Group: `com.nepalpharmacy`
  - Artifact: `pharmacy-mvp`
  - Version: `0.1.0-SNAPSHOT`
  - Display name: `Nepal Pharmacy MVP`
- Set UTF-8 source encoding.
- Set the Java compiler release to Java 17.
- Added the Maven wrapper for Unix/macOS in `mvnw`.
- Added the Windows Maven wrapper in `mvnw.cmd`.
- Added `.mvn/wrapper/maven-wrapper.properties` so Maven can bootstrap consistently without requiring a global Maven installation.
- Added `.gitignore` entries for:
  - Maven build output in `target/`.
  - Local runtime data in `data/`.
  - SQLite database, shared-memory, and write-ahead-log files.
  - IntelliJ project files.
  - macOS `.DS_Store` files.

### V1-002 — Dependency and plugin versions

`pom.xml` established these runtime and test dependencies:

| Component | Version | Purpose |
|---|---:|---|
| JavaFX Controls | 21.0.9 | Desktop controls and layouts |
| SQLite JDBC | 3.53.4.0 | Local embedded database connection |
| Flyway Core | 13.6.0 | Ordered, versioned schema migration |
| JUnit Jupiter | 5.12.2 | Unit and integration testing |

It also established these build plugins:

| Plugin | Version | Configuration |
|---|---:|---|
| Maven Compiler | 3.13.0 | Compiles with Java release 17 |
| Maven Surefire | 3.5.2 | Runs JUnit 5 with the module path disabled |
| JavaFX Maven Plugin | 0.0.8 | Launches `com.nepalpharmacy.PharmacyApplication` |

### V1-003 — Engineering rules

`AGENTS.md` established the following non-negotiable rules:

- Run on Java 17 and Maven.
- Run `./mvnw test` after behavioral changes.
- Run `./mvnw verify` before declaring a feature complete.
- Keep one deployable modular monolith organized by business capability.
- Add database changes through new Flyway migrations; never rewrite an applied migration.
- Represent money exactly and never with floating-point values.
- Treat inventory movements as append-only and never maintain a writable stock total.
- Track inventory by batch and expiry.
- Store quantities in the smallest saleable base unit.
- Reject expired stock at sale time and prefer FEFO batch selection.
- Keep completed invoices immutable and reverse them through controlled transactions.
- Audit cancellations, returns, adjustments, overrides, and permission changes when those capabilities are implemented.
- Keep Nepal tax and regulatory rules configurable rather than embedding invented legal rules.
- Optimize for one independent pharmacy and one billing computer.
- Exclude cloud synchronization, multi-branch behavior, microservices, and full accounting unless explicitly requested later.

### V1-004 — Architecture and scope documents

`docs/ARCHITECTURE.md` defined:

- One local JavaFX desktop application.
- One local SQLite database.
- UUID text identifiers to support later offline synchronization without redesigning primary keys.
- ISO-8601 text for dates and timestamps.
- Integer paisa for monetary values.
- Integer base units for quantities.
- Append-only movements as the only inventory source of truth.
- Package boundaries for `bootstrap`, `product`, `inventory`, `purchasing`, `sales`, `party`, `compliance`, `reporting`, and `shared`.
- The rule that JavaFX and JDBC remain edge concerns and dependencies point toward domain code.

`docs/MVP_SCOPE.md` defined the intended MVP capabilities and explicitly deferred multi-branch, cloud synchronization, full accounting, e-commerce, loyalty, AI forecasting/OCR, and CBMS integration.

### V1-005 — Database bootstrap behavior

`src/main/java/com/nepalpharmacy/bootstrap/DatabaseBootstrap.java` added:

- A configurable database path represented by an absolute `Path`.
- Default storage at `data/pharmacy.db`.
- Optional storage-directory override through `PHARMACY_DATA_DIR`.
- Automatic creation of the database parent directory.
- A JDBC URL in the form `jdbc:sqlite:<absolute-path>`.
- Flyway migration discovery from `classpath:db/migration`.
- Return of the number of migrations executed during startup.
- A connection factory used by repositories.
- `PRAGMA foreign_keys = ON` on every application connection.
- `PRAGMA busy_timeout = 5000` to tolerate short SQLite lock contention.
- Connection cleanup if PRAGMA initialization fails.
- A descriptive failure if the database directory cannot be created.

### V1-006 — Preliminary `product` table

V1 created `product` with this exact preliminary shape:

| Column | Rule and purpose |
|---|---|
| `id` | Text primary key intended to hold a UUID |
| `sku` | Required and unique stock-keeping code |
| `barcode` | Optional unique barcode |
| `brand_name` | Required product/brand name |
| `generic_name` | Optional generic medicine name |
| `strength` | Optional medicine strength |
| `dosage_form` | Optional preliminary dosage-form text |
| `manufacturer` | Optional manufacturer |
| `pack_label` | Required human-readable pack label |
| `base_units_per_pack` | Required integer greater than zero |
| `drug_category` | Required; defaults to `UNCLASSIFIED`; allowed values `A`, `B`, `C`, `UNCLASSIFIED` |
| `tax_category` | Required; defaults to `EXEMPT`; allowed values `EXEMPT`, `VAT`, `OTHER` |
| `active` | Required boolean integer, default `1`, restricted to `0` or `1` |
| `created_at` | Required ISO-8601 timestamp text by application convention |

This was a preliminary schema. The V2 product-master migration replaced its shape while preserving compatible development data.

### V1-007 — Preliminary `supplier` table

V1 created `supplier` with:

| Column | Rule and purpose |
|---|---|
| `id` | Text primary key intended to hold a UUID |
| `name` | Required supplier name |
| `pan` | Optional PAN |
| `phone` | Optional phone |
| `address` | Optional address |
| `active` | Required boolean integer, default `1`, restricted to `0` or `1` |
| `created_at` | Required timestamp text |

V1 supplied only schema groundwork; it did not yet provide a supplier UI or supplier service.

### V1-008 — Preliminary `product_batch` table

V1 created `product_batch` with:

| Column | Rule and purpose |
|---|---|
| `id` | Text primary key intended to hold a UUID |
| `product_id` | Required foreign key to `product(id)` |
| `supplier_id` | Optional foreign key to `supplier(id)` |
| `batch_number` | Required physical-package batch number |
| `expiry_date` | Required date text |
| `purchase_price_paisa` | Required non-negative integer paisa |
| `selling_price_paisa` | Required non-negative integer paisa |
| `mrp_paisa` | Optional non-negative integer paisa |
| `created_at` | Required timestamp text |

The tuple `(product_id, batch_number, expiry_date)` was unique. V1 also indexed `product_id` and `expiry_date` separately.

### V1-009 — Preliminary `inventory_movement` table

V1 created `inventory_movement` with:

| Column | Rule and purpose |
|---|---|
| `id` | Text primary key intended to hold a UUID |
| `batch_id` | Required foreign key to `product_batch(id)` |
| `movement_type` | Required controlled text value |
| `quantity_delta_base_units` | Required non-zero signed integer |
| `source_type` | Optional source-kind text |
| `source_id` | Optional source identifier |
| `occurred_at` | Required occurrence timestamp |
| `created_by` | Optional user identifier placeholder |
| `created_at` | Required creation timestamp |

The preliminary movement types were `OPENING`, `PURCHASE`, `SALE`, `SALE_RETURN`, `PURCHASE_RETURN`, `EXPIRY`, `DAMAGE`, `ADJUSTMENT`, and `CANCELLATION`.

Indexes were created for `batch_id` and `occurred_at`.

### V1-010 — Computed batch stock

V1 created the `batch_stock` view. It grouped inventory movements by `batch_id` and exposed `COALESCE(SUM(quantity_delta_base_units), 0)` as `quantity_base_units`.

This established the core invariant that stock is calculated from movements and is not stored as an editable product or batch field.

### V1-011 — Foundation documentation and operating instructions

`README.md` documented:

- Java 17, Git, and IntelliJ prerequisites.
- Maven wrapper usage.
- `java -version`, `./mvnw -version`, and `git --version` checks.
- The Homebrew Java 17 `JAVA_HOME` value used on the development Mac.
- `./mvnw test` and `./mvnw javafx:run` commands.
- The default database location and `PHARMACY_DATA_DIR` override.
- IntelliJ Maven import and Java 17 SDK setup.
- Suggested Codex project actions for test, verify, and run.

### V1 boundary

V1 did not provide a working purchase workflow, POS, sales, returns, reports, authentication, or cloud behavior. Although preliminary supplier, batch, and movement tables existed, stock-entry behavior was not implemented until V3.

---

## V2 — Medicine and product master vertical slice

Date: 2026-09-13  
Migration: `src/main/resources/db/migration/V2__create_product_master.sql`  
Purpose: replace the preliminary product schema with the canonical catalog and provide add, list, edit, and deactivate behavior through JavaFX.

### V2-001 — Product table rebuild

SQLite does not support every required column transformation directly, so V2 used a rebuild strategy:

1. Created `product_next` with the canonical V2 fields and constraints.
2. Copied and transformed every existing V1 product row.
3. Dropped the V1 `product` table.
4. Renamed `product_next` to `product`.
5. Added V2 indexes.

The migration was versioned separately and did not edit V1.

### V2-002 — Product field-by-field change map

| V1 field | V2 result |
|---|---|
| `id` | Preserved as the text UUID primary key |
| `sku` | Removed from the product-master slice |
| `barcode` | Removed from the product-master slice |
| `brand_name` | Renamed/transformed into required `name` |
| `generic_name` | Preserved as optional `generic_name` |
| `strength` | Removed as a separate field; strength can be included in `name` such as `Paracetamol 500mg` |
| `dosage_form` | Replaced by controlled `category` and `unit_of_sale` values |
| `manufacturer` | Preserved as optional `manufacturer` |
| `pack_label` | Removed |
| `base_units_per_pack` | Transformed into optional `pack_size` |
| `drug_category` | Replaced by the product-category enum model |
| `tax_category` | Replaced by configurable numeric `tax_rate_basis_points` |
| `active` | Renamed to `is_active` |
| `created_at` | Preserved |
| No V1 equivalent | Added `purchase_price_paisa` |
| No V1 equivalent | Added `sale_price_paisa` |
| No product-level V1 equivalent | Added optional `mrp_paisa` |
| No V1 equivalent | Added `reorder_threshold_base_units` |
| No V1 equivalent | Added `updated_at` |

### V2-003 — Canonical product columns and database constraints

| Column | Database rule |
|---|---|
| `id` | Text primary key |
| `name` | Required, trimmed length from 1 through 160 |
| `generic_name` | Optional, maximum length 160 |
| `manufacturer` | Optional, maximum length 160 |
| `category` | Required controlled value |
| `unit_of_sale` | Required controlled value |
| `pack_size` | Optional integer; greater than zero when present |
| `purchase_price_paisa` | Required non-negative integer; default `0` |
| `sale_price_paisa` | Required integer greater than zero |
| `mrp_paisa` | Optional non-negative integer |
| `tax_rate_basis_points` | Required integer from `0` through `10000`; default `0` |
| `reorder_threshold_base_units` | Required non-negative integer; default `0` |
| `is_active` | Required boolean integer; default `1`; restricted to `0` or `1` |
| `created_at` | Required timestamp text |
| `updated_at` | Required timestamp text |

Money remained integer paisa. A tax percentage was represented as basis points so, for example, `13.00%` is stored exactly as `1300` without floating point.

### V2-004 — Controlled category and unit values

V2 introduced Java enums mirrored by SQLite `CHECK` constraints.

Allowed product categories:

- `TABLET`
- `SYRUP`
- `INJECTION`
- `OINTMENT`
- `CAPSULE`
- `OTHER`

Allowed units of sale:

- `TABLET`
- `CAPSULE`
- `MILLILITRE`, displayed as `ML`
- `STRIP`
- `BOTTLE`
- `VIAL`
- `TUBE`
- `OTHER`

The decision avoided free-text classification and lookup-table complexity in the single-shop MVP. The accepted tradeoff is that adding a new value requires a Java enum change and a new versioned database migration.

### V2-005 — V1 data conversion during migration

For every V1 product row, V2 performed these exact conversions:

- Copied `id`, `brand_name`, `generic_name`, and `manufacturer` into their V2 equivalents.
- Converted `dosage_form`, case-insensitively after trimming, to category:
  - `TABLET` to `TABLET`
  - `SYRUP` to `SYRUP`
  - `INJECTION` to `INJECTION`
  - `OINTMENT` to `OINTMENT`
  - `CAPSULE` to `CAPSULE`
  - Anything else or missing to `OTHER`
- Converted `dosage_form` to unit of sale:
  - `TABLET` to `TABLET`
  - `CAPSULE` to `CAPSULE`
  - `SYRUP` to `MILLILITRE`
  - `INJECTION` to `VIAL`
  - `OINTMENT` to `TUBE`
  - Anything else or missing to `OTHER`
- Copied `base_units_per_pack` into `pack_size`.
- Loaded the latest batch purchase price by descending batch `created_at`; defaulted to `0` if no batch existed.
- Loaded the latest batch selling price by descending batch `created_at`; defaulted to `1` paisa if no batch existed so the new positive-price constraint remained valid.
- Loaded MRP from the latest batch when available.
- Defaulted the tax rate to `0` basis points because no reliable legal rate could be inferred.
- Defaulted the reorder threshold to `0` base units.
- Copied `active` into `is_active`.
- Copied `created_at` into both `created_at` and the initial `updated_at`.

### V2-006 — Product uniqueness and lookup indexes

V2 added:

- `ux_product_active_name_manufacturer`, a partial unique index for active products only.
- The unique key normalizes `name` and `manufacturer` with `trim` and `lower`.
- A missing manufacturer is compared as an empty string using `coalesce`.
- Inactive duplicates are allowed so discontinued history does not block a replacement record.
- `idx_product_name` for case-insensitive name ordering and lookup.
- `idx_product_active` for active/inactive filtering.

### V2-007 — Product domain model

`src/main/java/com/nepalpharmacy/product/Product.java` added the immutable product record with UUID, all catalog fields, exact monetary values, active state, and creation/update timestamps. Its compact constructor requires the identifier and timestamps and reuses `ProductValidator` so an invalid domain object cannot be constructed normally.

`ProductDraft.java` added the mutable-input boundary as an immutable record. Its `normalized()` method:

- Trims the required name.
- Converts blank optional generic-name and manufacturer values to `null`.
- Trims nonblank optional values.
- Preserves enum, numeric, and active values without lossy conversion.

`ProductCategory.java` and `UnitOfSale.java` added the controlled enum constants and human-friendly display names used by JavaFX.

### V2-008 — Product validation

`ProductValidator.java` added all of these checks:

- Product details cannot be null.
- Name is required.
- Name is at most 160 characters.
- Optional generic name is at most 160 characters.
- Optional manufacturer is at most 160 characters.
- Category is required.
- Unit of sale is required.
- Pack size must be greater than zero when supplied.
- Default purchase price cannot be negative.
- Sale price must be greater than zero.
- MRP cannot be negative when supplied.
- Tax rate must be from 0% through 100%, represented as 0 through 10,000 basis points.
- Reorder threshold cannot be negative.
- No rule requires sale price to exceed purchase price, allowing loss-leader pricing.

`ProductValidationException.java` preserved field-specific errors in insertion order, exposed an immutable error map, and combined error messages for ordinary exception display.

`ProductNotFoundException.java` added a specific error for updates targeting a nonexistent UUID.

### V2-009 — Product application service

`ProductService.java` added:

- Constructor injection of the repository.
- Default UTC clock and random UUID generation.
- Injectable clock and UUID supplier for deterministic tests.
- `create`, which normalizes, validates, checks active duplicates, generates identity/timestamps, inserts, and returns the product.
- `update`, which loads the existing product, preserves its original `created_at`, sets a new `updated_at`, validates duplicates while excluding the current ID, updates, and returns the result.
- `findAll`, which exposes the catalog list.
- Duplicate detection only when the submitted product is active.

### V2-010 — Product repository contract and JDBC implementation

`ProductRepository.java` defined insert, update, lookup by ID, complete listing, and active normalized duplicate detection.

`JdbcProductRepository.java` implemented:

- Parameterized product insertion.
- Parameterized full product update.
- A row-count check that throws `ProductNotFoundException` when an update changes no row.
- Lookup by UUID.
- Listing ordered by active products first, then case-insensitive name and manufacturer.
- Duplicate checking with trimmed, lowercased name/manufacturer and an optional excluded product ID.
- Explicit SQL `NULL` handling for optional strings and numbers.
- Conversion between UUID/enum/ISO timestamp values and their SQLite text representation.
- Conversion of `is_active` between Java boolean and SQLite `0`/`1`.
- Try-with-resources cleanup for connections, statements, and result sets.
- Domain-facing `ProductRepositoryException` wrapping SQL failures with operation-specific messages.

`product/infrastructure/ConnectionProvider.java` initially introduced the checked-SQL-exception connection-factory abstraction. V3 later moved this cross-cutting abstraction to `shared/infrastructure` without changing its function.

### V2-011 — Product-master JavaFX screen

`ProductScreen.java` added a product form and catalog table.

Form fields:

- Required name.
- Optional generic name.
- Optional manufacturer.
- Required controlled category combo box.
- Required controlled unit-of-sale combo box.
- Optional pack size.
- Default purchase price, initialized to `0.00` NPR.
- Required sale price.
- Optional MRP.
- Tax rate percentage, initialized to `0`.
- Reorder threshold, initialized to `0` base units.
- Active checkbox, selected by default.

Form behavior:

- Adds a product when no product is being edited.
- Updates the selected product while preserving its identity and creation time.
- Clears the form and returns it to add mode.
- Populates every form field when editing.
- Supports editing through an `Edit selected` button or a row double-click.
- Displays validation, numeric parsing, repository, and success feedback.
- Parses NPR decimal text into exact integer paisa with `BigDecimal.movePointRight(2).longValueExact()`.
- Rejects values with more than two decimal places or values outside integer-paisa range.
- Parses tax percentages into exact basis points.
- Formats stored paisa back to two-decimal NPR text.
- Never uses `float` or `double` for monetary calculations; layout widths are the only doubles.

Catalog columns:

- Name
- Generic name
- Manufacturer
- Category
- Unit
- Sale NPR
- Tax
- Active/inactive status

The screen explicitly stated that batch stock and expiry belong to purchasing rather than the catalog.

### V2-012 — Application-shell integration

`PharmacyApplication.java` added or established:

- Database migration before repository/service creation.
- Product repository and product service wiring.
- A `BorderPane` application shell.
- Header title and local-first subtitle.
- Dashboard and Products navigation buttons.
- A dashboard with database status, migrations-executed count, and current-capability card.
- Product-master guidance and an `Open product master` action.
- A product-screen route that replaces the shell center.
- Scene size 1180 by 720.
- Minimum window size 980 by 620.
- Application title `Nepal Pharmacy MVP`.
- Loading of `/styles/app.css`.

### V2-013 — JavaFX styling

`app.css` defined the visual rules used by the shell and product screen:

- Light application background and system font.
- Dark green application title, navigation, and headings.
- White bordered status cards and form panels.
- Navigation hover state.
- Section-title, form-title, field-label, hint, and guidance typography.
- Primary green and secondary pale-green buttons.
- Success feedback in green and error feedback in red.
- White product-form scroll backgrounds.

### V2-014 — Product and migration tests

The V1/V2 state contained 11 tests in total.

`DatabaseBootstrapTest` covered:

- Creating nested database directories.
- Creating a new SQLite file.
- Applying V1 and V2.
- Confirming canonical product columns such as `unit_of_sale` and `updated_at`.
- Starting from a V1-only database containing a product and batch.
- Upgrading that data to V2.
- Verifying mapped name, category, unit, purchase price, and sale price.
- Running `PRAGMA foreign_key_check` after the rebuild.
- Confirming `product_batch` still referenced the rebuilt `product` table.

`ProductValidatorTest` covered:

- Accepting a valid product with missing optional fields.
- Rejecting blank and overlong names.
- Requiring unit of sale.
- Rejecting zero or negative sale price.
- Allowing sale price below purchase price.
- Rejecting negative reorder threshold.

`ProductServiceTest` covered:

- Trimming input.
- Storing controlled ISO timestamps from a fixed clock.
- Rejecting a case-insensitive duplicate active name/manufacturer.
- Allowing an inactive duplicate.

`JdbcProductRepositoryTest` covered:

- Insert.
- Lookup by UUID.
- Listing.
- Normalized active duplicate detection.
- Excluding the edited product from duplicate detection.
- Update.
- Active-to-inactive persistence.

The completed V2 implementation was verified with `./mvnw test` and `./mvnw verify`; 11 tests passed with no failures or errors.

### V2-015 — Product-master documentation

`README.md` was expanded with the completed product-master fields and workflows and stated that purchasing, batch stock, and POS were outside the first slice.

`docs/ARCHITECTURE.md` recorded the category/unit enum decision and its lookup-table tradeoff.

### V2 boundary

V2 intentionally did not create purchase records, receive inventory, add batch quantities, build POS, process returns, report history, or add authentication. A product created in V2 had zero stock because no inventory movement was produced by product creation.

---

## V3 — Purchase entry, batch stock receipt, and supplier mini-flow

Date: 2026-09-13  
Migration: `src/main/resources/db/migration/V3__create_purchase_entry.sql`  
Purpose: let a pharmacist record one supplier purchase with multiple product/batch lines and make received base-unit quantities available through append-only inventory movements.

### V3-001 — Schema naming decision

The requirements described suppliers, batches, purchases, purchase lines, and inventory movements conceptually in plural. The existing V1 schema already used singular table names (`supplier`, `product_batch`, and `inventory_movement`), and V2 retained singular `product`. V3 kept that established convention and added singular `purchase` and `purchase_line` rather than introducing parallel plural tables.

### V3-002 — Supplier table upgrade

V3 rebuilt the preliminary supplier table through `supplier_next`, copied existing supplier rows, dropped the old table, and renamed the replacement to `supplier`.

Field changes:

| V1 supplier field | V3 result |
|---|---|
| `id` | Preserved as text UUID primary key |
| `name` | Preserved, trimmed during copy, constrained to 1–160 trimmed characters |
| `phone` | Preserved, optional, maximum 40 characters |
| `address` | Preserved, optional, maximum 240 characters |
| `pan` | Preserved, optional, maximum 40 characters |
| `active` | Renamed to `is_active`, still restricted to `0` or `1` |
| `created_at` | Preserved |
| No V1 equivalent | Added required `updated_at`, initialized from `created_at` for copied rows |

No supplier deletion workflow was added. Inactive state is retained for future historical integrity.

### V3-003 — Batch table upgrade

V3 rebuilt `product_batch` through `product_batch_next`, copied existing batch rows, dropped the old table, and renamed the replacement.

Field changes:

| V1 batch field | V3 result |
|---|---|
| `id` | Preserved as text UUID primary key |
| `product_id` | Preserved as required foreign key to `product(id)` |
| `supplier_id` | Removed; supplier belongs to the purchase header rather than batch identity |
| `batch_number` | Preserved, trimmed during copy, constrained to 1–80 trimmed characters |
| `expiry_date` | Preserved as required ISO date text by application convention |
| No V1 equivalent | Added optional `manufacturing_date` |
| `purchase_price_paisa` | Preserved as required non-negative integer paisa |
| `selling_price_paisa` | Removed from batch; product-master sale price remains the current catalog value |
| `mrp_paisa` | Removed from batch in this slice; product master retains optional MRP |
| `created_at` | Preserved |

Copied V1 rows received `NULL` manufacturing dates because V1 had no source value.

The table retained a case-sensitive table-level unique constraint for `(product_id, batch_number, expiry_date)`. V3 also added a case-insensitive unique identity index, described below, so normal application matching treats batch-number casing as equivalent.

### V3-004 — Inventory movement replacement

V3 dropped the preliminary V1 `inventory_movement` table and recreated it in the exact shape needed for receipt movements:

| Column | Database rule |
|---|---|
| `id` | Text UUID primary key |
| `batch_id` | Required foreign key to `product_batch(id)` |
| `movement_type` | Required; currently only `PURCHASE_RECEIPT` is allowed |
| `quantity_base_units` | Required positive integer |
| `reference_id` | Required foreign key to the originating `purchase(id)` |
| `created_at` | Required timestamp text |

Important migration detail: preliminary V1 movement rows are not copied into the V3 movement shape. This is based on the project invariant and slice order that the product master had no stock-entry workflow and V3 is the first supported way stock enters the system. Existing suppliers and batches are copied; the unused preliminary movement structure is replaced. A database that was manually populated through the V1 movement table outside the application must be backed up and separately reconciled before applying V3.

The new V3 movement model is append-only in application code. No update or delete method was added.

### V3-005 — Purchase header table

V3 added `purchase`:

| Column | Database rule |
|---|---|
| `id` | Text UUID primary key |
| `supplier_id` | Required foreign key to `supplier(id)` |
| `purchase_date` | Required ISO date text by application convention |
| `invoice_number` | Optional text, maximum 80 characters |
| `total_amount_paisa` | Required non-negative integer paisa |
| `created_at` | Required ISO timestamp text by application convention |
| `created_by` | Nullable text placeholder because authentication is intentionally deferred |

Purchase headers have no update or delete UI. They are treated as completed financial records once saved.

### V3-006 — Purchase-line table

V3 added `purchase_line`:

| Column | Database rule |
|---|---|
| `id` | Text UUID primary key |
| `purchase_id` | Required foreign key to `purchase(id)` |
| `batch_id` | Required foreign key to `product_batch(id)` |
| `quantity_received_base_units` | Required integer greater than zero |
| `unit_purchase_price_paisa` | Required non-negative integer paisa |
| `line_total_paisa` | Required non-negative integer paisa |

The application calculates line total with exact integer multiplication. It does not accept a user-edited total.

### V3-007 — V3 indexes and stock view

V3 added:

- `ux_product_batch_identity` on product ID, case-insensitive batch number, and expiry date. This enforces physical-batch reuse even when users type different letter casing.
- `idx_product_batch_product_expiry` for product batch and FEFO lookups.
- `idx_purchase_supplier_date` for supplier/date purchase access.
- `idx_purchase_line_purchase` for loading lines of a purchase.
- `idx_purchase_line_batch` for batch-linked purchase-line access.
- `idx_inventory_movement_batch` for stock aggregation.
- `idx_inventory_movement_reference` for tracing movements to purchase headers.

V3 recreated `batch_stock` to sum `quantity_base_units` from the new movement shape. It still groups by batch and exposes the computed result as `quantity_base_units`. There is no stored on-hand quantity column.

### V3-008 — Shared infrastructure refactor

The connection-provider abstraction was cross-cutting once supplier, inventory, and purchasing repositories were introduced.

- Removed `src/main/java/com/nepalpharmacy/product/infrastructure/ConnectionProvider.java`.
- Added the equivalent interface at `src/main/java/com/nepalpharmacy/shared/infrastructure/ConnectionProvider.java`.
- Updated `JdbcProductRepository` to import the shared interface.
- Kept the same single method: `Connection open() throws SQLException`.
- Added `shared/infrastructure/DataAccessException.java` for non-product JDBC failures.
- `DataAccessException` keeps an operation-specific message and original cause.

This move prevented inventory, party, and purchasing packages from depending on product infrastructure.

### V3-009 — Supplier domain and service files

Added `party/SupplierDraft.java`:

- Holds name, phone, address, PAN, and active state.
- Trims required name.
- Converts blank optional text to `null`.
- Trims nonblank optional values.

Added `party/Supplier.java`:

- Immutable UUID supplier model.
- Includes name, phone, address, PAN, active state, `createdAt`, and `updatedAt`.
- Requires ID and timestamps.
- Reuses supplier validation during construction.

Added `party/SupplierValidator.java`:

- Rejects missing supplier details.
- Requires a nonblank name.
- Limits name to 160 characters.
- Limits phone to 40 characters.
- Limits address to 240 characters.
- Limits PAN to 40 characters.

Added `party/SupplierValidationException.java`:

- Preserves field-level validation errors in insertion order.
- Exposes an immutable error map.
- Combines messages for general exception display.

Added `party/SupplierRepository.java` with insert, lookup-by-ID, and active-supplier-list operations.

Added `party/SupplierService.java`:

- Normalizes and validates supplier drafts.
- Generates a random UUID.
- Uses a UTC clock for creation and update timestamps.
- Inserts through the repository.
- Lists active suppliers for the purchase selector.

Added `party/infrastructure/JdbcSupplierRepository.java`:

- Inserts every supplier field with parameterized SQL.
- Supports normal connections and transaction-supplied connections.
- Finds suppliers by UUID.
- Lists only active suppliers in case-insensitive name order.
- Maps UUID, ISO timestamps, nullable text, and boolean integers.
- Uses try-with-resources and wraps ordinary SQL failures in `DataAccessException`.

### V3-010 — Inventory domain files

Added `inventory/Batch.java`:

- Immutable UUID batch model.
- Contains product UUID, batch number, expiry date, optional manufacturing date, exact batch purchase price, and creation timestamp.
- Requires ID, product ID, batch number, expiry, and creation timestamp.
- Restricts batch number to 1–80 characters.
- Rejects negative batch purchase prices.

Added `inventory/InventoryMovementType.java` with the single currently allowed value `PURCHASE_RECEIPT`.

Added `inventory/InventoryMovement.java`:

- Immutable UUID movement model.
- Contains batch UUID, movement type, positive base-unit quantity, purchase reference UUID, and creation timestamp.
- Requires all identity/reference/timestamp fields.
- Rejects zero or negative movement quantities.

Added `inventory/BatchStock.java` as the read model pairing a `Batch` with its calculated available base-unit quantity.

Added `inventory/BatchRepository.java` with the FEFO available-batch query contract.

Added `inventory/InventoryMovementRepository.java` as the movement repository contract used by the JDBC movement implementation.

### V3-011 — Inventory JDBC repositories and FEFO behavior

Added `inventory/infrastructure/JdbcBatchRepository.java`:

- Finds an existing batch within a supplied transaction by product UUID, case-insensitive batch number, and expiry date.
- Inserts a batch within the same transaction as its purchase.
- Persists optional manufacturing date as SQL `NULL` when absent.
- Maps UUID, `LocalDate`, `Instant`, and exact integer prices.
- Exposes a batch count used by transaction tests.
- Implements `findAvailableByProduct(productId, asOfDate)`.

The FEFO query:

- Joins the batch to its product.
- Requires the product to be active.
- Joins the computed `batch_stock` view.
- Filters to the requested product UUID.
- Excludes batches whose expiry is before the supplied as-of date.
- Excludes batches with zero or negative available quantity.
- Sorts by expiry ascending first.
- Uses creation timestamp and case-insensitive batch number as deterministic tie-breakers.
- Returns each complete batch together with current calculated quantity.

Added `inventory/infrastructure/JdbcInventoryMovementRepository.java`:

- Appends a movement within the purchase transaction.
- Persists ID, batch, enum name, positive base-unit quantity, purchase reference, and timestamp.
- Does not expose update or delete behavior.
- Exposes a count used by atomicity/reuse integration tests.

### V3-012 — Purchase domain files

Added `purchasing/PurchaseDraft.java`:

- Holds supplier UUID, purchase date, optional invoice, line list, and nullable creator UUID.
- Trims nonblank invoice numbers.
- Converts blank invoice numbers to `null`.
- Normalizes every non-null line.

Added `purchasing/PurchaseLineDraft.java`:

- Holds product UUID, batch number, expiry date, optional manufacturing date, base-unit quantity, and exact unit purchase price.
- Trims the batch number.
- Calculates line total with `Math.multiplyExact`.

Added `purchasing/Purchase.java`:

- Immutable purchase header model.
- Requires purchase UUID, supplier UUID, purchase date, and creation timestamp.
- Allows a nullable invoice and nullable creator UUID.
- Rejects a negative total.

Added `purchasing/PurchaseLine.java`:

- Immutable persisted line model with UUID, purchase UUID, batch UUID, base-unit quantity, unit price, and line total.
- Requires all IDs.
- Rejects non-positive quantity.
- Rejects negative price or total.
- Recomputes the expected total exactly and rejects inconsistent stored line data.

Added `purchasing/RecentPurchase.java` as a display/read model containing purchase UUID, date, supplier name, optional invoice, and total.

Added repository contracts:

- `PurchaseEntryRepository` for atomic save and recent-purchase listing.
- `PurchaseRepository` for purchase persistence support.
- `PurchaseLineRepository` for purchase-line persistence support.

### V3-013 — Purchase validation

Added `purchasing/PurchaseValidator.java` with these exact rules:

- Purchase details cannot be null.
- Supplier is required.
- Purchase date is required.
- Invoice number is optional and limited to 80 characters.
- At least one line is required.
- Every list entry must contain line details.
- Every line requires a product UUID.
- Every line requires a nonblank batch number.
- Batch number is limited to 80 characters.
- Every line requires an expiry date.
- Expiry must be strictly after the purchase date; equal or earlier dates are rejected.
- Quantity received must be a positive integer.
- Unit purchase price must be zero or greater.
- Line multiplication overflow is rejected.
- Purchase-total addition overflow is rejected.

Added `PurchaseValidationException.java` to preserve ordered, immutable, field-specific errors and supply a combined display message.

No rule forces purchase price to match the product default. The UI uses the default only as an editable starting value because batch price can differ.

### V3-014 — Purchase service

Added `purchasing/PurchaseService.java`:

- Injects the atomic purchase-entry repository.
- Uses a UTC clock by default.
- Supports an injected clock for testing or future deterministic behavior.
- Normalizes and validates the complete purchase before persistence.
- Calculates the exact total before persistence so overflow is rejected early.
- Records the purchase through the transaction repository.
- Loads the 25 most recent purchases for the reference table.

### V3-015 — Purchase, line, and transaction repositories

Added `purchasing/infrastructure/JdbcPurchaseRepository.java`:

- Inserts a purchase header using a caller-supplied transaction connection.
- Persists nullable invoice and creator fields as SQL `NULL`.
- Lists recent purchases by joining supplier names.
- Orders recent records by purchase date descending and creation timestamp descending.
- Applies a caller-provided result limit.
- Exposes a count for integration tests.

Added `purchasing/infrastructure/JdbcPurchaseLineRepository.java`:

- Inserts an immutable line using the caller-supplied transaction connection.
- Persists the received base-unit quantity, exact unit price, and exact line total.
- Exposes a count for integration tests.

Added `purchasing/infrastructure/JdbcPurchaseEntryRepository.java` as the transaction coordinator.

Its save sequence is:

1. Open one configured SQLite connection.
2. Disable auto-commit.
3. Load the selected supplier using the same connection.
4. Reject a missing supplier.
5. Reject an inactive supplier.
6. Generate a purchase UUID.
7. Recalculate the exact purchase total.
8. Insert the purchase header.
9. For each line, look up the product/batch/expiry identity.
10. Reuse the matching batch when found.
11. Otherwise generate a batch UUID and insert a new batch.
12. Generate and insert a purchase-line UUID.
13. Generate and append one `PURCHASE_RECEIPT` movement with the purchase UUID as reference.
14. Commit only after every line succeeds.
15. Roll back on validation, foreign-key, SQL, or other runtime failure.
16. Preserve rollback failure as a suppressed exception when one occurs.
17. Close the transaction connection in all outcomes.

The repository reports a failed save as `Could not save purchase; no stock was changed.` No partial header, line, batch, or movement remains after rollback.

For a reused batch, V3 retains the batch row's original batch-level purchase price. Each new receipt line still stores its own unit purchase price, preserving the exact price of that transaction.

### V3-016 — Purchase-entry JavaFX screen

Added `purchasing/ui/PurchaseScreen.java`.

Header behavior:

- Displays the `Purchase entry` title.
- Explains that stock is received by supplier, product batch, expiry, and smallest saleable base unit.
- Provides an active-supplier combo box.
- Provides a purchase-date picker defaulted to the computer's current date.
- Provides an optional supplier invoice-number field.

Inline supplier mini-flow:

- Uses a collapsed `Need a new supplier? Add one here` titled pane.
- Accepts name, phone, address, and PAN.
- Requires name and leaves the other fields optional.
- Creates the supplier as active.
- Refreshes the supplier list.
- Automatically selects the supplier just created.
- Clears the mini-form after success.
- Displays supplier validation and persistence errors in the main feedback area.

Line editor:

- Lists only active products.
- Displays product name, optional manufacturer, and the product's base unit in the selector.
- Accepts required batch number.
- Accepts required expiry date.
- Accepts optional manufacturing date.
- Accepts required integer quantity explicitly labelled as base units.
- Accepts required unit purchase price in NPR per base unit.
- Copies the selected product's default purchase price into the editable line-price field.
- Converts the NPR value exactly into integer paisa using `BigDecimal`.
- Validates each draft line relative to the current purchase date before adding it.
- Clears line-entry controls after adding a valid line.
- Supports adding multiple lines before saving.

Draft-line table:

- Product column.
- Batch column.
- Expiry column.
- Quantity column.
- Unit NPR column.
- Exact line-total column.
- `Remove selected` action.
- Empty-state message when no lines exist.

Save behavior:

- Displays an exact total recalculated from all draft lines.
- Uses `Math.addExact` so UI total overflow is visible rather than silently wrapping.
- Provides `Save purchase and receive stock`.
- Submits supplier, date, optional invoice, every draft line, and nullable `createdBy`.
- Clears invoice and draft lines after successful save.
- Resets purchase date to today after successful save.
- Keeps the selected supplier for convenient consecutive receipts.
- Refreshes the recent-purchase list.
- Confirms supplier, total NPR, and that batch stock is available.
- Displays all domain errors together, one per line.
- Displays transaction errors without partially changing stock.

Recent-purchase table:

- Shows the latest 25 receipts only.
- Contains date, supplier, invoice, and total NPR columns.
- Displays an empty-state message.
- Is a reference list, not a full reporting or editing screen.

### V3-017 — Application-shell and navigation changes

Modified `PharmacyApplication.java`:

- Uses the shared `ConnectionProvider` for all repositories.
- Constructs `SupplierService` with `JdbcSupplierRepository`.
- Constructs `PurchaseService` with `JdbcPurchaseEntryRepository`.
- Adds a `Purchase entry` navigation button.
- Adds a route that creates and displays `PurchaseScreen`.
- Changes the dashboard capability text from `Product master` to `Product master + purchase entry`.
- Changes dashboard guidance to explain the product-then-purchase workflow.
- Adds a `Record a purchase` primary action next to `Open product master`.
- Leaves the existing window size, header, dashboard, and product route intact.

### V3-018 — Styling changes

Modified `app.css`:

- Added white background rules for `.purchase-scroll` and its viewport.
- Added `.purchase-total` with 18-pixel, bold, dark-green text.
- Reused existing form-panel, field-label, button, and feedback styles.
- Did not add a separate visual framework or theme dependency.

### V3-019 — Documentation changes

Modified `docs/ARCHITECTURE.md`:

- Added the purchase-receipt transaction sequence.
- Recorded that header, batches, lines, and movements are one SQLite transaction.
- Recorded that any failure rolls back the whole receipt.
- Reaffirmed that `batch_stock` and the FEFO query calculate inventory at read time.
- Reaffirmed that product and batch rows have no editable on-hand quantity.

Modified `README.md`:

- Changed the current capability statement to include purchase entry.
- Added supplier, header, batch, pricing, append-only movement, reuse, FEFO, transaction, and recent-list behavior.
- Kept POS, sales, returns, authentication, and full purchase-history reporting explicitly out of scope.

### V3-020 — Migration-test adjustments

Modified `DatabaseBootstrapTest`:

- Changed a new database's expected migration count from 2 to 3.
- Changed the V1-upgrade expected migration count from 1 to 2 because both V2 and V3 now apply.
- Expanded the new-schema assertion to require all six current business tables: `product`, `supplier`, `product_batch`, `purchase`, `purchase_line`, and `inventory_movement`.
- Retained canonical product-column assertions.
- Retained the V1 data-upgrade scenario.
- Retained `PRAGMA foreign_key_check`.
- Retained verification that `product_batch` references the canonical `product` table after both rebuild migrations.

### V3-021 — New purchase validation tests

Added `PurchaseValidatorTest` with three tests:

- `rejectsExpiryInThePast` proves an expiry before purchase date is rejected with the expected line-specific message.
- `rejectsAnEmptyPurchase` proves zero-line purchases are rejected.
- `rejectsNonPositiveQuantityAndNegativeUnitPrice` proves quantity must be positive and price cannot be negative; both errors are retained.

### V3-022 — New purchase/inventory integration tests

Added `JdbcPurchaseEntryRepositoryTest` using a separate JUnit temporary SQLite database for every test.

The setup:

- Runs all Flyway migrations.
- Creates a real supplier through `SupplierService` and `JdbcSupplierRepository`.
- Creates a real active product through `ProductService` and `JdbcProductRepository`.
- Constructs the real purchase transaction service and all relevant JDBC repositories.

The tests prove:

- `reusesMatchingBatchAndAppendsAStockMovement` records two purchases for the same product, batch number with different casing, and expiry; asserts one batch, two purchases, two lines, two movements, and summed stock of 15 base units.
- `rollsBackHeaderLinesBatchesAndMovementsWhenAnyLineFails` supplies one valid line and one nonexistent product UUID; the foreign-key failure occurs after earlier work has started, then the test asserts zero purchases, lines, movements, and batches.
- `returnsAvailableBatchesInFefoOrderWithCurrentQuantities` receives a later-expiry batch and a sooner-expiry batch, then asserts the sooner expiry appears first and that quantities remain 7 and 20 respectively.

### V3-023 — Verification record

Behavioral checkpoints were run after core transaction work, after JavaFX integration, and after final documentation/test adjustments.

Final commands:

```text
./mvnw test
./mvnw verify
```

Development execution explicitly pointed Maven to the installed Java 17 JDK and workspace-local Maven cache, without changing project build configuration.

Final result:

- 17 tests run.
- 0 failures.
- 0 errors.
- 0 skipped.
- JAR created at `target/pharmacy-mvp-0.1.0-SNAPSHOT.jar`.
- Maven build status: `BUILD SUCCESS`.

### V3 boundary

V3 intentionally did not add:

- POS or sales.
- Sales returns or purchase returns.
- Purchase editing or deletion.
- Full purchase-history reporting.
- Authentication or user management.
- Non-null `created_by` enforcement.
- Stock adjustments, expiry write-offs, damage, or cancellation movements.
- Cloud, synchronization, multi-branch, microservices, or accounting behavior.

---

## Documentation governance update — detailed history requirement

Date: 2026-09-13  
Schema effect: none  
Runtime behavior effect: none

### DOC-001 — Detailed report created

- Added `docs/IMPLEMENTATION_HISTORY.md`.
- Reconstructed V1, V2, and V3 from the migration files, current source, tests, documentation, verified Maven output, and the single available Git commit.
- Recorded the fact that V1 and V2 share one Git commit so the report does not misrepresent source history.
- Added detailed field-by-field migration notes.
- Added file-by-file implementation responsibilities.
- Added validation, transaction, UI, query, test, and scope details.
- Added a reusable future-entry template below.

### DOC-002 — Future reporting made mandatory

Modified `AGENTS.md` to require every future task to update this history in the same change. The rule covers code, schema, UI, tests, configuration, build, and documentation, including minor changes.

The rule also requires future entries to record:

- Files added, modified, moved, or removed.
- Database behavior.
- Domain and validation behavior.
- Tests and verification output.
- Decisions and scope exclusions.
- Historical uncertainty when evidence is unavailable.

### DOC-003 — README discoverability

Modified `README.md` to link to this report and tell contributors that `AGENTS.md` requires it to remain current.

### DOC-004 — Verification after report creation

- Ran `git diff --check`; no whitespace errors were reported.
- Ran `./mvnw verify` after adding the report and maintenance rule.
- 17 tests passed with 0 failures, 0 errors, and 0 skipped.
- Maven rebuilt the JAR successfully and reported `BUILD SUCCESS`.
- No database migration or runtime source change was introduced by the documentation-governance update.

---

## V4 — Point of sale, immutable sales, customers, and loose coupling

Date: 2026-09-13  
Migration: `src/main/resources/db/migration/V4__create_sales_pos.sql`  
Purpose: allow a pharmacist to search the product catalog, select sellable batch stock, complete a cash/QR/credit sale atomically, reduce stock through append-only movements, and receive an on-screen invoice confirmation.

### V4-001 — Loose-coupling audit and structural correction

This architectural work was completed and verified separately from the sales feature so the coupling improvement remains independently traceable.

The pre-V4 audit confirmed that:

- `ProductService`, `SupplierService`, and `PurchaseService` referenced repository interfaces rather than JDBC implementations.
- `ProductScreen` and `PurchaseScreen` accepted services and had no repository or JDBC dependencies.
- Existing lower-level `product`, `inventory`, and `party` packages had no dependency on `sales`.

The audit found one construction issue: `JdbcPurchaseEntryRepository` created multiple concrete JDBC repositories internally from a connection provider. That made the transaction coordinator both a persistence orchestrator and a secondary composition root.

The correction:

- Changed `JdbcPurchaseEntryRepository` to receive `TransactionRunner`, `SupplierRepository`, `BatchRepository`, `PurchaseRepository`, `PurchaseLineRepository`, and `InventoryMovementRepository` through its constructor.
- Removed all internal `new Jdbc...Repository(...)` construction from that coordinator.
- Kept `PurchaseService` dependent only on `PurchaseEntryRepository`.
- Built `JdbcSaleEntryRepository` with the same interface-based pattern from the start.
- Kept `SaleService` dependent only on `SaleEntryRepository` and `BatchRepository`.
- Kept `POSScreen` dependent only on `SaleService`, `CustomerService`, and `ProductService`.
- Did not introduce Spring, Guice, reflection, annotations, or any other dependency-injection framework.

A production-source search after the refactor found repository and service constructors only in `bootstrap/ApplicationContext.java`. A UI-source search found no JDBC or repository imports. A reverse-dependency search found no import of `sales` from `product`, `inventory`, `party`, `purchasing`, or `shared`.

### V4-002 — Shared transaction abstraction

Added these plain-Java transaction contracts:

| File | Exact responsibility |
|---|---|
| `shared/persistence/TransactionContext.java` | Opaque marker passed among repository interfaces during one transaction |
| `shared/persistence/TransactionWork.java` | Callback contract for work that returns a result inside a transaction |
| `shared/persistence/TransactionRunner.java` | Infrastructure-independent transaction execution contract |
| `shared/infrastructure/JdbcTransactionContext.java` | JDBC implementation that owns/exposes the transaction connection only to JDBC adapters and rejects incompatible context implementations |
| `shared/infrastructure/JdbcTransactionRunner.java` | Opens one connection, disables auto-commit, runs the callback, commits success, rolls back runtime failure, and attaches any rollback failure as a suppressed exception |

The shared abstraction prevents service/domain packages from naming `java.sql.Connection` while still allowing multiple narrow repositories to participate in the same SQLite transaction.

Modified transaction-participating repository interfaces:

- `ProductRepository` gained transaction-aware `findById`.
- `SupplierRepository` gained transaction-aware `findById`.
- `CustomerRepository` includes transaction-aware `findById` for final credit/customer validation.
- `BatchRepository` gained transaction-aware identity lookup, stock lookup, and insertion.
- `PurchaseRepository` gained transaction-aware insertion.
- `PurchaseLineRepository` gained transaction-aware insertion.
- `InventoryMovementRepository` gained transaction-aware append.
- `SaleRepository` and `SaleLineRepository` were introduced with transaction-aware operations.

Modified JDBC adapters to unwrap `JdbcTransactionContext` only inside infrastructure code. Their ordinary read/create methods continue to open their own short-lived connections where a multi-repository transaction is unnecessary.

`shared/infrastructure/DataAccessException.java` also gained a message-only constructor. This avoids representing intentional persistence-state failures, such as a missing invoice counter, with an artificial `null` cause while preserving the existing message-and-cause constructor for SQL failures.

### V4-003 — Single production composition point

Added `bootstrap/ApplicationContext.java` as the only production object-construction location for repositories and services.

It creates exactly one instance of each adapter/service needed by the current application:

- One shared `ConnectionProvider` backed by `DatabaseBootstrap.openConnection`.
- One shared `JdbcTransactionRunner`.
- Product, supplier, customer, batch, inventory-movement, purchase, purchase-line, sale, and sale-line JDBC repositories.
- Purchase and sale transaction coordinators assembled from repository interfaces.
- Product, supplier, customer, purchase, and sale services.

Modified `PharmacyApplication.java` so startup now:

1. Builds and migrates `DatabaseBootstrap`.
2. Creates one `ApplicationContext`.
3. Retrieves service instances from the context.
4. Passes only the specific services required by each screen.

`PharmacyApplication` no longer contains repeated repository/service wiring. Test fixtures still construct isolated implementations directly because they are not production composition points.

### V4-004 — Existing purchase coordinator refactor

Modified `purchasing/infrastructure/JdbcPurchaseEntryRepository.java` to use the new `TransactionRunner` and injected narrow interfaces without changing its purchase behavior.

Modified supporting interfaces and JDBC implementations in `party`, `product`, `inventory`, and `purchasing` so supplier lookup, product lookup, matching-batch reuse/creation, purchase insertion, line insertion, and movement insertion all use the transaction context supplied by the runner.

Modified `JdbcPurchaseEntryRepositoryTest` fixture wiring to supply those collaborators explicitly. The existing batch-reuse, rollback, and FEFO tests continued to pass before sales code was added: 17 tests, 0 failures, 0 errors, and 0 skipped. This checkpoint demonstrated that the coupling refactor preserved V1-V3 behavior.

### V4-005 — Customer schema

Migration V4 added singular `customer`, matching the established singular-table convention.

| Column | SQLite type and rule | Meaning |
|---|---|---|
| `id` | `TEXT PRIMARY KEY` | UUID text identifier |
| `name` | required text, trimmed length 1-160 | Customer display name |
| `phone` | nullable text, maximum 40 characters | Optional contact number |
| `address` | nullable text, maximum 240 characters | Optional address |
| `is_active` | integer constrained to 0/1, default 1 | Soft-active flag |
| `created_at` | required text | ISO-8601 creation instant |
| `updated_at` | required text | ISO-8601 last-update instant |

Added `idx_customer_active_name` on active state and case-insensitive name so the POS customer selector can list active customers predictably.

### V4-006 — Invoice counter and immutable sale schema

Migration V4 added `invoice_counter` with a positive `next_value` and seeded the `SALE` counter at 1.

Migration V4 added `sale`:

| Column | Rule |
|---|---|
| `id` | UUID text primary key |
| `customer_id` | Nullable FK to `customer`; populated when any payment method selects a customer and mandatory in domain validation for credit |
| `sale_date` | Required ISO-8601 local-date text |
| `invoice_number` | Required positive integer with a unique constraint |
| `payment_method` | Required `CASH`, `QR`, or `CREDIT` value |
| `total_amount_paisa` | Required positive integer-paisa total |
| `created_at` | Required ISO-8601 instant text |
| `created_by` | Nullable UUID text reserved for later authentication/audit integration |

Migration V4 added `sale_line`:

| Column | Rule |
|---|---|
| `id` | UUID text primary key |
| `sale_id` | Required FK to `sale` |
| `batch_id` | Required FK to `product_batch` |
| `quantity_sold_base_units` | Required positive integer quantity in the product's base saleable unit |
| `unit_sale_price_paisa` | Required positive price snapshot |
| `line_total_paisa` | Required positive exact line-total snapshot |

Added indexes for sale date, sale customer, sale-line sale, and sale-line batch. No update or delete repository operation was added for completed sales or lines, keeping invoices immutable in this slice.

### V4-007 — Inventory movement direction decision and migration

V4 selected movement-schema option (a): store positive movement magnitudes and apply direction in `batch_stock`.

- `PURCHASE_RECEIPT` adds quantity.
- `SALE` subtracts quantity.
- Reserved future `SALE_RETURN` adds quantity.

This retains a simple database constraint that `quantity_base_units > 0`; movement type communicates business direction. Application code appends a positive `SALE` movement and never overwrites a stock total.

To extend the V3 constraint, V4:

1. Dropped the existing `batch_stock` view.
2. Created `inventory_movement_next` with allowed types `PURCHASE_RECEIPT`, `SALE`, and `SALE_RETURN`.
3. Copied every V3 movement column and row unchanged.
4. Dropped the old movement table.
5. Renamed the replacement to `inventory_movement`.
6. Recreated movement indexes.
7. Recreated `batch_stock` with the movement-direction `CASE` expression.

The V3 `reference_id` purchase foreign key was deliberately changed to required text without a single-table foreign key. V4 movements can refer to either a purchase or sale, and SQLite cannot express a polymorphic foreign key to both tables. The application transaction coordinators create the referenced record and movement together. A future dedicated reference structure can replace this if more movement families require database-level polymorphic integrity.

Rollback is forward-only through Flyway: the applied migration is not edited or automatically reversed. Existing V3 movement data is preserved by the copy. A deployment requiring downgrade must restore a pre-V4 database backup or use a separately reviewed forward migration.

### V4-008 — Customer domain, validation, repository, and service

Added:

| File | Behavior |
|---|---|
| `party/CustomerDraft.java` | Captures name, phone, address, and active input; trims values and converts blank optional text to null |
| `party/Customer.java` | Immutable UUID/name/contact/active/timestamp customer record |
| `party/CustomerValidator.java` | Requires name, enforces 160-character name, 40-character phone, and 240-character address limits |
| `party/CustomerValidationException.java` | Exposes ordered field-level validation messages |
| `party/CustomerRepository.java` | Narrow insert, ID lookup, transaction ID lookup, and active-list contract |
| `party/CustomerService.java` | Normalizes/validates input, creates UUID and UTC timestamps, persists, and lists active customers |
| `party/infrastructure/JdbcCustomerRepository.java` | SQLite insert, ordinary/transaction lookup, active case-insensitive name listing, mapping, and error translation |

Customer creation is intentionally a minimal POS-adjacent flow, not a full customer-management or credit-ledger module.

### V4-009 — Sales domain records

Added:

| File | Behavior |
|---|---|
| `sales/PaymentMethod.java` | Controlled `CASH`, `QR`, and `CREDIT` values with UI labels |
| `sales/SaleDraft.java` | Workflow input value containing optional customer, sale date, payment, line list, and optional creator; normalization copies the line list |
| `sales/SaleLineDraft.java` | Batch, base-unit quantity, and current unit-price input; uses `Math.multiplyExact` for the line total |
| `sales/Sale.java` | Immutable completed header with invoice number and total snapshot |
| `sales/SaleLine.java` | Immutable completed line with batch, quantity, unit-price snapshot, and line-total snapshot |
| `sales/SaleReceipt.java` | Completed sale plus receipt lines returned to the UI |
| `sales/SaleReceiptLine.java` | Product/batch/expiry/quantity/price read model for confirmation display |
| `sales/SaleValidationException.java` | Ordered field-level sale errors |

`created_by` remains nullable because authentication is not part of V4.

### V4-010 — Sale validation and exact totals

Added `sales/SaleValidator.java`. Validation is outside JavaFX and is run first against submitted shape, then again against live transaction data.

It rejects:

- A null draft.
- Missing sale date.
- Missing payment method.
- Credit/Udharo without a customer UUID.
- A sale with zero lines.
- A null line or missing batch UUID.
- Zero or negative quantity.
- Zero or negative unit sale price.
- Multiplication or addition overflow.
- A zero/non-positive aggregate total.
- A missing selected batch at save time.
- A batch whose expiry is before the sale date.
- Insufficient live stock.

Repeated lines for the same batch are aggregated with `Math.addExact` before stock comparison, preventing several individually valid lines from collectively overselling one batch.

`SaleLineDraft` owns exact line multiplication. `SaleValidator` owns exact aggregate total calculation. The UI requests these calculations through `SaleService`; it does not calculate the persisted total or decide sale validity.

### V4-011 — Narrow sale repositories and atomic transaction

Added three separate repository contracts:

- `SaleRepository`: invoice allocation, header insert, and diagnostic/test count.
- `SaleLineRepository`: line insert and diagnostic/test count.
- `SaleEntryRepository`: one complete atomic sale operation returning a receipt.

Added corresponding JDBC adapters:

- `JdbcSaleRepository` reads the current counter and conditionally increments it inside the active transaction, inserts the header, and exposes count/counter reads used by integration tests.
- `JdbcSaleLineRepository` inserts immutable line snapshots and exposes a test count.
- `JdbcSaleEntryRepository` coordinates the entire transaction through interfaces.

The sale transaction performs these operations in order:

1. Reloads and checks the selected customer when present; missing/inactive customers are rejected.
2. Loads each distinct selected batch and computed stock on the same connection.
3. Loads each batch's product and rejects missing/inactive products.
4. Replaces UI-supplied draft prices with each product's current persisted sale price.
5. Revalidates expiry, combined requested quantity, live availability, line totals, and sale total.
6. Allocates the next invoice number.
7. Inserts the sale header.
8. Inserts every immutable sale line.
9. Appends one positive-magnitude `SALE` movement per line.
10. Commits and returns the sale confirmation, or rolls everything back on any runtime failure.

Because the counter increment occurs in the same transaction, a failure after allocation rolls back the counter with the header, lines, and movements. Committed invoices are unique and strictly sequential; failed attempts do not create gaps or permanently consume a number.

### V4-012 — Sale service, product search, and FEFO reuse

Added `sales/SaleService.java`. It depends only on `SaleEntryRepository`, `BatchRepository`, and a `Clock`.

Its operations:

- Return available batches for a product/date using the existing inventory FEFO query.
- Prepare and validate an individual draft line.
- Calculate an exact running total for display.
- Normalize, validate, timestamp, and record a complete sale.

Modified `ProductRepository`, `ProductService`, and `JdbcProductRepository` with a bounded active-name search used by POS:

- Null queries normalize to an empty query.
- User text is trimmed.
- Results contain active products only.
- Matching is case-insensitive substring matching through SQLite `instr(lower(name), lower(?))`.
- Results are ordered case-insensitively by product name and manufacturer.
- The service limits the POS result list to 30 products.

The existing `BatchRepository.findAvailableByProduct` query remains the source of FEFO behavior. It excludes zero-stock and expired rows and orders by expiry ascending. V4 did not copy FEFO logic into `POSScreen`.

Modified `InventoryMovementType.java` to add `SALE` and the migration-reserved `SALE_RETURN` beside `PURCHASE_RECEIPT`.

### V4-013 — JavaFX POS screen

Added `sales/ui/POSScreen.java` with service-only constructor dependencies.

The screen provides:

- Sale date, defaulted to the current local date.
- Product-name text search and a matching active-product selector.
- Batch selector populated from `SaleService.findAvailableBatches`.
- Automatic selection of the first FEFO result and manual override through the same selector.
- Batch detail showing current available base units and the product sale price.
- Positive integer base-unit quantity entry.
- An add-line action that calls `SaleService.prepareLine`.
- A draft table with product, batch, expiry, quantity, unit price, and line total.
- Removal of a selected unsaved line.
- A running total obtained from `SaleService.calculateTotal`.
- Payment selection for cash, QR/digital, and credit/Udharo.
- An active-customer selector shown for credit/Udharo and required by service validation there; cash/QR use the permitted no-customer walk-in path.
- An inline expandable customer form inside the credit panel for name, phone, and address; successful creation refreshes and selects the new customer.
- A complete-sale action that calls `SaleService.record` and displays field/domain/persistence errors.
- A read-only on-screen invoice confirmation with invoice number, date, payment, optional customer, product, batch, expiry, quantity, unit price, line total, and grand total.
- Post-save clearing of draft lines, total reset, stock refresh, and reset to cash.

Changing away from credit hides the customer panel and clears its selection, preventing a hidden customer from being attached accidentally. The domain and schema still permit a nullable customer for cash/QR as required.

The screen performs input parsing and presentation only. Final price snapshotting, total calculation for persistence, expiry rejection, stock sufficiency, customer requirements, and transaction behavior remain in domain/service/repository code.

### V4-014 — Application shell and CSS

Modified `PharmacyApplication.java`:

- Added customer and sale service fields supplied by `ApplicationContext`.
- Added the `Point of sale` navigation button and route.
- Added an `Open point of sale` dashboard action.
- Changed the capability card to `Product + purchasing + POS`.
- Updated dashboard guidance to describe batch/expiry receipt followed by FEFO-guided cash, QR, or credit sale.
- Continued constructing screens with only their required services.

Modified `styles/app.css`:

- Added `pos-scroll` and its viewport to the shared white-background scroll-pane rule.
- Added `invoice-view` with a monospaced 13-pixel font for aligned on-screen receipt content.
- Reused the existing form, button, feedback, table-hint, and purchase-total styles instead of adding a duplicate POS style system.

### V4-015 — Documentation changes

Modified `docs/ARCHITECTURE.md`:

- Renamed the heading from `Initial architecture` to `Architecture` because it now describes the evolved system.
- Added the `ApplicationContext` composition rule.
- Added service/repository/screen dependency boundaries.
- Recorded the one-way `sales` dependency direction.
- Documented the live-validation and atomic sale transaction.
- Documented positive movement magnitudes and signed stock-view behavior.

Modified `README.md`:

- Added a Point of sale capability section.
- Listed active-product search, FEFO suggestion/manual override, transaction-time stock/expiry validation, payment methods, inline customers, invoice allocation, immutable snapshots, stock movements, and confirmation display.
- Removed the now-stale statement that POS/sales were outside completed slices.
- Updated the history link wording from V1-V3 to V1-V4.
- Listed the exact V4 exclusions.

Modified the header, source-history note, V4 entry, and current responsibility index in this file so the report reflects schema V4 and all completed slices.

### V4-016 — Database bootstrap and migration tests

Modified `DatabaseBootstrapTest.java`:

- Changed the expected current schema version from 3 to 4.
- Added `customer`, `invoice_counter`, `sale`, and `sale_line` to the expected application tables, making the expected count 10.
- Changed the V1-to-current upgrade expectation from two pending migrations to three (`V2`, `V3`, and `V4`).
- Retained the foreign-key enforcement assertion.

These checks prove both a fresh database and an existing V1 database reach schema V4 through Flyway without editing an older migration.

### V4-017 — Validation and JDBC integration tests

Added `sales/SaleValidatorTest.java` with five tests:

- Rejects requested quantity greater than live available stock.
- Rejects a batch expired before the sale date.
- Rejects credit without a customer UUID.
- Rejects a sale with no lines.
- Calculates the exact 875-paisa total for representative lines.

Added `sales/infrastructure/JdbcSaleEntryRepositoryTest.java` using temporary SQLite databases with seven tests:

- A successful sale creates one header/line/movement and reduces computed stock by the sold quantity.
- An oversell across a multi-line draft creates no header, line, or sale movement; preserves both stock amounts; and leaves the next invoice at 1.
- Available batches are returned in FEFO order.
- An expired batch with positive stock is excluded from sale suggestions.
- Two committed sales receive invoice numbers 1 and 2 and leave the counter at 3.
- Persistence ignores a stale/tampered UI draft price, snapshots the current product sale price, and calculates the persisted total from it.
- A forced line-repository failure after invoice allocation rolls back the counter, sale header, movement, and stock, proving no invoice number is consumed by a failed transaction.

Modified `JdbcProductRepositoryTest.java` to prove active-product search is case-insensitive and excludes a product after deactivation.

Modified the existing purchase integration fixture for explicit dependency injection; all prior behavioral assertions remain unchanged.

### V4-018 — Verification record

Commands run during V4:

```text
./mvnw test
./mvnw verify
```

Development execution explicitly selected the installed Java 17 JDK and workspace-local Maven cache without modifying `pom.xml` or wrapper configuration.

Checkpoints:

- After the coupling refactor and before sales: 17 tests passed.
- After the core sales transaction implementation: 27 tests passed.
- After adding price-search, price-snapshot, post-allocation rollback, and final POS acceptance corrections: 29 tests passed.
- Final `./mvnw verify`: 29 tests run, 0 failures, 0 errors, 0 skipped; JAR rebuilt at `target/pharmacy-mvp-0.1.0-SNAPSHOT.jar`; Maven reported `BUILD SUCCESS`.

### V4 boundary

V4 intentionally did not add:

- Discounts, promotions, or manual price overrides.
- Split payments.
- Sales returns, cancellation, voiding, or invoice editing.
- A credit balance, settlement, aging, or Udharo ledger.
- A full customer-management screen.
- Barcode scanning.
- Physical receipt or A4 invoice printing.
- Authentication, roles, or user-populated `created_by`.
- CBMS or tax-authority integration.
- Purchase-entry changes beyond the coupling-preserving transaction refactor.
- Reports, cloud sync, multi-branch behavior, microservices, or accounting.

---

## Current source-file responsibility index

This index describes the project after V4.

### Root and build files

| File | Current responsibility |
|---|---|
| `.gitignore` | Excludes builds, local SQLite files, IDE metadata, and OS metadata |
| `.mvn/wrapper/maven-wrapper.properties` | Maven wrapper bootstrap configuration |
| `mvnw` | Unix/macOS Maven wrapper |
| `mvnw.cmd` | Windows Maven wrapper |
| `pom.xml` | Java 17 build, dependencies, tests, and JavaFX launcher |
| `AGENTS.md` | Engineering invariants, scope rules, verification commands, and mandatory history maintenance |
| `README.md` | Setup, run instructions, current slices, and documentation links |

### Architecture and scope files

| File | Current responsibility |
|---|---|
| `docs/ARCHITECTURE.md` | Deployment model, package boundaries, data rules, composition, purchase transaction, and sale transaction |
| `docs/MVP_SCOPE.md` | Included/deferred capabilities and pilot completion criteria |
| `docs/IMPLEMENTATION_HISTORY.md` | Append-only detailed record of every version and later change |

### Application and bootstrap files

| File | Current responsibility |
|---|---|
| `PharmacyApplication.java` | JavaFX startup, service retrieval, shell, dashboard, and navigation |
| `bootstrap/ApplicationContext.java` | Single production composition point for JDBC adapters, transaction runner, coordinators, and services |
| `bootstrap/DatabaseBootstrap.java` | Database path, Flyway migration, SQLite connections, FK enforcement, and busy timeout |

### Product files

| File | Current responsibility |
|---|---|
| `product/Product.java` | Immutable canonical product |
| `product/ProductDraft.java` | Product input and text normalization |
| `product/ProductCategory.java` | Controlled product-category values |
| `product/UnitOfSale.java` | Controlled base sale-unit values |
| `product/ProductValidator.java` | Product field validation |
| `product/ProductValidationException.java` | Field-level product errors |
| `product/ProductNotFoundException.java` | Missing-product update error |
| `product/ProductRepository.java` | Product CRUD, active-name search, duplicate check, and transaction lookup contract |
| `product/ProductRepositoryException.java` | Product SQL error boundary |
| `product/ProductService.java` | Product create/update/list/search orchestration and duplicate rule |
| `product/infrastructure/JdbcProductRepository.java` | SQLite product CRUD, bounded active-name search, duplicate query, and transaction lookup |
| `product/ui/ProductScreen.java` | Product form, catalog table, edit, deactivate, parsing, and feedback |

### Party files

| File | Current responsibility |
|---|---|
| `party/Supplier.java` | Immutable supplier |
| `party/SupplierDraft.java` | Supplier input normalization |
| `party/SupplierValidator.java` | Supplier validation |
| `party/SupplierValidationException.java` | Field-level supplier errors |
| `party/SupplierRepository.java` | Supplier persistence contract |
| `party/SupplierService.java` | Supplier creation and active listing |
| `party/infrastructure/JdbcSupplierRepository.java` | SQLite supplier insert/find/list operations |
| `party/Customer.java` | Immutable customer record |
| `party/CustomerDraft.java` | Customer input and optional-text normalization |
| `party/CustomerValidator.java` | Required name and customer field-length validation |
| `party/CustomerValidationException.java` | Field-level customer errors |
| `party/CustomerRepository.java` | Narrow customer insert/find/active-list contract including transaction lookup |
| `party/CustomerService.java` | Customer creation, timestamps, and active listing |
| `party/infrastructure/JdbcCustomerRepository.java` | SQLite customer insert, find, transaction find, and active listing |

### Inventory files

| File | Current responsibility |
|---|---|
| `inventory/Batch.java` | Immutable product batch |
| `inventory/BatchStock.java` | Batch plus computed available quantity read model |
| `inventory/BatchRepository.java` | Batch identity/stock transaction operations and public FEFO query contract |
| `inventory/InventoryMovement.java` | Immutable append-only stock movement magnitude |
| `inventory/InventoryMovementType.java` | `PURCHASE_RECEIPT`, `SALE`, and reserved `SALE_RETURN` movement types |
| `inventory/InventoryMovementRepository.java` | Transaction-aware inventory movement append contract |
| `inventory/infrastructure/JdbcBatchRepository.java` | Transaction batch lookup/insert, count, and FEFO stock query |
| `inventory/infrastructure/JdbcInventoryMovementRepository.java` | Transaction movement append and integration-test count |

### Purchasing files

| File | Current responsibility |
|---|---|
| `purchasing/Purchase.java` | Immutable purchase header |
| `purchasing/PurchaseDraft.java` | Purchase input normalization |
| `purchasing/PurchaseLine.java` | Immutable persisted purchase line |
| `purchasing/PurchaseLineDraft.java` | Draft line and exact line-total calculation |
| `purchasing/PurchaseValidator.java` | Header, line, expiry, quantity, price, and overflow validation |
| `purchasing/PurchaseValidationException.java` | Field-level purchase errors |
| `purchasing/RecentPurchase.java` | Recent-purchase table read model |
| `purchasing/PurchaseEntryRepository.java` | Atomic purchase-save and recent-list contract |
| `purchasing/PurchaseRepository.java` | Purchase repository contract |
| `purchasing/PurchaseLineRepository.java` | Purchase-line repository contract |
| `purchasing/PurchaseService.java` | Purchase normalization, validation, save, and recent listing |
| `purchasing/infrastructure/JdbcPurchaseRepository.java` | Purchase insert, recent query, and test count |
| `purchasing/infrastructure/JdbcPurchaseLineRepository.java` | Purchase-line insert and test count |
| `purchasing/infrastructure/JdbcPurchaseEntryRepository.java` | Interface-driven, transaction-runner-based all-or-nothing receipt coordinator |
| `purchasing/ui/PurchaseScreen.java` | Supplier mini-flow, purchase header, repeatable lines, exact total, save, and recent list |

### Sales files

| File | Current responsibility |
|---|---|
| `sales/PaymentMethod.java` | Controlled cash, QR, and credit/Udharo payment values |
| `sales/Sale.java` | Immutable completed sale header and invoice total snapshot |
| `sales/SaleDraft.java` | Normalized sale input with optional customer and creator |
| `sales/SaleLine.java` | Immutable completed batch sale line |
| `sales/SaleLineDraft.java` | Draft batch/quantity/price and exact line-total calculation |
| `sales/SaleReceipt.java` | Completed sale confirmation returned to UI |
| `sales/SaleReceiptLine.java` | Product/batch/expiry detail for invoice confirmation |
| `sales/SaleValidator.java` | Shape, payment, expiry, aggregate stock, price, total, and overflow validation |
| `sales/SaleValidationException.java` | Field-level sale errors |
| `sales/SaleEntryRepository.java` | Atomic complete-sale contract |
| `sales/SaleRepository.java` | Narrow invoice allocation and sale-header contract |
| `sales/SaleLineRepository.java` | Narrow immutable line-insert contract |
| `sales/SaleService.java` | FEFO availability, line preparation, total calculation, and sale orchestration |
| `sales/infrastructure/JdbcSaleRepository.java` | Transactional invoice counter, header insert, and diagnostic reads |
| `sales/infrastructure/JdbcSaleLineRepository.java` | Transactional immutable sale-line insert |
| `sales/infrastructure/JdbcSaleEntryRepository.java` | Live repricing/customer/stock validation and all-or-nothing sale coordinator |
| `sales/ui/POSScreen.java` | Product search, FEFO/manual batch selection, draft lines, payment/customer flow, completion, and on-screen invoice |

### Shared files

| File | Current responsibility |
|---|---|
| `shared/infrastructure/ConnectionProvider.java` | Shared checked connection factory |
| `shared/infrastructure/DataAccessException.java` | Shared non-product persistence failure wrapper with message-only and caused forms |
| `shared/persistence/TransactionContext.java` | Infrastructure-neutral transaction marker passed through repository interfaces |
| `shared/persistence/TransactionWork.java` | Result-producing transaction callback contract |
| `shared/persistence/TransactionRunner.java` | Infrastructure-neutral transaction runner contract |
| `shared/infrastructure/JdbcTransactionContext.java` | JDBC connection carrier and checked unwrapping boundary |
| `shared/infrastructure/JdbcTransactionRunner.java` | JDBC connection lifecycle, commit, rollback, and failure translation |

### Migration and style files

| File | Current responsibility |
|---|---|
| `V1__create_inventory_foundation.sql` | Preliminary product/supplier/batch/movement schema and computed-stock view |
| `V2__create_product_master.sql` | Canonical product-table rebuild and product data migration |
| `V3__create_purchase_entry.sql` | Canonical supplier/batch receipt schema, purchases, lines, movements, indexes, and stock view |
| `V4__create_sales_pos.sql` | Customers, transactional invoice counter, immutable sales/lines, expanded movements, indexes, and signed stock view |
| `styles/app.css` | Shared shell, forms, tables, feedback, buttons, scroll panes, totals, and invoice styling |

### Test files

| File | Current responsibility |
|---|---|
| `bootstrap/DatabaseBootstrapTest.java` | Fresh V4 migration and V1-to-V4 upgrade/FK checks |
| `product/ProductValidatorTest.java` | Product validation rules |
| `product/ProductServiceTest.java` | Product normalization, timestamps, and active duplicate behavior |
| `product/infrastructure/JdbcProductRepositoryTest.java` | Product JDBC CRUD, duplicate behavior, and active case-insensitive POS search |
| `purchasing/PurchaseValidatorTest.java` | Purchase expiry, empty-line, quantity, and price rules |
| `purchasing/infrastructure/JdbcPurchaseEntryRepositoryTest.java` | Batch reuse, movement append, atomic rollback, and FEFO stock behavior |
| `sales/SaleValidatorTest.java` | Stock, expiry, credit-customer, empty-sale, and exact-total rules |
| `sales/infrastructure/JdbcSaleEntryRepositoryTest.java` | Stock reduction, rollback, FEFO, expiry, invoice sequence, price snapshot, and counter rollback behavior |

---

## Template for the next change

Copy this section to the end of the file and fill it in. Do not remove or rewrite earlier entries.

```markdown
## <Version or change name> — <short description>

Date: YYYY-MM-DD  
Migration: <migration filename or "none">  
Purpose: <why this change exists>

### <ID>-001 — Files changed

- Added: ...
- Modified: ...
- Moved: ...
- Removed: ...

### <ID>-002 — Database effect

- Tables/columns/constraints/indexes/data migration: ...
- Compatibility and rollback considerations: ...

### <ID>-003 — Domain and validation effect

- ...

### <ID>-004 — Repository and transaction effect

- ...

### <ID>-005 — UI and navigation effect

- ...

### <ID>-006 — Tests and verification

- Tests added/changed: ...
- `./mvnw test`: ...
- `./mvnw verify`: ...

### <ID>-007 — Decisions, limitations, and excluded scope

- ...
```
