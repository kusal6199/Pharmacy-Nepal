package com.nepalpharmacy.bootstrap;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseBootstrapTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void createsAndMigratesANewDatabase() throws Exception {
        Path databaseFile = temporaryDirectory.resolve("nested/pharmacy.db");
        DatabaseBootstrap bootstrap = new DatabaseBootstrap(databaseFile);

        int migrationsExecuted = bootstrap.migrate();

        assertEquals(6, migrationsExecuted);
        assertTrue(Files.exists(databaseFile));

        try (var connection = DriverManager.getConnection(bootstrap.jdbcUrl());
             var statement = connection.prepareStatement("""
                     SELECT COUNT(*)
                     FROM sqlite_master
                     WHERE type = 'table'
                       AND name IN ('product', 'supplier', 'product_batch', 'purchase',
                                    'purchase_line', 'inventory_movement', 'customer',
                                    'invoice_counter', 'sale', 'sale_line', 'sales_return',
                                    'sales_return_line', 'purchase_return', 'purchase_return_line',
                                    'customer_account_entry', 'supplier_account_entry')
                     """)) {
            try (var results = statement.executeQuery()) {
                assertTrue(results.next());
                assertEquals(16, results.getInt(1));
            }
        }

        try (var connection = DriverManager.getConnection(bootstrap.jdbcUrl());
             var statement = connection.prepareStatement("PRAGMA table_info(product)");
             var results = statement.executeQuery()) {
            boolean foundUnitOfSale = false;
            boolean foundUpdatedAt = false;
            while (results.next()) {
                foundUnitOfSale |= "unit_of_sale".equals(results.getString("name"));
                foundUpdatedAt |= "updated_at".equals(results.getString("name"));
            }
            assertTrue(foundUnitOfSale);
            assertTrue(foundUpdatedAt);
        }
    }

    @Test
    void upgradesThePreliminaryProductSchemaWithoutBreakingBatchReferences() throws Exception {
        Path databaseFile = temporaryDirectory.resolve("upgrade/pharmacy.db");
        DatabaseBootstrap bootstrap = new DatabaseBootstrap(databaseFile);
        Files.createDirectories(databaseFile.getParent());

        Flyway.configure()
                .dataSource(bootstrap.jdbcUrl(), null, null)
                .locations("classpath:db/migration")
                .target("1")
                .load()
                .migrate();

        String productId = "d9c73bbd-186c-47b9-bf29-9fa27379d447";
        try (var connection = DriverManager.getConnection(bootstrap.jdbcUrl());
             var product = connection.prepareStatement("""
                     INSERT INTO product (
                         id, sku, brand_name, generic_name, dosage_form, manufacturer,
                         pack_label, base_units_per_pack, active, created_at
                     ) VALUES (?, 'PCM-500', 'Paracetamol 500mg', 'Paracetamol',
                               'Tablet', 'Acme Pharma', '10 tablets', 10, 1,
                               '2026-09-13T04:00:00Z')
                     """);
             var batch = connection.prepareStatement("""
                     INSERT INTO product_batch (
                         id, product_id, batch_number, expiry_date, purchase_price_paisa,
                         selling_price_paisa, mrp_paisa, created_at
                     ) VALUES ('2fb976a0-ded5-4a19-915a-f51dac02d7fd', ?, 'B-001',
                               '2028-01-31', 1000, 1500, 1800, '2026-09-13T04:30:00Z')
                     """)) {
            product.setString(1, productId);
            product.executeUpdate();
            batch.setString(1, productId);
            batch.executeUpdate();
        }

        assertEquals(5, bootstrap.migrate());

        try (var connection = bootstrap.openConnection();
             var product = connection.prepareStatement("""
                     SELECT name, category, unit_of_sale, purchase_price_paisa, sale_price_paisa
                     FROM product WHERE id = ?
                     """)) {
            product.setString(1, productId);
            try (var results = product.executeQuery()) {
                assertTrue(results.next());
                assertEquals("Paracetamol 500mg", results.getString("name"));
                assertEquals("TABLET", results.getString("category"));
                assertEquals("TABLET", results.getString("unit_of_sale"));
                assertEquals(1000, results.getLong("purchase_price_paisa"));
                assertEquals(1500, results.getLong("sale_price_paisa"));
            }

            try (var foreignKeyCheck = connection.prepareStatement("PRAGMA foreign_key_check");
                 var violations = foreignKeyCheck.executeQuery()) {
                assertFalse(violations.next());
            }

            try (var foreignKeys = connection.prepareStatement("PRAGMA foreign_key_list(product_batch)");
                 var results = foreignKeys.executeQuery()) {
                boolean referencesProduct = false;
                while (results.next()) {
                    referencesProduct |= "product".equals(results.getString("table"));
                }
                assertTrue(referencesProduct);
            }
        }
    }

    @Test
    void startupIntegrityCheckAcceptsAllMatchingTransactionReferences() throws Exception {
        DatabaseBootstrap bootstrap = migratedBootstrap("valid-references");
        try (Connection connection = bootstrap.openConnection()) {
            seedProductSupplierAndBatch(connection);
            execute(connection, """
                    INSERT INTO purchase (
                        id, supplier_id, purchase_date, invoice_number,
                        payment_method, total_amount_paisa, created_at, created_by
                    ) VALUES (
                        'purchase-1', 'supplier-1', '2026-09-13', 'SUP-1',
                        'CASH', 100, '2026-09-13T04:00:00Z', NULL
                    )
                    """);
            execute(connection, """
                    INSERT INTO sale (
                        id, customer_id, sale_date, invoice_number, payment_method,
                        total_amount_paisa, created_at, created_by
                    ) VALUES (
                        'sale-1', NULL, '2026-09-13', 1, 'CASH',
                        150, '2026-09-13T05:00:00Z', NULL
                    )
                    """);
            execute(connection, """
                    INSERT INTO sales_return (
                        id, return_number, original_sale_id, return_date, reason,
                        refund_method, total_amount_paisa, notes, created_at, created_by
                    ) VALUES (
                        'sales-return-1', 1, 'sale-1', '2026-09-13', 'CUSTOMER_RETURN',
                        'CASH', 150, NULL, '2026-09-13T06:00:00Z', NULL
                    )
                    """);
            execute(connection, """
                    INSERT INTO purchase_return (
                        id, return_number, original_purchase_id, supplier_id, return_date,
                        reason, settlement_method, notes, total_amount_paisa, created_at, created_by
                    ) VALUES (
                        'purchase-return-1', 1, 'purchase-1', 'supplier-1', '2026-09-13',
                        'DAMAGED', 'CASH', NULL, 100, '2026-09-13T06:00:00Z', NULL
                    )
                    """);
            insertMovement(connection, "movement-purchase", "PURCHASE_RECEIPT", "purchase-1");
            insertMovement(connection, "movement-sale", "SALE", "sale-1");
            insertMovement(connection, "movement-sales-return", "SALE_RETURN", "sales-return-1");
            insertMovement(connection, "movement-purchase-return", "PURCHASE_RETURN", "purchase-return-1");
        }

        assertDoesNotThrow(bootstrap::migrate);
    }

    @Test
    void startupIntegrityCheckRejectsOrphanedPurchaseReceiptReference() throws Exception {
        DatabaseBootstrap bootstrap = migratedBootstrap("orphaned-purchase-reference");
        try (Connection connection = bootstrap.openConnection()) {
            seedProductSupplierAndBatch(connection);
            insertMovement(connection, "movement-orphan", "PURCHASE_RECEIPT", "missing-purchase");
        }

        IllegalStateException exception = assertThrows(IllegalStateException.class, bootstrap::migrate);

        assertTrue(exception.getMessage().contains("movement-orphan"));
        assertTrue(exception.getMessage().contains("PURCHASE_RECEIPT"));
        assertTrue(exception.getMessage().contains("missing-purchase"));
    }

    @Test
    void startupIntegrityCheckRejectsSaleMovementPointingToAPurchase() throws Exception {
        DatabaseBootstrap bootstrap = migratedBootstrap("mismatched-sale-reference");
        try (Connection connection = bootstrap.openConnection()) {
            seedProductSupplierAndBatch(connection);
            execute(connection, """
                    INSERT INTO purchase (
                        id, supplier_id, purchase_date, invoice_number,
                        payment_method, total_amount_paisa, created_at, created_by
                    ) VALUES (
                        'purchase-only', 'supplier-1', '2026-09-13', NULL,
                        'CASH', 100, '2026-09-13T04:00:00Z', NULL
                    )
                    """);
            insertMovement(connection, "movement-wrong-type", "SALE", "purchase-only");
        }

        IllegalStateException exception = assertThrows(IllegalStateException.class, bootstrap::migrate);

        assertTrue(exception.getMessage().contains("movement-wrong-type"));
        assertTrue(exception.getMessage().contains("SALE"));
        assertTrue(exception.getMessage().contains("purchase-only"));
    }

    @Test
    void startupIntegrityCheckRejectsSalesReturnMovementPointingToASale() throws Exception {
        DatabaseBootstrap bootstrap = migratedBootstrap("mismatched-return-reference");
        try (Connection connection = bootstrap.openConnection()) {
            seedProductSupplierAndBatch(connection);
            execute(connection, """
                    INSERT INTO sale (
                        id, customer_id, sale_date, invoice_number, payment_method,
                        total_amount_paisa, created_at, created_by
                    ) VALUES (
                        'sale-not-return', NULL, '2026-09-13', 1, 'CASH',
                        150, '2026-09-13T05:00:00Z', NULL
                    )
                    """);
            insertMovement(connection, "movement-return", "SALE_RETURN", "sale-not-return");
        }

        IllegalStateException exception = assertThrows(IllegalStateException.class, bootstrap::migrate);

        assertTrue(exception.getMessage().contains("movement-return"));
        assertTrue(exception.getMessage().contains("SALE_RETURN"));
        assertTrue(exception.getMessage().contains("sale-not-return"));
    }

    @Test
    void startupIntegrityCheckRejectsOrphanedPurchaseReturnReference() throws Exception {
        DatabaseBootstrap bootstrap = migratedBootstrap("orphaned-purchase-return-reference");
        try (Connection connection = bootstrap.openConnection()) {
            seedProductSupplierAndBatch(connection);
            insertMovement(connection, "movement-purchase-return", "PURCHASE_RETURN", "missing-return");
        }

        IllegalStateException exception = assertThrows(IllegalStateException.class, bootstrap::migrate);

        assertTrue(exception.getMessage().contains("movement-purchase-return"));
        assertTrue(exception.getMessage().contains("PURCHASE_RETURN"));
        assertTrue(exception.getMessage().contains("missing-return"));
    }

    private DatabaseBootstrap migratedBootstrap(String directory) {
        DatabaseBootstrap bootstrap = new DatabaseBootstrap(
                temporaryDirectory.resolve(directory).resolve("pharmacy.db"));
        bootstrap.migrate();
        return bootstrap;
    }

    private static void seedProductSupplierAndBatch(Connection connection) throws Exception {
        execute(connection, """
                INSERT INTO product (
                    id, name, generic_name, manufacturer, category, unit_of_sale, pack_size,
                    purchase_price_paisa, sale_price_paisa, mrp_paisa, tax_rate_basis_points,
                    reorder_threshold_base_units, is_active, created_at, updated_at
                ) VALUES (
                    'product-1', 'Integrity Test Product', NULL, NULL, 'TABLET', 'TABLET', NULL,
                    100, 150, NULL, 0, 0, 1,
                    '2026-09-13T03:00:00Z', '2026-09-13T03:00:00Z'
                )
                """);
        execute(connection, """
                INSERT INTO supplier (
                    id, name, phone, address, pan, is_active, created_at, updated_at
                ) VALUES (
                    'supplier-1', 'Integrity Test Supplier', NULL, NULL, NULL, 1,
                    '2026-09-13T03:00:00Z', '2026-09-13T03:00:00Z'
                )
                """);
        execute(connection, """
                INSERT INTO product_batch (
                    id, product_id, batch_number, expiry_date, manufacturing_date,
                    purchase_price_paisa, created_at
                ) VALUES (
                    'batch-1', 'product-1', 'CHECK-1', '2028-01-01', NULL,
                    100, '2026-09-13T03:00:00Z'
                )
                """);
    }

    private static void insertMovement(
            Connection connection, String id, String type, String referenceId) throws Exception {
        try (var statement = connection.prepareStatement("""
                INSERT INTO inventory_movement (
                    id, batch_id, movement_type, quantity_base_units, reference_id, created_at
                ) VALUES (?, 'batch-1', ?, 1, ?, '2026-09-13T06:00:00Z')
                """)) {
            statement.setString(1, id);
            statement.setString(2, type);
            statement.setString(3, referenceId);
            statement.executeUpdate();
        }
    }

    private static void execute(Connection connection, String sql) throws Exception {
        try (var statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        }
    }
}
