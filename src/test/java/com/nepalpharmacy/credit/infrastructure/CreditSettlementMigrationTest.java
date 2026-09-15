package com.nepalpharmacy.credit.infrastructure;

import com.nepalpharmacy.bootstrap.DatabaseBootstrap;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreditSettlementMigrationTest {
    private static final String[] PRESERVED_TABLES = {
            "customer", "supplier", "sale", "sales_return", "purchase", "purchase_return",
            "customer_account_entry", "supplier_account_entry"
    };

    @TempDir
    Path temporaryDirectory;

    @Test
    void upgradesV7WithoutChangingExistingRowsOrAccountEntryMetadata() throws Exception {
        DatabaseBootstrap database = schemaV7("preservation");
        List<String> before;
        try (Connection connection = database.openConnection()) {
            seedParties(connection);
            seedSourceTransactions(connection);
            seedExistingAccountEntries(connection);
            before = snapshot(connection, PRESERVED_TABLES);
        }

        assertEquals(1, database.migrate());

        try (Connection connection = database.openConnection()) {
            assertEquals(before, snapshot(connection, PRESERVED_TABLES));
            assertFalse(hasForeignKeyViolation(connection));
        }
    }

    @Test
    void upgradedV8AcceptsNewSettlementTypesAndRetainsAccountConstraints() throws Exception {
        DatabaseBootstrap database = schemaV7("constraints");
        try (Connection connection = database.openConnection()) {
            seedParties(connection);
        }

        assertEquals(1, database.migrate());

        try (Connection connection = database.openConnection()) {
            insertCustomerEntry(connection, "customer-payout-cash", "customer-1",
                    "CREDIT_PAYOUT", 100, "CASH", "CP-CASH", "cash payout");
            insertCustomerEntry(connection, "customer-payout-qr", "customer-1",
                    "CREDIT_PAYOUT", 200, "QR", "CP-QR", "QR payout");
            insertSupplierEntry(connection, "supplier-refund-cash", "supplier-1",
                    "CREDIT_REFUND_RECEIVED", 300, "CASH", "SR-CASH", "cash refund");
            insertSupplierEntry(connection, "supplier-refund-qr", "supplier-1",
                    "CREDIT_REFUND_RECEIVED", 400, "QR", "SR-QR", "QR refund");

            assertThrows(SQLException.class, () -> insertCustomerEntry(connection,
                    "bad-c-null-method", "customer-1", "CREDIT_PAYOUT", 100,
                    null, null, null));
            assertThrows(SQLException.class, () -> insertCustomerEntry(connection,
                    "bad-c-credit-method", "customer-1", "CREDIT_PAYOUT", 100,
                    "CREDIT", null, null));
            assertThrows(SQLException.class, () -> insertSupplierEntry(connection,
                    "bad-s-null-method", "supplier-1", "CREDIT_REFUND_RECEIVED", 100,
                    null, null, null));
            assertThrows(SQLException.class, () -> insertSupplierEntry(connection,
                    "bad-s-credit-method", "supplier-1", "CREDIT_REFUND_RECEIVED", 100,
                    "CREDIT", null, null));

            assertThrows(SQLException.class, () -> insertCustomerEntry(connection,
                    "bad-c-supplier-type", "customer-1", "CREDIT_REFUND_RECEIVED", 100,
                    "CASH", null, null));
            assertThrows(SQLException.class, () -> insertSupplierEntry(connection,
                    "bad-s-customer-type", "supplier-1", "CREDIT_PAYOUT", 100,
                    "CASH", null, null));
            assertThrows(SQLException.class, () -> insertCustomerEntry(connection,
                    "bad-c-zero", "customer-1", "CREDIT_PAYOUT", 0,
                    "CASH", null, null));
            assertThrows(SQLException.class, () -> insertSupplierEntry(connection,
                    "bad-s-negative", "supplier-1", "CREDIT_REFUND_RECEIVED", -1,
                    "QR", null, null));
            assertThrows(SQLException.class, () -> insertCustomerEntry(connection,
                    "bad-c-reference", "customer-1", "CREDIT_PAYOUT", 100,
                    "CASH", "r".repeat(161), null));
            assertThrows(SQLException.class, () -> insertSupplierEntry(connection,
                    "bad-s-notes", "supplier-1", "CREDIT_REFUND_RECEIVED", 100,
                    "QR", null, "n".repeat(501)));
            assertThrows(SQLException.class, () -> insertCustomerEntry(connection,
                    "bad-c-party", "missing-customer", "CREDIT_PAYOUT", 100,
                    "CASH", null, null));
            assertThrows(SQLException.class, () -> insertSupplierEntry(connection,
                    "bad-s-party", "missing-supplier", "CREDIT_REFUND_RECEIVED", 100,
                    "QR", null, null));

            insertCustomerEntry(connection, "customer-opening", "customer-1",
                    "OPENING_BALANCE", 500, null, null, null);
            insertSupplierEntry(connection, "supplier-opening", "supplier-1",
                    "OPENING_BALANCE", 600, null, null, null);
            assertThrows(SQLException.class, () -> insertCustomerEntry(connection,
                    "duplicate-c-opening", "customer-1", "OPENING_BALANCE", 700,
                    null, null, null));
            assertThrows(SQLException.class, () -> insertSupplierEntry(connection,
                    "duplicate-s-opening", "supplier-1", "OPENING_BALANCE", 800,
                    null, null, null));

            assertEquals(3, scalar(connection, "SELECT COUNT(*) FROM customer_account_entry"));
            assertEquals(3, scalar(connection, "SELECT COUNT(*) FROM supplier_account_entry"));
            assertEquals(4, scalar(connection, """
                    SELECT COUNT(*) FROM sqlite_master
                    WHERE type = 'index' AND name IN (
                        'ux_customer_account_opening', 'idx_customer_account_party_date',
                        'ux_supplier_account_opening', 'idx_supplier_account_party_date')
                    """));
            assertEquals(0, scalar(connection, """
                    SELECT COUNT(*) FROM sqlite_master
                    WHERE type = 'table' AND name IN (
                        'customer_account_entry_next', 'supplier_account_entry_next')
                    """));
            assertTrue(hasForeignKey(
                    connection, "customer_account_entry", "customer_id", "customer"));
            assertTrue(hasForeignKey(
                    connection, "supplier_account_entry", "supplier_id", "supplier"));
            assertFalse(hasForeignKeyViolation(connection));
        }
    }

    @Test
    void ambiguousV7NullPaymentMethodsBlockV8BeforeAnyDataOrSchemaChange() throws Exception {
        assertAmbiguousSettlementBlocksUpgrade(true);
        assertAmbiguousSettlementBlocksUpgrade(false);
    }

    private void assertAmbiguousSettlementBlocksUpgrade(boolean customerEntry) throws Exception {
        String role = customerEntry ? "customer" : "supplier";
        DatabaseBootstrap database = schemaV7("ambiguous-" + role);
        List<String> before;
        try (Connection connection = database.openConnection()) {
            seedParties(connection);
            if (customerEntry) {
                insertCustomerEntry(connection, "ambiguous-payment", "customer-1",
                        "PAYMENT_RECEIVED", 100, null, "LEGACY", "unknown method");
            } else {
                insertSupplierEntry(connection, "ambiguous-payment", "supplier-1",
                        "PAYMENT_MADE", 100, null, "LEGACY", "unknown method");
            }
            before = snapshot(connection,
                    customerEntry ? "customer_account_entry" : "supplier_account_entry");
        }

        RuntimeException exception = assertThrows(RuntimeException.class, database::migrate);

        assertTrue(allMessages(exception).contains(
                "v8_" + role + "_payment_method_preflight"
                        + ".repair_null_payment_method_before_v8"));
        try (Connection connection = database.openConnection()) {
            assertEquals(7, scalar(connection, """
                    SELECT CAST(version AS INTEGER)
                    FROM flyway_schema_history
                    WHERE success = 1
                    ORDER BY installed_rank DESC LIMIT 1
                    """));
            assertEquals(before, snapshot(connection,
                    customerEntry ? "customer_account_entry" : "supplier_account_entry"));
            assertEquals(0, scalar(connection, """
                    SELECT COUNT(*) FROM sqlite_master
                    WHERE type = 'table' AND name IN (
                        'customer_account_entry_next', 'supplier_account_entry_next')
                    """));
            assertFalse(hasForeignKeyViolation(connection));
        }
    }

    private DatabaseBootstrap schemaV7(String name) throws Exception {
        Path file = temporaryDirectory.resolve(name).resolve("pharmacy.db");
        Files.createDirectories(file.getParent());
        DatabaseBootstrap database = new DatabaseBootstrap(file);
        Flyway.configure()
                .dataSource(database.jdbcUrl(), null, null)
                .locations("classpath:db/migration")
                .target("7")
                .load()
                .migrate();
        return database;
    }

    private static void seedParties(Connection connection) throws SQLException {
        execute(connection, """
                INSERT INTO customer (
                    id, name, phone, address, is_active, created_at, updated_at
                ) VALUES (
                    'customer-1', 'Ram', '9800000000', 'Kathmandu', 1,
                    '2026-09-01T00:00:00Z', '2026-09-02T00:00:00Z'
                )
                """);
        execute(connection, """
                INSERT INTO supplier (
                    id, name, phone, address, pan, is_active, created_at, updated_at
                ) VALUES (
                    'supplier-1', 'Nepal Supplier', '9811111111', 'Bhaktapur', 'PAN-1', 0,
                    '2026-09-01T00:00:00Z', '2026-09-03T00:00:00Z'
                )
                """);
    }

    private static void seedSourceTransactions(Connection connection) throws SQLException {
        execute(connection, """
                INSERT INTO sale (
                    id, customer_id, sale_date, invoice_number, payment_method,
                    total_amount_paisa, created_at, created_by
                ) VALUES (
                    'walk-in-sale', NULL, '2026-09-04', 1, 'CASH', 100000,
                    '2026-09-04T01:00:00Z', 'cashier-sale'
                )
                """);
        execute(connection, """
                INSERT INTO sales_return (
                    id, return_number, original_sale_id, customer_id, return_date, reason,
                    refund_method, total_amount_paisa, notes, created_at, created_by
                ) VALUES (
                    'credit-return', 1, 'walk-in-sale', 'customer-1', '2026-09-05',
                    'CUSTOMER_RETURN', 'CREDIT', 40000, 'sealed medicine',
                    '2026-09-05T01:00:00Z', 'cashier-return'
                )
                """);
        execute(connection, """
                INSERT INTO purchase (
                    id, supplier_id, purchase_date, invoice_number, payment_method,
                    total_amount_paisa, created_at, created_by
                ) VALUES (
                    'credit-purchase', 'supplier-1', '2026-09-06', 'SUP-1', 'CREDIT', 50000,
                    '2026-09-06T01:00:00Z', 'cashier-purchase'
                )
                """);
        execute(connection, """
                INSERT INTO purchase_return (
                    id, return_number, original_purchase_id, supplier_id, return_date, reason,
                    settlement_method, notes, total_amount_paisa, created_at, created_by
                ) VALUES (
                    'credit-purchase-return', 1, 'credit-purchase', 'supplier-1', '2026-09-07',
                    'DAMAGED', 'CREDIT', 'damaged pack', 10000,
                    '2026-09-07T01:00:00Z', 'cashier-purchase-return'
                )
                """);
    }

    private static void seedExistingAccountEntries(Connection connection) throws SQLException {
        insertCustomerEntry(connection, "customer-opening-before", "customer-1",
                "OPENING_BALANCE", 5000, null, "C-OPEN", "customer opening");
        insertCustomerEntry(connection, "customer-payment-before", "customer-1",
                "PAYMENT_RECEIVED", 1200, "QR", "C-PAY", "customer payment");
        insertSupplierEntry(connection, "supplier-opening-before", "supplier-1",
                "OPENING_BALANCE", 8000, null, "S-OPEN", "supplier opening");
        insertSupplierEntry(connection, "supplier-payment-before", "supplier-1",
                "PAYMENT_MADE", 2300, "CASH", "S-PAY", "supplier payment");
    }

    private static void insertCustomerEntry(
            Connection connection,
            String id,
            String partyId,
            String entryType,
            long amountPaisa,
            String paymentMethod,
            String reference,
            String notes
    ) throws SQLException {
        insertAccountEntry(connection, "customer_account_entry", "customer_id", id, partyId,
                entryType, amountPaisa, paymentMethod, reference, notes);
    }

    private static void insertSupplierEntry(
            Connection connection,
            String id,
            String partyId,
            String entryType,
            long amountPaisa,
            String paymentMethod,
            String reference,
            String notes
    ) throws SQLException {
        insertAccountEntry(connection, "supplier_account_entry", "supplier_id", id, partyId,
                entryType, amountPaisa, paymentMethod, reference, notes);
    }

    private static void insertAccountEntry(
            Connection connection,
            String table,
            String partyColumn,
            String id,
            String partyId,
            String entryType,
            long amountPaisa,
            String paymentMethod,
            String reference,
            String notes
    ) throws SQLException {
        String sql = """
                INSERT INTO %s (
                    id, %s, entry_date, entry_type, amount_paisa, payment_method,
                    reference_text, notes, created_at, created_by
                ) VALUES (?, ?, '2026-09-15', ?, ?, ?, ?, ?,
                          '2026-09-15T01:02:03Z', 'cashier-account')
                """.formatted(table, partyColumn);
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            statement.setString(2, partyId);
            statement.setString(3, entryType);
            statement.setLong(4, amountPaisa);
            statement.setString(5, paymentMethod);
            statement.setString(6, reference);
            statement.setString(7, notes);
            statement.executeUpdate();
        }
    }

    private static List<String> snapshot(Connection connection, String... tables)
            throws SQLException {
        List<String> values = new ArrayList<>();
        for (String table : tables) {
            try (var statement = connection.prepareStatement("SELECT * FROM " + table + " ORDER BY id");
                 var rows = statement.executeQuery()) {
                var metadata = rows.getMetaData();
                int rowIndex = 0;
                while (rows.next()) {
                    for (int column = 1; column <= metadata.getColumnCount(); column++) {
                        values.add(table + "[" + rowIndex + "]."
                                + metadata.getColumnName(column) + "=" + rows.getString(column));
                    }
                    rowIndex++;
                }
            }
        }
        return List.copyOf(values);
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
            return false;
        }
    }

    private static boolean hasForeignKeyViolation(Connection connection) throws SQLException {
        try (var statement = connection.prepareStatement("PRAGMA foreign_key_check");
             var rows = statement.executeQuery()) {
            return rows.next();
        }
    }

    private static String allMessages(Throwable exception) {
        StringBuilder messages = new StringBuilder();
        Throwable current = exception;
        while (current != null) {
            if (current.getMessage() != null) messages.append(current.getMessage()).append('\n');
            current = current.getCause();
        }
        return messages.toString();
    }

    private static long scalar(Connection connection, String sql) throws SQLException {
        try (var statement = connection.prepareStatement(sql);
             var rows = statement.executeQuery()) {
            assertTrue(rows.next());
            return rows.getLong(1);
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        }
    }
}
