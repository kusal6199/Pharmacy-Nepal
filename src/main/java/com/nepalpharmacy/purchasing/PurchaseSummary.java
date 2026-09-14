package com.nepalpharmacy.purchasing;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record PurchaseSummary(
        UUID id,
        LocalDate purchaseDate,
        String supplierName,
        String supplierInvoice,
        PurchasePaymentMethod paymentMethod,
        long totalAmountPaisa
) {
    public PurchaseSummary {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(purchaseDate, "purchaseDate");
        Objects.requireNonNull(supplierName, "supplierName");
        Objects.requireNonNull(paymentMethod, "paymentMethod");
    }
}
