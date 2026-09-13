package com.nepalpharmacy.sales.infrastructure;

import com.nepalpharmacy.bootstrap.DatabaseBootstrap;
import com.nepalpharmacy.sales.PaymentMethod;
import com.nepalpharmacy.sales.SaleDetail;
import com.nepalpharmacy.sales.SaleReturnStatus;
import com.nepalpharmacy.sales.SaleSearchCriteria;
import com.nepalpharmacy.sales.SaleSummary;
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

class JdbcSaleHistoryRepositoryTest {

    private static final UUID PRODUCT_ID = UUID.fromString(
            "11111111-1111-1111-1111-111111111111");
    private static final UUID BATCH_ID = UUID.fromString(
            "22222222-2222-2222-2222-222222222222");
    private static final UUID ALICE_ID = UUID.fromString(
            "33333333-3333-3333-3333-333333333333");
    private static final UUID BOB_ID = UUID.fromString(
            "44444444-4444-4444-4444-444444444444");

    @TempDir
    Path temporaryDirectory;

    private DatabaseBootstrap database;
    private JdbcSaleHistoryRepository history;

    @BeforeEach
    void setUp() throws SQLException {
        database = new DatabaseBootstrap(temporaryDirectory.resolve("pharmacy.db"));
        database.migrate();
        history = new JdbcSaleHistoryRepository(database::openConnection);
        seedFoundation();
    }

    @Test
    void emptyHistoryAndUnknownDetailAreValid() {
        assertTrue(history.search(emptyCriteria(), 50).isEmpty());
        assertTrue(history.findDetail(UUID.randomUUID()).isEmpty());
        assertTrue(new JdbcSaleRepository(database::openConnection)
                .findById(UUID.randomUUID()).isEmpty());
        assertTrue(new JdbcSaleLineRepository(database::openConnection)
                .findBySaleId(UUID.randomUUID()).isEmpty());
    }

    @Test
    void searchIsInclusiveOrderedAndIncludesWalkInAndInactiveCustomers() throws SQLException {
        UUID aliceSale = insertSale(1, LocalDate.of(2026, 9, 10), ALICE_ID,
                PaymentMethod.CASH, "2026-09-10T08:00:00Z", 2, 150);
        UUID walkInSale = insertSale(2, LocalDate.of(2026, 9, 11), null,
                PaymentMethod.QR, "2026-09-11T08:00:00Z", 1, 160);
        UUID bobSale = insertSale(3, LocalDate.of(2026, 9, 11), BOB_ID,
                PaymentMethod.CREDIT, "2026-09-11T09:00:00Z", 1, 170);

        List<SaleSummary> sameDay = history.search(new SaleSearchCriteria(
                null, LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 11),
                null, null), 100);
        assertEquals(List.of(bobSale, walkInSale),
                sameDay.stream().map(SaleSummary::id).toList());

        List<SaleSummary> inactive = history.search(new SaleSearchCriteria(
                null, null, null, "ALIce", null), 100);
        assertEquals(List.of(aliceSale), inactive.stream().map(SaleSummary::id).toList());
        assertEquals("Alice Health", inactive.get(0).customerName());

        List<SaleSummary> walkIn = history.search(new SaleSearchCriteria(
                null, null, null, "walk", null), 100);
        assertEquals(List.of(walkInSale), walkIn.stream().map(SaleSummary::id).toList());
        assertEquals("Walk-in", walkIn.get(0).customerName());

