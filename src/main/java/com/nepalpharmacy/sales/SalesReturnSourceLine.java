package com.nepalpharmacy.sales;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record SalesReturnSourceLine(
        UUID originalSaleLineId,
        UUID productId,
        String productName,
        UUID batchId,
        String batchNumber,
        LocalDate expiryDate,
        int soldBaseUnits,
        long previouslyReturnedBaseUnits,
        long remainingReturnableBaseUnits,
        long unitPricePaisa
) {

    public SalesReturnSourceLine {
        Objects.requireNonNull(originalSaleLineId, "originalSaleLineId");
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(productName, "productName");
        Objects.requireNonNull(batchId, "batchId");
        Objects.requireNonNull(batchNumber, "batchNumber");
        Objects.requireNonNull(expiryDate, "expiryDate");
    }
}
