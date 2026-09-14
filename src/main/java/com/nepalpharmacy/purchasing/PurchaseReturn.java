package com.nepalpharmacy.purchasing;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record PurchaseReturn(
        UUID id,
        long returnNumber,
        UUID originalPurchaseId,
        UUID supplierId,
        LocalDate returnDate,
        PurchaseReturnReason reason,
        PurchasePaymentMethod settlementMethod,
        String notes,
        long totalAmountPaisa,
        Instant createdAt,
        UUID createdBy
) {

    public PurchaseReturn {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(originalPurchaseId, "originalPurchaseId");
        Objects.requireNonNull(supplierId, "supplierId");
        Objects.requireNonNull(returnDate, "returnDate");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(settlementMethod, "settlementMethod");
        Objects.requireNonNull(createdAt, "createdAt");
        if (returnNumber <= 0) {
            throw new IllegalArgumentException("Purchase return number must be positive.");
        }
        if (totalAmountPaisa < 0) {
            throw new IllegalArgumentException("Purchase return total cannot be negative.");
        }
        if (notes != null && notes.length() > 500) {
            throw new IllegalArgumentException("Purchase return notes must be at most 500 characters.");
        }
    }
}
