package com.nepalpharmacy.purchasing;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PurchaseReturnValidatorTest {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 13);

    @Test
    void rejectsZeroAndNegativeQuantities() {
        PurchaseReturnValidationException zero = assertThrows(
                PurchaseReturnValidationException.class,
                () -> PurchaseReturnValidator.validate(draft(
                        UUID.randomUUID(), UUID.randomUUID(),
                        line(UUID.randomUUID(), UUID.randomUUID(), 0, 100))));
        PurchaseReturnValidationException negative = assertThrows(
                PurchaseReturnValidationException.class,
                () -> PurchaseReturnValidator.validate(draft(
                        UUID.randomUUID(), UUID.randomUUID(),
                        line(UUID.randomUUID(), UUID.randomUUID(), -1, 100))));

        assertEquals("Line 1: quantity must be greater than zero.",
                zero.fieldErrors().get("line1.quantity"));
        assertEquals("Line 1: quantity must be greater than zero.",
                negative.fieldErrors().get("line1.quantity"));
    }

    @Test
    void rejectsSupplierDifferentFromOriginalPurchase() {
        UUID purchaseId = UUID.randomUUID();
        UUID originalSupplier = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        PurchaseReturnDraft draft = draft(purchaseId, UUID.randomUUID(),
                line(lineId, batchId, 1, 100));

        PurchaseReturnValidationException exception = assertThrows(
                PurchaseReturnValidationException.class,
                () -> PurchaseReturnValidator.validate(draft,
                        purchase(purchaseId, originalSupplier), Map.of(lineId,
                                availability(lineId, purchaseId, batchId, 5, 0, 5))));

        assertEquals("Supplier does not match the original purchase.",
                exception.fieldErrors().get("supplier"));
    }

    @Test
    void rejectsLineAndBatchThatDoNotBelongToOriginalPurchase() {
        UUID purchaseId = UUID.randomUUID();
        UUID supplierId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();
        UUID actualBatch = UUID.randomUUID();
        PurchaseReturnDraft draft = draft(purchaseId, supplierId,
                line(lineId, UUID.randomUUID(), 1, 100));

        PurchaseReturnValidationException exception = assertThrows(
                PurchaseReturnValidationException.class,
                () -> PurchaseReturnValidator.validate(draft,
                        purchase(purchaseId, supplierId), Map.of(lineId,
                                availability(lineId, UUID.randomUUID(), actualBatch, 5, 0, 5))));

        assertEquals("Line 1: original line does not belong to this purchase.",
                exception.fieldErrors().get("line1.originalLine"));
        assertEquals("Line 1: batch does not match the original purchase line.",
                exception.fieldErrors().get("line1.batch"));
    }

    @Test
    void rejectsReturnBeyondRemainingOriginalQuantity() {
        UUID purchaseId = UUID.randomUUID();
        UUID supplierId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        PurchaseReturnDraft draft = draft(purchaseId, supplierId,
                line(lineId, batchId, 3, 100));

        PurchaseReturnValidationException exception = assertThrows(
                PurchaseReturnValidationException.class,
                () -> PurchaseReturnValidator.validate(draft,
                        purchase(purchaseId, supplierId), Map.of(lineId,
                                availability(lineId, purchaseId, batchId, 5, 3, 5))));

        assertEquals("Return quantity 3 exceeds remaining returnable quantity 2 for the original purchase line.",
                exception.fieldErrors().get("remaining." + lineId));
    }

    @Test
    void rejectsReturnBeyondCurrentBatchStock() {
        UUID purchaseId = UUID.randomUUID();
        UUID supplierId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        PurchaseReturnDraft draft = draft(purchaseId, supplierId,
                line(lineId, batchId, 4, 100));

        PurchaseReturnValidationException exception = assertThrows(
                PurchaseReturnValidationException.class,
                () -> PurchaseReturnValidator.validate(draft,
                        purchase(purchaseId, supplierId), Map.of(lineId,
                                availability(lineId, purchaseId, batchId, 10, 0, 3))));

        assertEquals("Purchase return quantity 4 exceeds current available batch stock 3.",
                exception.fieldErrors().get("stock." + batchId));
    }

    @Test
    void acceptsPartialReturnAndCalculatesExactOriginalCostTotal() {
        UUID purchaseId = UUID.randomUUID();
        UUID supplierId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        PurchaseReturnDraft draft = draft(purchaseId, supplierId,
                line(lineId, batchId, 2, 125));

        PurchaseReturnValidator.validate(draft, purchase(purchaseId, supplierId),
                Map.of(lineId, availability(lineId, purchaseId, batchId, 5, 1, 4)));

        assertEquals(250, PurchaseReturnValidator.totalPaisa(draft));
    }

    @Test
    void requiresExplicitNonLegacySettlementMethod() {
        UUID purchaseId = UUID.randomUUID();
        UUID supplierId = UUID.randomUUID();
        PurchaseReturnLineDraft line = line(UUID.randomUUID(), UUID.randomUUID(), 1, 100);
        PurchaseReturnDraft missing = new PurchaseReturnDraft(purchaseId, supplierId, DATE,
                PurchaseReturnReason.DAMAGED, null, null, List.of(line), null);
        PurchaseReturnDraft legacy = new PurchaseReturnDraft(purchaseId, supplierId, DATE,
                PurchaseReturnReason.DAMAGED, PurchasePaymentMethod.LEGACY_UNSPECIFIED,
                null, List.of(line), null);

        assertEquals("Settlement method is required.", assertThrows(
                PurchaseReturnValidationException.class,
                () -> PurchaseReturnValidator.validate(missing))
                .fieldErrors().get("settlementMethod"));
        assertEquals("Select Cash, QR / digital, or Credit / udharo.", assertThrows(
                PurchaseReturnValidationException.class,
                () -> PurchaseReturnValidator.validate(legacy))
                .fieldErrors().get("settlementMethod"));
    }

    private static PurchaseReturnDraft draft(
            UUID purchaseId, UUID supplierId, PurchaseReturnLineDraft line) {
        return new PurchaseReturnDraft(purchaseId, supplierId, DATE,
                PurchaseReturnReason.DAMAGED, PurchasePaymentMethod.CASH,
                null, List.of(line), null);
    }

    private static PurchaseReturnLineDraft line(
            UUID lineId, UUID batchId, int quantity, long cost) {
        return new PurchaseReturnLineDraft(lineId, batchId, quantity, cost);
    }

    private static Purchase purchase(UUID id, UUID supplierId) {
        return new Purchase(id, supplierId, DATE.minusDays(1), "INV-1",
                PurchasePaymentMethod.CASH, 500,
                Instant.parse("2026-09-13T00:00:00Z"), null);
    }

    private static PurchaseReturnLineAvailability availability(
            UUID lineId, UUID purchaseId, UUID batchId, int received,
            long returned, long stock) {
        return new PurchaseReturnLineAvailability(new PurchaseLine(
                lineId, purchaseId, batchId, received, 100, received * 100L),
                UUID.randomUUID(), returned, stock);
    }
}
