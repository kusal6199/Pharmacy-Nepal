package com.nepalpharmacy.credit.infrastructure;

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

class CreditMigrationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void upgradesV5RowsAsLegacyWithoutChangingTheirValues() throws Exception {
        DatabaseBootstrap database = database("upgrade-v5");
        migrateTo(database, "5");
        try (Connection connection = database.openConnection()) {
            seedParties(connection);
            seedCatalog(connection);
            execute(connection, """
                    INSERT INTO purchase (
                        id, supplier_id, purchase_date, invoice_number,
                        total_amount_paisa, created_at, created_by
                    ) VALUES ('old-purchase', 'supplier-1', '2026-09-01', 'OLD-7',
                              12345, '2026-09-01T01:00:00Z', NULL)
                    """);
            execute(connection, """
                    INSERT INTO purchase_line (
                        id, purchase_id, batch_id, quantity_received_base_units,
                        unit_purchase_price_paisa, line_total_paisa
                    ) VALUES ('old-purchase-line', 'old-purchase', 'batch-1', 10, 100, 1000)
                    """);
            execute(connection, """
                    INSERT INTO purchase_return (
                        id, return_number, original_purchase_id, supplier_id, return_date,
                        reason, notes, total_amount_paisa, created_at, created_by
                    ) VALUES ('old-return', 1, 'old-purchase', 'supplier-1', '2026-09-02',
                              'DAMAGED', 'old note', 345, '2026-09-02T01:00:00Z', NULL)
                    """);
            execute(connection, """
                    INSERT INTO purchase_return_line (
                        id, purchase_return_id, original_purchase_line_id, product_id,
                        batch_id, quantity_returned_base_units, unit_cost_paisa,
                        line_total_paisa
                    ) VALUES ('old-return-line', 'old-return', 'old-purchase-line',
                              'product-1', 'batch-1', 2, 100, 200)
                    """);
            execute(connection, """
                    INSERT INTO sale (
                        id, customer_id, sale_date, invoice_number, payment_method,
                        total_amount_paisa, created_at, created_by
                    ) VALUES ('old-sale', 'customer-1', '2026-09-03', 12, 'CREDIT',
                              777, '2026-09-03T01:00:00Z', NULL)
                    """);
            execute(connection, """
                    INSERT INTO sales_return (
                        id, return_number, original_sale_id, return_date, reason,
                        refund_method, total_amount_paisa, notes, created_at, created_by
                    ) VALUES ('old-sales-return', 2, 'old-sale', '2026-09-04',
                              'CUSTOMER_RETURN', 'CREDIT', 111, 'sale note',
                              '2026-09-04T01:00:00Z', NULL)
                    """);
        }

        assertEquals(3, database.migrate());

