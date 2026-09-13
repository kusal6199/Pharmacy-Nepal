package com.nepalpharmacy.sales;

import com.nepalpharmacy.inventory.Batch;
import com.nepalpharmacy.inventory.BatchStock;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SaleValidatorTest {

    private static final LocalDate SALE_DATE = LocalDate.of(2026, 9, 13);

    @Test
    void rejectsInsufficientStock() {
        UUID batchId = UUID.randomUUID();
        SaleDraft draft = draft(PaymentMethod.CASH, null, List.of(line(batchId, 11, 125)));

        SaleValidationException exception = assertThrows(
                SaleValidationException.class,
                () -> SaleValidator.validate(draft, Map.of(batchId, stock(batchId, 10, SALE_DATE.plusYears(1)))));

        assertEquals(
                "Insufficient stock for batch B-1: requested 11, available 10.",
                exception.fieldErrors().get("stock." + batchId));
    }

    @Test
    void rejectsExpiredBatch() {
        UUID batchId = UUID.randomUUID();
        SaleDraft draft = draft(PaymentMethod.CASH, null, List.of(line(batchId, 1, 125)));

        SaleValidationException exception = assertThrows(
                SaleValidationException.class,
                () -> SaleValidator.validate(draft, Map.of(
                        batchId, stock(batchId, 10, SALE_DATE.minusDays(1)))));

        assertEquals("Line 1: expired batch cannot be sold.",
                exception.fieldErrors().get("line1.expiry"));
    }

    @Test
    void rejectsCreditSaleWithoutCustomer() {
        SaleDraft draft = draft(
                PaymentMethod.CREDIT, null,
                List.of(line(UUID.randomUUID(), 1, 125)));

        SaleValidationException exception = assertThrows(
                SaleValidationException.class, () -> SaleValidator.validate(draft));

        assertEquals("Customer is required for a credit sale.",
                exception.fieldErrors().get("customer"));
    }

    @Test
    void rejectsZeroLineSale() {
        SaleValidationException exception = assertThrows(
                SaleValidationException.class,
                () -> SaleValidator.validate(draft(PaymentMethod.QR, null, List.of())));

        assertEquals("Add at least one sale line.", exception.fieldErrors().get("lines"));
    }

    @Test
    void calculatesExactTotalInPaisa() {
        SaleDraft draft = draft(PaymentMethod.CASH, null, List.of(
                line(UUID.randomUUID(), 3, 125),
                line(UUID.randomUUID(), 2, 250)));

        assertEquals(875, SaleValidator.totalPaisa(draft));
    }

    private static SaleDraft draft(
            PaymentMethod paymentMethod, UUID customerId, List<SaleLineDraft> lines) {
        return new SaleDraft(customerId, SALE_DATE, paymentMethod, lines, null);
    }

    private static SaleLineDraft line(UUID batchId, int quantity, long price) {
        return new SaleLineDraft(batchId, quantity, price);
    }

    private static BatchStock stock(
            UUID batchId, long quantity, LocalDate expiryDate) {
        return new BatchStock(new Batch(
                batchId,
                UUID.randomUUID(),
                "B-1",
                expiryDate,
                null,
                100,
                Instant.parse("2026-01-01T00:00:00Z")), quantity);
    }
}
