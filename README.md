# Nepal Pharmacy MVP

Local-first pharmacy management application scaffolded for supervised, incremental development.

## Current foundation

- Java 17
- JavaFX desktop UI
- Maven build
- SQLite local database
- Flyway schema migrations
- JUnit 5 tests

The executable opens a dashboard and a product-master screen. A pharmacist can add, list, edit, and deactivate products. Purchasing, batch stock, and POS remain separate future slices.

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

## Completed first vertical slice

The product master includes:

1. Medicine/product name, generic name, and manufacturer
2. Controlled category and base unit selections
3. Optional pack size
4. Exact purchase, sale, and MRP values stored in paisa
5. Configurable tax rate
6. Reorder threshold
7. Active/inactive status instead of deletion
8. Add, list, and edit workflows in JavaFX

Purchase entry, batch expiry, stock movements, and POS are intentionally not part of this slice.

See `docs/MVP_SCOPE.md` for the scope boundary and `docs/ARCHITECTURE.md` for the initial design.
