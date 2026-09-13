package com.nepalpharmacy.sales;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record SalesReturn(
        UUID id,
        long returnNumber,
        UUID originalSaleId,
        LocalDate returnDate,
        SalesReturnReason reason,
        PaymentMethod refundMethod,
        long totalAmountPaisa,
        String notes,
        Instant createdAt,
        UUID createdBy
) {

    public SalesReturn {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(originalSaleId, "originalSaleId");
        Objects.requireNonNull(returnDate, "returnDate");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(refundMethod, "refundMethod");
        Objects.requireNonNull(createdAt, "createdAt");
        if (returnNumber <= 0) {
            throw new IllegalArgumentException("Sales return number must be positive.");
        }
        if (totalAmountPaisa <= 0) {
            throw new IllegalArgumentException("Sales return total must be greater than zero.");
        }
        if (notes != null && notes.length() > 500) {
            throw new IllegalArgumentException("Sales return notes must be at most 500 characters.");
        }
    }
}
