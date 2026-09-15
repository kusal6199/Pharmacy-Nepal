package com.nepalpharmacy.sales;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SalesReturnValidatorTest {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 13);

    @Test
    void rejectsZeroAndNegativeQuantities() {
        SalesReturnValidationException zero = assertThrows(
                SalesReturnValidationException.class,
                () -> SalesReturnValidator.validate(draft(UUID.randomUUID(),
                        line(UUID.randomUUID(), UUID.randomUUID(), 0, 150))));
        SalesReturnValidationException negative = assertThrows(
                SalesReturnValidationException.class,
                () -> SalesReturnValidator.validate(draft(UUID.randomUUID(),
                        line(UUID.randomUUID(), UUID.randomUUID(), -1, 150))));

        assertEquals("Line 1: quantity must be greater than zero.",
                zero.fieldErrors().get("line1.quantity"));
        assertEquals("Line 1: quantity must be greater than zero.",
                negative.fieldErrors().get("line1.quantity"));
    }

    @Test
    void rejectsLineFromAnotherSale() {
        UUID requestedSale = UUID.randomUUID();
        UUID originalLineId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        SalesReturnDraft draft = draft(requestedSale, line(originalLineId, batchId, 1, 150));

        SalesReturnValidationException exception = assertThrows(
                SalesReturnValidationException.class,
                () -> SalesReturnValidator.validate(draft, Map.of(originalLineId,
                        availability(originalLineId, UUID.randomUUID(), batchId, 5, 0))));

        assertEquals("Line 1: original line does not belong to this sale.",
                exception.fieldErrors().get("line1.originalLine"));
    }

    @Test
    void rejectsBatchDifferentFromOriginalLine() {
        UUID saleId = UUID.randomUUID();
        UUID originalLineId = UUID.randomUUID();
        UUID actualBatch = UUID.randomUUID();
        SalesReturnDraft draft = draft(saleId,
                line(originalLineId, UUID.randomUUID(), 1, 150));

        SalesReturnValidationException exception = assertThrows(
                SalesReturnValidationException.class,
                () -> SalesReturnValidator.validate(draft, Map.of(originalLineId,
                        availability(originalLineId, saleId, actualBatch, 5, 0))));

        assertEquals("Line 1: batch does not match the original sale line.",
                exception.fieldErrors().get("line1.batch"));
    }

    @Test
    void rejectsReturnBeyondOriginalQuantityAfterEarlierReturns() {
        UUID saleId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        SalesReturnDraft draft = draft(saleId, line(lineId, batchId, 3, 150));

        SalesReturnValidationException exception = assertThrows(
                SalesReturnValidationException.class,
                () -> SalesReturnValidator.validate(draft, Map.of(lineId,
                        availability(lineId, saleId, batchId, 5, 3))));

        assertEquals("Return quantity 3 exceeds remaining returnable quantity 2 for the original sale line.",
                exception.fieldErrors().get("remaining." + lineId));
    }

    @Test
    void acceptsPartialReturnAndCalculatesExactOriginalPriceTotal() {
        UUID saleId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        SalesReturnDraft draft = draft(saleId, line(lineId, batchId, 2, 175));

        SalesReturnValidator.validate(draft, Map.of(lineId,
                availability(lineId, saleId, batchId, 5, 1)));

        assertEquals(350, SalesReturnValidator.totalPaisa(draft));
    }

    @Test
    void creditRefundRequiresCustomerOnOriginalSale() {
        UUID saleId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        SalesReturnDraft credit = new SalesReturnDraft(saleId, DATE,
                SalesReturnReason.CUSTOMER_RETURN, PaymentMethod.CREDIT, null,
                List.of(line(lineId, batchId, 1, 150)), null);
        Sale walkIn = new Sale(saleId, null, DATE, 1, PaymentMethod.CASH, 150,
                Instant.parse("2026-09-13T00:00:00Z"), null);

        SalesReturnValidationException exception = assertThrows(
                SalesReturnValidationException.class,
                () -> SalesReturnValidator.validate(credit, walkIn, Map.of(lineId,
                        availability(lineId, saleId, batchId, 1, 0))));

        assertEquals("Credit refund requires a customer account.",
                exception.fieldErrors().get("refundMethod"));
    }

    @Test
    void creditSaleRejectsCashRefund() {
        assertCreditSaleRejects(PaymentMethod.CASH);
    }

    @Test
    void creditSaleRejectsQrRefund() {
        assertCreditSaleRejects(PaymentMethod.QR);
    }

    @Test
    void cashSaleAcceptsCashQrAndCreditRefunds() {
        UUID saleId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        Sale cashSale = sale(saleId, PaymentMethod.CASH);
        Map<UUID, SalesReturnLineAvailability> availability = Map.of(lineId,
                availability(lineId, saleId, batchId, 3, 0));

        for (PaymentMethod refundMethod : PaymentMethod.values()) {
            SalesReturnDraft returnDraft = draft(
                    saleId, line(lineId, batchId, 1, 150), refundMethod);
            assertDoesNotThrow(() -> SalesReturnValidator.validate(
                    returnDraft, cashSale, availability));
        }
    }

    @Test
    void allowedRefundMethodsFollowOriginalSalePaymentMethod() {
        assertEquals(List.of(PaymentMethod.CREDIT), SalesReturnValidator.allowedRefundMethods(
                sale(UUID.randomUUID(), PaymentMethod.CREDIT)));
        assertEquals(List.of(PaymentMethod.CASH, PaymentMethod.QR, PaymentMethod.CREDIT),
                SalesReturnValidator.allowedRefundMethods(
                        sale(UUID.randomUUID(), PaymentMethod.CASH)));
        assertEquals(List.of(PaymentMethod.CASH, PaymentMethod.QR, PaymentMethod.CREDIT),
                SalesReturnValidator.allowedRefundMethods(
                        sale(UUID.randomUUID(), PaymentMethod.QR)));
    }

    private static void assertCreditSaleRejects(PaymentMethod refundMethod) {
        UUID saleId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        SalesReturnDraft returnDraft = draft(
                saleId, line(lineId, batchId, 1, 150), refundMethod);

        SalesReturnValidationException exception = assertThrows(
                SalesReturnValidationException.class,
                () -> SalesReturnValidator.validate(returnDraft,
                        sale(saleId, PaymentMethod.CREDIT), Map.of(lineId,
                                availability(lineId, saleId, batchId, 1, 0))));

        assertEquals(SalesReturnValidator.UNPAID_SALE_REFUND_MESSAGE,
                exception.fieldErrors().get("refundMethod"));
    }

    private static Sale sale(UUID saleId, PaymentMethod paymentMethod) {
        return new Sale(saleId, UUID.randomUUID(), DATE, 1, paymentMethod, 150,
                Instant.parse("2026-09-13T00:00:00Z"), null);
    }

    private static SalesReturnDraft draft(UUID saleId, SalesReturnLineDraft line) {
        return draft(saleId, line, PaymentMethod.CASH);
    }

    private static SalesReturnDraft draft(
            UUID saleId, SalesReturnLineDraft line, PaymentMethod refundMethod) {
        return new SalesReturnDraft(saleId, DATE, SalesReturnReason.CUSTOMER_RETURN,
                refundMethod, null, List.of(line), null);
    }

    private static SalesReturnLineDraft line(
            UUID lineId, UUID batchId, int quantity, long price) {
        return new SalesReturnLineDraft(lineId, batchId, quantity, price);
    }

    private static SalesReturnLineAvailability availability(
            UUID lineId, UUID saleId, UUID batchId, int sold, long returned) {
        return new SalesReturnLineAvailability(new SaleLine(
                lineId, saleId, batchId, sold, 150, sold * 150L),
                UUID.randomUUID(), returned);
    }
}
