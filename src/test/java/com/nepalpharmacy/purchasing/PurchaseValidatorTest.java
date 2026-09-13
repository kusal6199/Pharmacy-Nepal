package com.nepalpharmacy.purchasing;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PurchaseValidatorTest {

    private static final LocalDate PURCHASE_DATE = LocalDate.of(2026, 9, 13);

    @Test
    void rejectsExpiryInThePast() {
        PurchaseDraft draft = draft(List.of(line(PURCHASE_DATE.minusDays(1))));

        PurchaseValidationException exception = assertThrows(
                PurchaseValidationException.class, () -> PurchaseValidator.validate(draft));

        assertEquals(
                "Line 1: expiry must be after the purchase date.",
                exception.fieldErrors().get("line1.expiryDate"));
    }

    @Test
    void rejectsAnEmptyPurchase() {
        PurchaseValidationException exception = assertThrows(
                PurchaseValidationException.class,
                () -> PurchaseValidator.validate(draft(List.of())));

        assertEquals("Add at least one purchase line.", exception.fieldErrors().get("lines"));
    }

    @Test
    void rejectsNonPositiveQuantityAndNegativeUnitPrice() {
        PurchaseLineDraft invalid = new PurchaseLineDraft(
                UUID.randomUUID(), "B-1", PURCHASE_DATE.plusMonths(6), null, 0, -1);

        PurchaseValidationException exception = assertThrows(
                PurchaseValidationException.class,
                () -> PurchaseValidator.validate(draft(List.of(invalid))));

        assertEquals("Line 1: quantity must be greater than zero.",
                exception.fieldErrors().get("line1.quantity"));
        assertEquals("Line 1: unit purchase price cannot be negative.",
                exception.fieldErrors().get("line1.price"));
    }

    private static PurchaseDraft draft(List<PurchaseLineDraft> lines) {
        return new PurchaseDraft(UUID.randomUUID(), PURCHASE_DATE, "INV-1", lines, null);
    }

    private static PurchaseLineDraft line(LocalDate expiryDate) {
        return new PurchaseLineDraft(
                UUID.randomUUID(), "B-1", expiryDate, null, 10, 125);
    }
}
