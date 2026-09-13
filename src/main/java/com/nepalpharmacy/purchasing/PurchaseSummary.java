package com.nepalpharmacy.purchasing;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record PurchaseSummary(
        UUID id,
        LocalDate purchaseDate,
        String supplierName,
        String supplierInvoice,
        long totalAmountPaisa
) {
    public PurchaseSummary {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(purchaseDate, "purchaseDate");
        Objects.requireNonNull(supplierName, "supplierName");
    }
}
