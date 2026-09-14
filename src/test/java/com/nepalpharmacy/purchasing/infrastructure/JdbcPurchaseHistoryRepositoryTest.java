package com.nepalpharmacy.purchasing.infrastructure;

import com.nepalpharmacy.bootstrap.DatabaseBootstrap;
import com.nepalpharmacy.purchasing.PurchaseDetail;
import com.nepalpharmacy.purchasing.PurchasePaymentMethod;
import com.nepalpharmacy.purchasing.PurchaseSearchCriteria;
import com.nepalpharmacy.purchasing.PurchaseSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcPurchaseHistoryRepositoryTest {

    private static final UUID PRODUCT_ID = UUID.fromString(
            "51111111-1111-1111-1111-111111111111");
    private static final UUID BATCH_ID = UUID.fromString(
            "52222222-2222-2222-2222-222222222222");
    private static final UUID KATHMANDU_ID = UUID.fromString(
            "53333333-3333-3333-3333-333333333333");
    private static final UUID POKHARA_ID = UUID.fromString(
            "54444444-4444-4444-4444-444444444444");

    @TempDir
    Path temporaryDirectory;

    private DatabaseBootstrap database;
    private JdbcPurchaseHistoryRepository history;

    @BeforeEach
    void setUp() throws SQLException {
        database = new DatabaseBootstrap(temporaryDirectory.resolve("pharmacy.db"));
        database.migrate();
        history = new JdbcPurchaseHistoryRepository(database::openConnection);
        seedFoundation();
    }

    @Test
    void emptyHistoryAndUnknownDetailAreValid() {
        assertTrue(history.search(emptyCriteria(), 50).isEmpty());
        assertTrue(history.findDetail(UUID.randomUUID()).isEmpty());
        assertTrue(new JdbcPurchaseRepository(database::openConnection)
                .findById(UUID.randomUUID()).isEmpty());
        assertTrue(new JdbcPurchaseLineRepository(database::openConnection)
                .findByPurchaseId(UUID.randomUUID()).isEmpty());
    }

    @Test
    void searchIsInclusiveOrderedAndKeepsInactiveSuppliersAndDuplicateInvoices()
            throws SQLException {
        UUID inactive = insertPurchase(LocalDate.of(2026, 9, 10), KATHMANDU_ID,
                "DUP-77", "2026-09-10T08:00:00Z", 2, 100, true);
        UUID earlier = insertPurchase(LocalDate.of(2026, 9, 11), POKHARA_ID,
                "DUP-77", "2026-09-11T08:00:00Z", 1, 110, true);
        UUID later = insertPurchase(LocalDate.of(2026, 9, 11), POKHARA_ID,
                null, "2026-09-11T09:00:00Z", 1, 120, true);

        List<PurchaseSummary> inclusive = history.search(new PurchaseSearchCriteria(
                LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 11), null, null), 100);
        assertEquals(List.of(later, earlier),
                inclusive.stream().map(PurchaseSummary::id).toList());
        assertEquals(null, inclusive.get(0).supplierInvoice());
        assertEquals(PurchasePaymentMethod.CASH, inclusive.get(0).paymentMethod());

        List<PurchaseSummary> inactiveResult = history.search(new PurchaseSearchCriteria(
                null, null, "MEDICAL SUP", null), 100);
        assertEquals(List.of(inactive),
                inactiveResult.stream().map(PurchaseSummary::id).toList());
        assertEquals("Kathmandu Medical Suppliers", inactiveResult.get(0).supplierName());

        List<PurchaseSummary> duplicates = history.search(new PurchaseSearchCriteria(
                null, null, null, "dup-77"), 100);
        assertEquals(2, duplicates.size());
        assertEquals(List.of(earlier, inactive),
                duplicates.stream().map(PurchaseSummary::id).toList());
        assertEquals(2, history.search(emptyCriteria(), 2).size());
    }

    @Test
    void detailUsesSavedCostsAndShowsReturnsAlongsideCurrentPhysicalStock()
            throws SQLException {
        UUID purchaseId = insertPurchase(LocalDate.of(2026, 9, 12), KATHMANDU_ID,
                "COST-1", "2026-09-12T08:00:00Z", 10, 125, true);
        UUID purchaseLineId = lineId(purchaseId);
        insertSaleAndMovement(purchaseId, 5);
        insertPurchaseReturn(purchaseId, purchaseLineId, 3);
        execute("UPDATE product SET purchase_price_paisa = 999, is_active = 0 WHERE id = ?",
                PRODUCT_ID);

        PurchaseDetail detail = history.findDetail(purchaseId).orElseThrow();

        assertEquals(125, detail.lines().get(0).unitPurchasePricePaisa());
        assertEquals(1250, detail.lines().get(0).lineTotalPaisa());
        assertEquals(10, detail.lines().get(0).quantityReceivedBaseUnits());
        assertEquals(3, detail.lines().get(0).previouslyReturnedBaseUnits());
        assertEquals(7, detail.lines().get(0).remainingReturnableBaseUnits());
        assertEquals(2, detail.lines().get(0).currentBatchStockBaseUnits());
        assertEquals(2, detail.lines().get(0).currentlyReturnableBaseUnits());
        assertTrue(detail.hasReturnableQuantity());
        assertEquals(PurchasePaymentMethod.CASH, detail.summary().paymentMethod());

        assertEquals(purchaseId, new JdbcPurchaseRepository(database::openConnection)
                .findById(purchaseId).orElseThrow().id());
        assertEquals(purchaseLineId, new JdbcPurchaseLineRepository(database::openConnection)
                .findById(purchaseLineId).orElseThrow().id());
    }

    @Test
    void soldOutPurchaseIsNotReturnableAndOldInvoiceRemainsSearchable() throws SQLException {
        UUID oldPurchase = insertPurchase(LocalDate.of(2025, 1, 1), KATHMANDU_ID,
                "OLD-UNIQUE", "2025-01-01T08:00:00Z", 4, 90, true);
        insertSaleAndMovement(oldPurchase, 4);
        for (int index = 0; index < 26; index++) {
            insertPurchase(LocalDate.of(2026, 1, 1).plusDays(index), POKHARA_ID,
                    "NEW-" + index, "2026-02-01T08:00:" + String.format("%02d", index)
                            + "Z", 1, 100, false);
        }

        PurchaseDetail soldOut = history.findDetail(oldPurchase).orElseThrow();
        assertEquals(0, soldOut.lines().get(0).currentBatchStockBaseUnits());
        assertFalse(soldOut.hasReturnableQuantity());

        List<PurchaseSummary> found = history.search(new PurchaseSearchCriteria(
                null, null, null, "old-unique"), 100);
        assertEquals(List.of(oldPurchase), found.stream().map(PurchaseSummary::id).toList());
        assertFalse(history.search(emptyCriteria(), 25).stream()
                .anyMatch(summary -> summary.id().equals(oldPurchase)));
    }

    @Test
    void historyMapsLegacyUnspecifiedPaymentHonestly() throws SQLException {
        UUID purchaseId = insertPurchase(LocalDate.of(2026, 9, 12), KATHMANDU_ID,
                "LEGACY-1", "2026-09-12T08:00:00Z", 1, 100, false);
        execute("UPDATE purchase SET payment_method = 'LEGACY_UNSPECIFIED' WHERE id = ?",
                purchaseId);

        PurchaseSummary summary = history.findDetail(purchaseId).orElseThrow().summary();

        assertEquals(PurchasePaymentMethod.LEGACY_UNSPECIFIED, summary.paymentMethod());
        assertEquals("Legacy / unspecified", summary.paymentMethod().displayName());
    }

    private void seedFoundation() throws SQLException {
        execute("""
                INSERT INTO product (
                    id, name, generic_name, manufacturer, category, unit_of_sale,
                    pack_size, purchase_price_paisa, sale_price_paisa, mrp_paisa,
                    tax_rate_basis_points, reorder_threshold_base_units, is_active,
                    created_at, updated_at
                ) VALUES (?, 'Amoxicillin 500mg', 'Amoxicillin', 'Nepal Pharma',
                          'CAPSULE', 'CAPSULE', 10, 100, 150, 150, 0, 5, 1,
                          '2026-09-01T00:00:00Z', '2026-09-01T00:00:00Z')
                """, PRODUCT_ID);
        execute("""
                INSERT INTO product_batch (
                    id, product_id, batch_number, expiry_date, manufacturing_date,
                    purchase_price_paisa, created_at
                ) VALUES (?, ?, 'BATCH-P', '2027-12-31', '2026-01-01', 100,
                          '2026-09-01T00:00:00Z')
                """, BATCH_ID, PRODUCT_ID);
        execute("""
                INSERT INTO supplier (
                    id, name, phone, address, pan, is_active, created_at, updated_at
                ) VALUES (?, 'Kathmandu Medical Suppliers', NULL, NULL, NULL, 0,
                          '2026-09-01T00:00:00Z', '2026-09-01T00:00:00Z')
                """, KATHMANDU_ID);
        execute("""
                INSERT INTO supplier (
                    id, name, phone, address, pan, is_active, created_at, updated_at
                ) VALUES (?, 'Pokhara Wholesale', NULL, NULL, NULL, 1,
                          '2026-09-01T00:00:00Z', '2026-09-01T00:00:00Z')
                """, POKHARA_ID);
    }

    private UUID insertPurchase(
            LocalDate date,
            UUID supplierId,
            String invoice,
            String createdAt,
            int quantity,
            long unitCost,
            boolean receiveStock
    ) throws SQLException {
        UUID purchaseId = UUID.randomUUID();
        execute("""
                INSERT INTO purchase (
                    id, supplier_id, purchase_date, invoice_number,
                    payment_method, total_amount_paisa, created_at, created_by
                ) VALUES (?, ?, ?, ?, 'CASH', ?, ?, NULL)
                """, purchaseId, supplierId, date, invoice, quantity * unitCost, createdAt);
        execute("""
                INSERT INTO purchase_line (
                    id, purchase_id, batch_id, quantity_received_base_units,
                    unit_purchase_price_paisa, line_total_paisa
                ) VALUES (?, ?, ?, ?, ?, ?)
                """, lineId(purchaseId), purchaseId, BATCH_ID, quantity, unitCost,
                quantity * unitCost);
        if (receiveStock) {
            insertMovement("PURCHASE_RECEIPT", purchaseId, quantity);
        }
        return purchaseId;
    }

    private void insertSaleAndMovement(UUID purchaseId, int quantity) throws SQLException {
        UUID saleId = UUID.nameUUIDFromBytes(
                (purchaseId + "-sale").getBytes(StandardCharsets.UTF_8));
        execute("""
                INSERT INTO sale (
                    id, customer_id, sale_date, invoice_number, payment_method,
                    total_amount_paisa, created_at, created_by
                ) VALUES (?, NULL, '2026-09-13', ?, 'CASH', ?,
                          '2026-09-13T00:00:00Z', NULL)
                """, saleId, positiveInvoice(purchaseId), quantity * 150);
        execute("""
                INSERT INTO sale_line (
                    id, sale_id, batch_id, quantity_sold_base_units,
                    unit_sale_price_paisa, line_total_paisa
                ) VALUES (?, ?, ?, ?, 150, ?)
                """, UUID.randomUUID(), saleId, BATCH_ID, quantity, quantity * 150);
        insertMovement("SALE", saleId, quantity);
    }

    private void insertPurchaseReturn(UUID purchaseId, UUID purchaseLineId, int quantity)
            throws SQLException {
        UUID returnId = UUID.randomUUID();
        execute("""
                INSERT INTO purchase_return (
                    id, return_number, original_purchase_id, supplier_id, return_date,
                    reason, settlement_method, notes, total_amount_paisa, created_at, created_by
                ) VALUES (?, 1, ?, ?, '2026-09-13', 'DAMAGED', 'CASH', NULL, ?,
                          '2026-09-13T00:00:00Z', NULL)
                """, returnId, purchaseId, KATHMANDU_ID, quantity * 125);
        execute("""
                INSERT INTO purchase_return_line (
                    id, purchase_return_id, original_purchase_line_id, product_id,
                    batch_id, quantity_returned_base_units, unit_cost_paisa,
                    line_total_paisa
                ) VALUES (?, ?, ?, ?, ?, ?, 125, ?)
                """, UUID.randomUUID(), returnId, purchaseLineId, PRODUCT_ID, BATCH_ID,
                quantity, quantity * 125);
        insertMovement("PURCHASE_RETURN", returnId, quantity);
    }

    private void insertMovement(String type, UUID referenceId, int quantity) throws SQLException {
        execute("""
                INSERT INTO inventory_movement (
                    id, batch_id, movement_type, quantity_base_units, reference_id, created_at
                ) VALUES (?, ?, ?, ?, ?, '2026-09-13T00:00:00Z')
                """, UUID.randomUUID(), BATCH_ID, type, quantity, referenceId);
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

    private static long positiveInvoice(UUID id) {
        return Math.abs((long) id.hashCode()) + 1;
    }

    private static UUID lineId(UUID purchaseId) {
        return UUID.nameUUIDFromBytes(
                (purchaseId + "-line").getBytes(StandardCharsets.UTF_8));
    }

    private static PurchaseSearchCriteria emptyCriteria() {
        return new PurchaseSearchCriteria(null, null, null, null);
    }
}
