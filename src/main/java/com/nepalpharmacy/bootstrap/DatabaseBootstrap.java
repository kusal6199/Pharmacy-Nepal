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

        return flyway.migrate().migrationsExecuted;
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