        try (Connection connection = database.openConnection()) {
            assertRow(connection,
                    "SELECT invoice_number, payment_method, total_amount_paisa FROM purchase",
                    "OLD-7", "LEGACY_UNSPECIFIED", 12345);
            assertRow(connection,
                    "SELECT notes, settlement_method, total_amount_paisa FROM purchase_return",
                    "old note", "LEGACY_UNSPECIFIED", 345);
            assertEquals(1, scalar(connection,
                    "SELECT COUNT(*) FROM purchase_line WHERE purchase_id = 'old-purchase'"));
            assertEquals(1, scalar(connection,
                    "SELECT COUNT(*) FROM purchase_return_line WHERE purchase_return_id = 'old-return'"));
            assertEquals(777, scalar(connection,
                    "SELECT total_amount_paisa FROM sale WHERE id = 'old-sale' AND customer_id = 'customer-1'"));
            assertEquals(111, scalar(connection,
                    "SELECT total_amount_paisa FROM sales_return WHERE id = 'old-sales-return' AND original_sale_id = 'old-sale'"));
            assertNull(value(connection,
                    "SELECT customer_id FROM sales_return WHERE id = 'old-sales-return'"));
            assertEquals(1, scalar(connection,
                    "SELECT COUNT(*) FROM customer WHERE id = 'customer-1' AND name = 'Asha'"));
            assertEquals(1, scalar(connection,
                    "SELECT COUNT(*) FROM supplier WHERE id = 'supplier-1' AND name = 'Nepal Supplier'"));
            assertFalse(hasForeignKeyViolation(connection));
        }
    }

    @Test
    void createsRequiredNonDefaultedSettlementColumnsAndNoStoredBalances() throws Exception {
        DatabaseBootstrap database = database("shape");
        database.migrate();
        try (Connection connection = database.openConnection()) {
            Column purchaseMethod = column(connection, "purchase", "payment_method");
            Column returnMethod = column(connection, "purchase_return", "settlement_method");
            assertTrue(purchaseMethod.notNull());
            assertNull(purchaseMethod.defaultValue());
            assertTrue(returnMethod.notNull());
            assertNull(returnMethod.defaultValue());
            assertFalse(hasColumnContaining(connection, "customer", "balance"));
            assertFalse(hasColumnContaining(connection, "supplier", "balance"));
            assertFalse(hasColumnContaining(connection, "customer_account_entry", "balance"));
            assertFalse(hasColumnContaining(connection, "supplier_account_entry", "balance"));
            assertEquals(4, scalar(connection, """
                    SELECT COUNT(*) FROM sqlite_master
                    WHERE type = 'index' AND name IN (
                        'ux_customer_account_opening', 'idx_customer_account_party_date',
                        'ux_supplier_account_opening', 'idx_supplier_account_party_date')
                    """));
        }
    }

    @Test
    void databaseRejectsInvalidManualAccountEntriesAndDuplicateOpenings() throws Exception {
        DatabaseBootstrap database = database("constraints");
        database.migrate();
        try (Connection connection = database.openConnection()) {
            seedParties(connection);
            execute(connection, customerOpening("opening-1", 100));
            assertThrows(SQLException.class,
                    () -> execute(connection, customerOpening("opening-2", 200)));
            assertThrows(SQLException.class, () -> execute(connection, """
                    INSERT INTO customer_account_entry (
                        id, customer_id, entry_date, entry_type, amount_paisa,
                        payment_method, created_at
                    ) VALUES ('bad-zero', 'customer-1', '2026-09-01',
                              'PAYMENT_RECEIVED', 0, 'CASH', '2026-09-01T00:00:00Z')
                    """));
            assertThrows(SQLException.class, () -> execute(connection, """
                    INSERT INTO supplier_account_entry (
                        id, supplier_id, entry_date, entry_type, amount_paisa,
                        payment_method, created_at
                    ) VALUES ('bad-credit', 'supplier-1', '2026-09-01',
                              'PAYMENT_MADE', 100, 'CREDIT', '2026-09-01T00:00:00Z')
                    """));
            assertThrows(SQLException.class, () -> execute(connection, """
                    INSERT INTO supplier_account_entry (
                        id, supplier_id, entry_date, entry_type, amount_paisa,
                        payment_method, created_at
                    ) VALUES ('bad-opening-method', 'supplier-1', '2026-09-01',
                              'OPENING_BALANCE', 100, 'QR', '2026-09-01T00:00:00Z')
                    """));
        }
    }

    private DatabaseBootstrap database(String name) throws Exception {
        Path file = temporaryDirectory.resolve(name).resolve("pharmacy.db");
        Files.createDirectories(file.getParent());
        return new DatabaseBootstrap(file);
    }

    private static void migrateTo(DatabaseBootstrap database, String target) {
        Flyway.configure().dataSource(database.jdbcUrl(), null, null)
                .locations("classpath:db/migration").target(target).load().migrate();
    }

    private static void seedParties(Connection connection) throws SQLException {
        execute(connection, """
                INSERT INTO customer (id, name, is_active, created_at, updated_at)
                VALUES ('customer-1', 'Asha', 1, '2026-09-01T00:00:00Z', '2026-09-01T00:00:00Z')
                """);
        execute(connection, """
                INSERT INTO supplier (id, name, is_active, created_at, updated_at)
                VALUES ('supplier-1', 'Nepal Supplier', 1,
                        '2026-09-01T00:00:00Z', '2026-09-01T00:00:00Z')
                """);
    }

    private static void seedCatalog(Connection connection) throws SQLException {
        execute(connection, """
                INSERT INTO product (
                    id, name, category, unit_of_sale, purchase_price_paisa,
                    sale_price_paisa, tax_rate_basis_points,
                    reorder_threshold_base_units, is_active, created_at, updated_at
                ) VALUES ('product-1', 'Migration Product', 'TABLET', 'TABLET', 100,
                          150, 0, 0, 1, '2026-09-01T00:00:00Z', '2026-09-01T00:00:00Z')
                """);
        execute(connection, """
                INSERT INTO product_batch (
                    id, product_id, batch_number, expiry_date,
                    purchase_price_paisa, created_at
                ) VALUES ('batch-1', 'product-1', 'MIG-1', '2027-09-01',
                          100, '2026-09-01T00:00:00Z')
                """);
    }

    private static String customerOpening(String id, long amount) {
        return """
                INSERT INTO customer_account_entry (
                    id, customer_id, entry_date, entry_type, amount_paisa,
                    payment_method, created_at
                ) VALUES ('%s', 'customer-1', '2026-09-01',
                          'OPENING_BALANCE', %d, NULL, '2026-09-01T00:00:00Z')
                """.formatted(id, amount);
    }

    private static void assertRow(
            Connection connection, String sql, String first, String second, long amount)
            throws SQLException {
        try (var statement = connection.prepareStatement(sql);
             var row = statement.executeQuery()) {
            assertTrue(row.next());
            assertEquals(first, row.getString(1));
            assertEquals(second, row.getString(2));
            assertEquals(amount, row.getLong(3));
        }
    }

    private static boolean hasForeignKeyViolation(Connection connection) throws SQLException {
        try (var statement = connection.prepareStatement("PRAGMA foreign_key_check");
             var rows = statement.executeQuery()) {
            return rows.next();
        }
    }

    private static long scalar(Connection connection, String sql) throws SQLException {
        try (var statement = connection.prepareStatement(sql);
             var rows = statement.executeQuery()) {
            rows.next();
            return rows.getLong(1);
        }
    }

    private static String value(Connection connection, String sql) throws SQLException {
        try (var statement = connection.prepareStatement(sql);
             var results = statement.executeQuery()) {
            assertTrue(results.next());
            return results.getString(1);
        }
    }

    private static Column column(Connection connection, String table, String name)
            throws SQLException {
        try (var statement = connection.prepareStatement("PRAGMA table_info(" + table + ")");
             var rows = statement.executeQuery()) {
            while (rows.next()) {
                if (name.equals(rows.getString("name"))) {
                    return new Column(rows.getInt("notnull") == 1, rows.getString("dflt_value"));
                }
            }
        }
        throw new AssertionError("Missing column " + table + "." + name);
    }

    private static boolean hasColumnContaining(Connection connection, String table, String text)
            throws SQLException {
        try (var statement = connection.prepareStatement("PRAGMA table_info(" + table + ")");
             var rows = statement.executeQuery()) {
            while (rows.next()) {
                if (rows.getString("name").toLowerCase().contains(text)) return true;
            }
            return false;
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        }
    }

    private record Column(boolean notNull, String defaultValue) { }
}
