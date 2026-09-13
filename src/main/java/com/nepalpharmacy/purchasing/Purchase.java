package com.nepalpharmacy.purchasing;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record Purchase(
        UUID id,
        UUID supplierId,
        LocalDate purchaseDate,
        String invoiceNumber,
        long totalAmountPaisa,
        Instant createdAt,
        UUID createdBy
) {

    public Purchase {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(supplierId, "supplierId");
        Objects.requireNonNull(purchaseDate, "purchaseDate");
        Objects.requireNonNull(createdAt, "createdAt");
        if (totalAmountPaisa < 0) {
            throw new IllegalArgumentException("Purchase total cannot be negative.");
        }
    }
}
