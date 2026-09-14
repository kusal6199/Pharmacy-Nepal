package com.nepalpharmacy.inventory.infrastructure;

import com.nepalpharmacy.bootstrap.DatabaseBootstrap;
import com.nepalpharmacy.inventory.ExpiryBatchAlert;
import com.nepalpharmacy.inventory.ExpiryHorizon;
import com.nepalpharmacy.inventory.ExpiryStatus;
import com.nepalpharmacy.inventory.InventoryAlertCriteria;
import com.nepalpharmacy.inventory.InventoryAlertSummary;
import com.nepalpharmacy.inventory.ProductStockAlert;
import com.nepalpharmacy.inventory.ProductStockFilter;
import com.nepalpharmacy.inventory.ProductStockStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcInventoryAlertRepositoryTest {

    private static final LocalDate AS_OF = LocalDate.of(2026, 9, 13);
    private static final UUID SUPPLIER_ID = UUID.fromString(
            "70000000-0000-0000-0000-000000000001");

    @TempDir
    Path temporaryDirectory;

    private DatabaseBootstrap database;
    private JdbcInventoryAlertRepository alerts;
    private int documentNumber;

    @BeforeEach
    void setUp() throws SQLException {
        database = new DatabaseBootstrap(temporaryDirectory.resolve("pharmacy.db"));
        database.migrate();
        alerts = new JdbcInventoryAlertRepository(database::openConnection);
        execute("""
                INSERT INTO supplier (
                    id, name, phone, address, pan, is_active, created_at, updated_at
                ) VALUES (?, 'Alert Test Supplier', NULL, NULL, NULL, 1,
                          '2026-09-01T00:00:00Z', '2026-09-01T00:00:00Z')
                """, SUPPLIER_ID);
    }

    @Test
    void summaryAndExpiryListUseExactNonOverlappingDayBoundaries() throws SQLException {
        UUID inactiveExpired = product("Zed Inactive", null, null, 0, false);
        batch(inactiveExpired, "OLD", AS_OF.minusDays(1), 2);
        UUID today = product("Today Medicine", null, null, 0, true);
        UUID todayBatch = batch(today, "TODAY", AS_OF, 3);
        batch(product("Day 30", null, null, 0, true), "D30", AS_OF.plusDays(30), 1);
        batch(product("Day 31", null, null, 0, true), "D31", AS_OF.plusDays(31), 1);
        batch(product("Day 60", null, null, 0, true), "D60", AS_OF.plusDays(60), 1);
        batch(product("Day 61", null, null, 0, true), "D61", AS_OF.plusDays(61), 1);
        batch(product("Day 90", null, null, 0, true), "D90", AS_OF.plusDays(90), 1);
        batch(product("Day 91", null, null, 0, true), "D91", AS_OF.plusDays(91), 1);

        InventoryAlertSummary summary = alerts.loadSummary(AS_OF);
        assertEquals(1, summary.expiredBatchCount());
        assertEquals(2, summary.days0To30BatchCount());
        assertEquals(2, summary.days31To60BatchCount());
        assertEquals(2, summary.days61To90BatchCount());
        assertEquals(0, summary.lowStockProductCount());
        assertEquals(0, summary.outOfStockProductCount());

        List<ExpiryBatchAlert> rows = alerts.findExpiryAlerts(
                AS_OF, InventoryAlertCriteria.defaults(), 100);
        assertEquals(List.of(
                        ExpiryStatus.EXPIRED,
                        ExpiryStatus.DAYS_0_TO_30,
                        ExpiryStatus.DAYS_0_TO_30,
                        ExpiryStatus.DAYS_31_TO_60,
                        ExpiryStatus.DAYS_31_TO_60,
                        ExpiryStatus.DAYS_61_TO_90,
                        ExpiryStatus.DAYS_61_TO_90),
                rows.stream().map(ExpiryBatchAlert::status).toList());
        assertFalse(rows.get(0).productActive());
        assertFalse(rows.stream().anyMatch(row -> row.batchNumber().equals("D91")));

        assertEquals(todayBatch, new JdbcBatchRepository(database::openConnection)
                .findAvailableByProduct(today, AS_OF).get(0).batch().id());
    }

    @Test
    void expiryFiltersSearchAllRequestedFieldsAndExcludeNonPositiveStock()
            throws SQLException {
        UUID genericMatch = product("Brand One", "Acetylsalicylic Acid", "Maker A", 0, true);
        batch(genericMatch, "GENERIC", AS_OF.plusDays(10), 4);
        UUID makerMatch = product("Brand Two", "Ibuprofen", "Himalaya Remedies", 0, true);
        batch(makerMatch, "MAKER", AS_OF.plusDays(40), 4);
        UUID zero = product("Zero Expired", null, "Himalaya", 0, true);
        batch(zero, "ZERO", AS_OF.minusDays(5), 0);
        UUID negative = product("Negative Expired", null, "Himalaya", 0, true);
        UUID negativeBatch = batch(negative, "NEG", AS_OF.minusDays(4), 2);
        saleMovement(negativeBatch, 3);

        List<ExpiryBatchAlert> genericRows = alerts.findExpiryAlerts(AS_OF,
                new InventoryAlertCriteria(
                        ExpiryHorizon.DAYS_0_TO_30, "ACETYLSALICYLIC",
                        ProductStockFilter.ALL, null), 100);
        assertEquals(List.of("GENERIC"), genericRows.stream()
                .map(ExpiryBatchAlert::batchNumber).toList());

        List<ExpiryBatchAlert> makerRows = alerts.findExpiryAlerts(AS_OF,
                new InventoryAlertCriteria(
                        ExpiryHorizon.DAYS_31_TO_60, "hImAlAyA",
                        ProductStockFilter.ALL, null), 100);
        assertEquals(List.of("MAKER"), makerRows.stream()
                .map(ExpiryBatchAlert::batchNumber).toList());

        List<ExpiryBatchAlert> expired = alerts.findExpiryAlerts(AS_OF,
                new InventoryAlertCriteria(
                        ExpiryHorizon.EXPIRED, null, ProductStockFilter.ALL, null), 100);
        assertTrue(expired.isEmpty());
    }

    @Test
    void stockAlertsUseSellableStockAndConfiguredThresholds() throws SQLException {
        product("Out Zero Threshold", null, null, 0, true);
        UUID expiredOnly = product("Out Expired Only", null, null, 10, true);
        batch(expiredOnly, "EXPIRED", AS_OF.minusDays(1), 5);
        UUID lowEqual = product("Low Equal", null, null, 5, true);
        batch(lowEqual, "VALID-5", AS_OF.plusDays(1), 5);
        UUID oneAbove = product("Okay One Above", null, null, 5, true);
        batch(oneAbove, "VALID-6", AS_OF.plusDays(1), 6);
        UUID zeroThreshold = product("Okay Zero Threshold", null, null, 0, true);
        batch(zeroThreshold, "VALID-1", AS_OF.plusDays(1), 1);
        UUID mixed = product("Mixed Medicine", null, null, 8, true);
        batch(mixed, "MIX-OLD", AS_OF.minusDays(1), 7);
        batch(mixed, "MIX-VALID", AS_OF.plusDays(1), 4);
        product("Inactive Empty", null, null, 100, false);

        List<ProductStockAlert> rows = alerts.findProductStockAlerts(
                AS_OF, InventoryAlertCriteria.defaults(), 100);

        assertEquals(4, rows.size());
        assertEquals(List.of(ProductStockStatus.OUT_OF_STOCK, ProductStockStatus.OUT_OF_STOCK),
                rows.subList(0, 2).stream().map(ProductStockAlert::status).toList());
        ProductStockAlert expired = byName(rows, "Out Expired Only");
        assertEquals(0, expired.sellableStockBaseUnits());
        assertEquals(5, expired.physicalStockBaseUnits());
        assertEquals(5, expired.expiredStockBaseUnits());
        assertEquals(ProductStockStatus.LOW_STOCK, byName(rows, "Low Equal").status());
        ProductStockAlert mixedRow = byName(rows, "Mixed Medicine");
        assertEquals(4, mixedRow.sellableStockBaseUnits());
        assertEquals(11, mixedRow.physicalStockBaseUnits());
        assertEquals(7, mixedRow.expiredStockBaseUnits());
        assertFalse(rows.stream().anyMatch(row -> row.productName().startsWith("Okay")));
        assertFalse(rows.stream().anyMatch(row -> row.productName().startsWith("Inactive")));

        List<ProductStockAlert> filtered = alerts.findProductStockAlerts(AS_OF,
                new InventoryAlertCriteria(
                        ExpiryHorizon.ALL, null, ProductStockFilter.LOW_STOCK, "mIxEd"), 100);
        assertEquals(List.of("Mixed Medicine"), filtered.stream()
                .map(ProductStockAlert::productName).toList());

        InventoryAlertSummary summary = alerts.loadSummary(AS_OF);
        assertEquals(2, summary.outOfStockProductCount());
        assertEquals(2, summary.lowStockProductCount());
    }

    @Test
    void eachMovementTypeAndThresholdEditAreVisibleOnTheNextQuery() throws SQLException {
        UUID product = product("Moving Stock", null, null, 5, true);
        UUID batch = batch(product, "MOVE", AS_OF.plusDays(30), 0);
        assertEquals(ProductStockStatus.OUT_OF_STOCK,
                byName(stockAlerts(), "Moving Stock").status());

        purchaseMovement(batch, 10);
        assertTrue(stockAlerts().isEmpty());

        UUID sale = saleMovement(batch, 6);
        assertEquals(4, byName(stockAlerts(), "Moving Stock").sellableStockBaseUnits());

        salesReturnMovement(batch, sale, 2);
        assertTrue(stockAlerts().isEmpty());

        UUID purchase = purchaseReference();
        purchaseReturnMovement(batch, purchase, 2);
        assertEquals(4, byName(stockAlerts(), "Moving Stock").sellableStockBaseUnits());

        execute("UPDATE product SET reorder_threshold_base_units = 3 WHERE id = ?", product);
        assertTrue(stockAlerts().isEmpty());
    }

    private List<ProductStockAlert> stockAlerts() {
        return alerts.findProductStockAlerts(AS_OF, InventoryAlertCriteria.defaults(), 100);
    }

    private static ProductStockAlert byName(List<ProductStockAlert> rows, String name) {
        return rows.stream().filter(row -> row.productName().equals(name)).findFirst().orElseThrow();
    }

    private UUID product(
            String name, String genericName, String manufacturer, int threshold, boolean active)
            throws SQLException {
        UUID id = UUID.randomUUID();
        execute("""
                INSERT INTO product (
                    id, name, generic_name, manufacturer, category, unit_of_sale,
                    pack_size, purchase_price_paisa, sale_price_paisa, mrp_paisa,
                    tax_rate_basis_points, reorder_threshold_base_units, is_active,
                    created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'TABLET', 'TABLET', NULL, 100, 150, 150,
                          0, ?, ?, '2026-09-01T00:00:00Z', '2026-09-01T00:00:00Z')
                """, id, name, genericName, manufacturer, threshold, active ? 1 : 0);
        return id;
    }

    private UUID batch(UUID productId, String number, LocalDate expiry, int quantity)
            throws SQLException {
        UUID batchId = UUID.randomUUID();
        execute("""
                INSERT INTO product_batch (
                    id, product_id, batch_number, expiry_date, manufacturing_date,
                    purchase_price_paisa, created_at
                ) VALUES (?, ?, ?, ?, NULL, 100, '2026-09-01T00:00:00Z')
                """, batchId, productId, number, expiry);
        if (quantity > 0) {
            purchaseMovement(batchId, quantity);
        }
        return batchId;
    }

    private UUID purchaseMovement(UUID batchId, int quantity) throws SQLException {
        UUID purchase = purchaseReference();
        movement(batchId, "PURCHASE_RECEIPT", quantity, purchase);
        return purchase;
    }

    private UUID purchaseReference() throws SQLException {
        UUID purchase = UUID.randomUUID();
        execute("""
                INSERT INTO purchase (
                    id, supplier_id, purchase_date, invoice_number,
                    total_amount_paisa, created_at, created_by
                ) VALUES (?, ?, ?, NULL, 100, '2026-09-01T00:00:00Z', NULL)
                """, purchase, SUPPLIER_ID, AS_OF);
        return purchase;
    }

    private UUID saleMovement(UUID batchId, int quantity) throws SQLException {
        UUID sale = UUID.randomUUID();
        execute("""
                INSERT INTO sale (
                    id, customer_id, sale_date, invoice_number, payment_method,
                    total_amount_paisa, created_at, created_by
                ) VALUES (?, NULL, ?, ?, 'CASH', 100, '2026-09-01T00:00:00Z', NULL)
                """, sale, AS_OF, ++documentNumber);
        movement(batchId, "SALE", quantity, sale);
        return sale;
    }

    private void salesReturnMovement(UUID batchId, UUID sale, int quantity) throws SQLException {
        UUID salesReturn = UUID.randomUUID();
        execute("""
                INSERT INTO sales_return (
                    id, return_number, original_sale_id, return_date, reason,
                    refund_method, total_amount_paisa, notes, created_at, created_by
                ) VALUES (?, ?, ?, ?, 'CUSTOMER_RETURN', 'CASH', 100, NULL,
                          '2026-09-01T00:00:00Z', NULL)
                """, salesReturn, ++documentNumber, sale, AS_OF);
        movement(batchId, "SALE_RETURN", quantity, salesReturn);
    }

    private void purchaseReturnMovement(UUID batchId, UUID purchase, int quantity)
            throws SQLException {
        UUID purchaseReturn = UUID.randomUUID();
        execute("""
                INSERT INTO purchase_return (
                    id, return_number, original_purchase_id, supplier_id, return_date,
                    reason, notes, total_amount_paisa, created_at, created_by
                ) VALUES (?, ?, ?, ?, ?, 'DAMAGED', NULL, 100,
                          '2026-09-01T00:00:00Z', NULL)
                """, purchaseReturn, ++documentNumber, purchase, SUPPLIER_ID, AS_OF);
        movement(batchId, "PURCHASE_RETURN", quantity, purchaseReturn);
    }

    private void movement(UUID batchId, String type, int quantity, UUID reference)
            throws SQLException {
        execute("""
                INSERT INTO inventory_movement (
                    id, batch_id, movement_type, quantity_base_units, reference_id, created_at
                ) VALUES (?, ?, ?, ?, ?, '2026-09-01T00:00:00Z')
                """, UUID.randomUUID(), batchId, type, quantity, reference);
    }

    private void execute(String sql, Object... parameters) throws SQLException {
        try (Connection connection = database.openConnection();
             var statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                Object value = parameters[index];
                if (value instanceof UUID uuid) {
                    value = uuid.toString();
                } else if (value instanceof LocalDate date) {
                    value = date.toString();
                }
                statement.setObject(index + 1, value);
            }
            statement.executeUpdate();
        }
    }
}
