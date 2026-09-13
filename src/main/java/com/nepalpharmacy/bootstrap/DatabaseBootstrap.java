package com.nepalpharmacy.bootstrap;

import org.flywaydb.core.Flyway;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;

public final class DatabaseBootstrap {

    private static final String DATA_DIRECTORY_ENV = "PHARMACY_DATA_DIR";

    private final Path databasePath;

    public DatabaseBootstrap(Path databasePath) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath").toAbsolutePath();
    }

    public static DatabaseBootstrap inConfiguredDataDirectory() {
        String configuredDirectory = System.getenv(DATA_DIRECTORY_ENV);
        Path dataDirectory = configuredDirectory == null || configuredDirectory.isBlank()
                ? Path.of("data")
                : Path.of(configuredDirectory);
        return new DatabaseBootstrap(dataDirectory.resolve("pharmacy.db"));
    }

    public int migrate() {
        createParentDirectory();

        Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl(), null, null)
                .locations("classpath:db/migration")
                .load();

        int migrationsExecuted = flyway.migrate().migrationsExecuted;
        verifyInventoryMovementReferences();
        return migrationsExecuted;
    }

    public void verifyInventoryMovementReferences() {
        String sql = """
                SELECT movement.id, movement.movement_type, movement.reference_id
                FROM inventory_movement movement
                LEFT JOIN purchase purchase_header
                    ON purchase_header.id = movement.reference_id
                LEFT JOIN sale sale_header
                    ON sale_header.id = movement.reference_id
                LEFT JOIN sales_return sales_return_header
                    ON sales_return_header.id = movement.reference_id
                LEFT JOIN purchase_return purchase_return_header
                    ON purchase_return_header.id = movement.reference_id
                WHERE (movement.movement_type = 'PURCHASE_RECEIPT'
                       AND purchase_header.id IS NULL)
                   OR (movement.movement_type = 'SALE'
                       AND sale_header.id IS NULL)
                   OR (movement.movement_type = 'SALE_RETURN'
                       AND sales_return_header.id IS NULL)
                   OR (movement.movement_type = 'PURCHASE_RETURN'
                       AND purchase_return_header.id IS NULL)
                   OR movement.movement_type NOT IN (
                       'PURCHASE_RECEIPT', 'SALE', 'SALE_RETURN', 'PURCHASE_RETURN')
                LIMIT 1
                """;

        try (Connection connection = openConnection();
             var statement = connection.prepareStatement(sql);
             var results = statement.executeQuery()) {
            if (results.next()) {
                throw new IllegalStateException(
                        "Inventory movement reference integrity check failed: movement "
                                + results.getString("id") + " of type "
                                + results.getString("movement_type") + " references "
                                + results.getString("reference_id")
                                + ", which is not a matching persisted transaction.");
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Could not verify inventory movement reference integrity.", exception);
        }
    }

    public Path databasePath() {
        return databasePath;
    }

    public String jdbcUrl() {
        return "jdbc:sqlite:" + databasePath;
    }

    public Connection openConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl());
        try (var statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
            return connection;
        } catch (SQLException exception) {
            connection.close();
            throw exception;
        }
    }

    private void createParentDirectory() {
        Path parent = databasePath.getParent();
        if (parent == null) {
            return;
        }

        try {
            Files.createDirectories(parent);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create database directory: " + parent, exception);
        }
    }
}