        assertEquals(List.of(walkInSale), history.search(new SaleSearchCriteria(
                2L, null, null, null, PaymentMethod.QR), 100)
                .stream().map(SaleSummary::id).toList());
        assertEquals(2, history.search(emptyCriteria(), 2).size());
    }

    @Test
    void detailUsesSavedPricesAndDerivesNonePartialAndFullReturnStatus() throws SQLException {
        UUID saleId = insertSale(8, LocalDate.of(2026, 9, 12), ALICE_ID,
                PaymentMethod.CASH, "2026-09-12T08:00:00Z", 5, 150);
        UUID lineId = lineId(saleId);

        SaleDetail original = history.findDetail(saleId).orElseThrow();
        assertEquals(SaleReturnStatus.NONE, original.summary().returnStatus());
        assertEquals(150, original.lines().get(0).unitSalePricePaisa());
        assertEquals(750, original.lines().get(0).lineTotalPaisa());
        assertEquals(5, original.lines().get(0).remainingReturnableBaseUnits());

        execute("UPDATE product SET sale_price_paisa = 999, is_active = 0 WHERE id = ?",
                PRODUCT_ID);
        insertReturn(saleId, lineId, 2, 1);
        SaleDetail partial = history.findDetail(saleId).orElseThrow();
        assertEquals(SaleReturnStatus.PARTIAL, partial.summary().returnStatus());
        assertEquals(150, partial.lines().get(0).unitSalePricePaisa());
        assertEquals(2, partial.lines().get(0).previouslyReturnedBaseUnits());
        assertEquals(3, partial.lines().get(0).remainingReturnableBaseUnits());
        assertTrue(partial.hasReturnableQuantity());

        insertReturn(saleId, lineId, 3, 2);
        SaleDetail full = history.findDetail(saleId).orElseThrow();
        assertEquals(SaleReturnStatus.FULL, full.summary().returnStatus());
        assertEquals(5, full.lines().get(0).previouslyReturnedBaseUnits());
        assertEquals(0, full.lines().get(0).remainingReturnableBaseUnits());
        assertFalse(full.hasReturnableQuantity());

        assertEquals(saleId, new JdbcSaleRepository(database::openConnection)
                .findById(saleId).orElseThrow().id());
        assertEquals(lineId, new JdbcSaleLineRepository(database::openConnection)
                .findById(lineId).orElseThrow().id());
    }

    private void seedFoundation() throws SQLException {
        execute("""
                INSERT INTO product (
                    id, name, generic_name, manufacturer, category, unit_of_sale,
                    pack_size, purchase_price_paisa, sale_price_paisa, mrp_paisa,
                    tax_rate_basis_points, reorder_threshold_base_units, is_active,
                    created_at, updated_at
                ) VALUES (?, 'Paracetamol 500mg', 'Paracetamol', 'Nepal Pharma',
                          'TABLET', 'TABLET', 10, 100, 150, 150, 0, 5, 1,
                          '2026-09-01T00:00:00Z', '2026-09-01T00:00:00Z')
                """, PRODUCT_ID);
        execute("""
                INSERT INTO product_batch (
                    id, product_id, batch_number, expiry_date, manufacturing_date,
                    purchase_price_paisa, created_at
                ) VALUES (?, ?, 'BATCH-A', '2027-12-31', '2026-01-01', 100,
                          '2026-09-01T00:00:00Z')
                """, BATCH_ID, PRODUCT_ID);
        execute("""
                INSERT INTO customer (
                    id, name, phone, address, is_active, created_at, updated_at
                ) VALUES (?, 'Alice Health', NULL, NULL, 0,
                          '2026-09-01T00:00:00Z', '2026-09-01T00:00:00Z')
                """, ALICE_ID);
        execute("""
                INSERT INTO customer (
                    id, name, phone, address, is_active, created_at, updated_at
                ) VALUES (?, 'Bob Clinic', NULL, NULL, 1,
                          '2026-09-01T00:00:00Z', '2026-09-01T00:00:00Z')
                """, BOB_ID);
    }

    private UUID insertSale(
            long invoice,
            LocalDate date,
            UUID customerId,
            PaymentMethod payment,
            String createdAt,
            int quantity,
            long unitPrice
    ) throws SQLException {
        UUID saleId = UUID.randomUUID();
        execute("""
                INSERT INTO sale (
                    id, customer_id, sale_date, invoice_number, payment_method,
                    total_amount_paisa, created_at, created_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, NULL)
                """, saleId, customerId, date, invoice, payment.name(), quantity * unitPrice,
                createdAt);
        execute("""
                INSERT INTO sale_line (
                    id, sale_id, batch_id, quantity_sold_base_units,
                    unit_sale_price_paisa, line_total_paisa
                ) VALUES (?, ?, ?, ?, ?, ?)
                """, lineId(saleId), saleId, BATCH_ID, quantity, unitPrice,
                quantity * unitPrice);
        return saleId;
    }

    private void insertReturn(UUID saleId, UUID lineId, int quantity, int number)
            throws SQLException {
        UUID returnId = UUID.randomUUID();
        execute("""
                INSERT INTO sales_return (
                    id, return_number, original_sale_id, return_date, reason,
                    refund_method, total_amount_paisa, notes, created_at, created_by
                ) VALUES (?, ?, ?, '2026-09-13', 'CUSTOMER_RETURN', 'CASH', ?, NULL,
                          '2026-09-13T00:00:00Z', NULL)
                """, returnId, number, saleId, quantity * 150);
        execute("""
                INSERT INTO sales_return_line (
                    id, sales_return_id, original_sale_line_id, product_id, batch_id,
                    quantity_returned_base_units, unit_price_paisa, line_total_paisa
                ) VALUES (?, ?, ?, ?, ?, ?, 150, ?)
                """, UUID.randomUUID(), returnId, lineId, PRODUCT_ID, BATCH_ID, quantity,
                quantity * 150);
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

    private static UUID lineId(UUID saleId) {
        return UUID.nameUUIDFromBytes((saleId + "-line").getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static SaleSearchCriteria emptyCriteria() {
        return new SaleSearchCriteria(null, null, null, null, null);
    }
}
