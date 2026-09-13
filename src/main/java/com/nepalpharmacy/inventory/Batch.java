package com.nepalpharmacy.inventory;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record Batch(
        UUID id,
        UUID productId,
        String batchNumber,
        LocalDate expiryDate,
        LocalDate manufacturingDate,
        long purchasePricePaisa,
        Instant createdAt
) {

    public Batch {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(batchNumber, "batchNumber");
        Objects.requireNonNull(expiryDate, "expiryDate");
        Objects.requireNonNull(createdAt, "createdAt");
        if (batchNumber.isBlank() || batchNumber.length() > 80) {
            throw new IllegalArgumentException("Batch number must be between 1 and 80 characters.");
        }
        if (purchasePricePaisa < 0) {
            throw new IllegalArgumentException("Batch purchase price cannot be negative.");
        }
    }
}
