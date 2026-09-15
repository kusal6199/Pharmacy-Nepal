package com.nepalpharmacy.sales.infrastructure;

import com.nepalpharmacy.bootstrap.DatabaseBootstrap;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SalesReturnCustomerMigrationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void addsNullableReturnCustomerForeignKeyWithoutChangingExistingSaleOrReturn() throws Exception {
        DatabaseBootstrap database = database();
        Flyway.configure()
                .dataSource(database.jdbcUrl(), null, null)
                .locations("classpath:db/migration")
                .target("6")
                .load()
                .migrate();
        try (Connection connection = database.openConnection()) {
            execute(connection, """
                    INSERT INTO customer (id, name, is_active, created_at, updated_at)
                    VALUES ('customer-1', 'Asha', 1,
                            '2026-09-01T00:00:00Z', '2026-09-01T00:00:00Z')
                    """);
            execute(connection, """
                    INSERT INTO sale (
                        id, customer_id, sale_date, invoice_number, payment_method,
                        total_amount_paisa, created_at, created_by
                    ) VALUES ('walk-in-sale', NULL, '2026-09-01', 1, 'CASH',
                              200, '2026-09-01T01:00:00Z', NULL)
                    """);
            execute(connection, """
                    INSERT INTO sales_return (
                        id, return_number, original_sale_id, return_date, reason,
                        refund_method, total_amount_paisa, notes, created_at, created_by
                    ) VALUES ('existing-return', 1, 'walk-in-sale', '2026-09-02',
                              'CUSTOMER_RETURN', 'CASH', 100, NULL,
                              '2026-09-02T01:00:00Z', NULL)
                    """);
        }

        int migrationsExecuted = Flyway.configure()
                .dataSource(database.jdbcUrl(), null, null)
                .locations("classpath:db/migration")
                .target("7")
                .load()
                .migrate()
                .migrationsExecuted;

        assertEquals(1, migrationsExecuted);

        try (Connection connection = database.openConnection()) {
            assertTrue(isNullableColumn(connection, "sales_return", "customer_id"));
            assertTrue(hasForeignKey(connection, "sales_return", "customer_id", "customer"));
            assertNull(value(connection,
                    "SELECT customer_id FROM sales_return WHERE id = 'existing-return'"));
            assertNull(value(connection,
                    "SELECT customer_id FROM sale WHERE id = 'walk-in-sale'"));

            execute(connection, """
                    INSERT INTO sales_return (
                        id, return_number, original_sale_id, customer_id, return_date, reason,
                        refund_method, total_amount_paisa, notes, created_at, created_by
                    ) VALUES ('credit-return', 2, 'walk-in-sale', 'customer-1', '2026-09-03',
                              'CUSTOMER_RETURN', 'CREDIT', 100, NULL,
                              '2026-09-03T01:00:00Z', NULL)
                    """);
            assertThrows(SQLException.class, () -> execute(connection, """
                    INSERT INTO sales_return (
                        id, return_number, original_sale_id, customer_id, return_date, reason,
                        refund_method, total_amount_paisa, notes, created_at, created_by
                    ) VALUES ('orphan-return', 3, 'walk-in-sale', 'missing-customer',
                              '2026-09-03', 'CUSTOMER_RETURN', 'CREDIT', 100, NULL,
                              '2026-09-03T02:00:00Z', NULL)
                    """));
            assertEquals("customer-1", value(connection,
                    "SELECT customer_id FROM sales_return WHERE id = 'credit-return'"));
            assertFalse(hasForeignKeyViolation(connection));
        }
    }

    private DatabaseBootstrap database() throws Exception {
        Path file = temporaryDirectory.resolve("upgrade-v6/pharmacy.db");
        Files.createDirectories(file.getParent());
        return new DatabaseBootstrap(file);
    }

    private static boolean isNullableColumn(
            Connection connection, String table, String column) throws SQLException {
        try (var statement = connection.prepareStatement("PRAGMA table_info(" + table + ")");
             var rows = statement.executeQuery()) {
            while (rows.next()) {
                if (column.equals(rows.getString("name"))) {
                    return rows.getInt("notnull") == 0 && rows.getString("dflt_value") == null;
                }
            }
        }
        return false;
    }

    private static boolean hasForeignKey(
            Connection connection, String table, String column, String parent) throws SQLException {
        try (var statement = connection.prepareStatement("PRAGMA foreign_key_list(" + table + ")");
             var rows = statement.executeQuery()) {
            while (rows.next()) {
                if (column.equals(rows.getString("from"))
                        && parent.equals(rows.getString("table"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasForeignKeyViolation(Connection connection) throws SQLException {
        try (var statement = connection.prepareStatement("PRAGMA foreign_key_check");
             var rows = statement.executeQuery()) {
            return rows.next();
        }
    }

    private static String value(Connection connection, String sql) throws SQLException {
        try (var statement = connection.prepareStatement(sql);
             var rows = statement.executeQuery()) {
            assertTrue(rows.next());
            return rows.getString(1);
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        }
    }
}
