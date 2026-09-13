package com.nepalpharmacy.bootstrap;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseBootstrapTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void createsAndMigratesANewDatabase() throws Exception {
        Path databaseFile = temporaryDirectory.resolve("nested/pharmacy.db");
        DatabaseBootstrap bootstrap = new DatabaseBootstrap(databaseFile);

        int migrationsExecuted = bootstrap.migrate();

        assertEquals(4, migrationsExecuted);
        assertTrue(Files.exists(databaseFile));

        try (var connection = DriverManager.getConnection(bootstrap.jdbcUrl());
             var statement = connection.prepareStatement("""
                     SELECT COUNT(*)
                     FROM sqlite_master
                     WHERE type = 'table'
                       AND name IN ('product', 'supplier', 'product_batch', 'purchase',
                                    'purchase_line', 'inventory_movement', 'customer',
                                    'invoice_counter', 'sale', 'sale_line')
                     """)) {
            try (var results = statement.executeQuery()) {
                assertTrue(results.next());
                assertEquals(10, results.getInt(1));
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

        assertEquals(3, bootstrap.migrate());

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
}
