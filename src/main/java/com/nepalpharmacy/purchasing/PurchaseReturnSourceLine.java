package com.nepalpharmacy.purchasing;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record PurchaseReturnSourceLine(
        UUID originalPurchaseLineId,
        UUID productId,
        String productName,
        UUID batchId,
        String batchNumber,
        LocalDate expiryDate,
        int receivedBaseUnits,
        long previouslyReturnedBaseUnits,
        long remainingReturnableBaseUnits,
        long availableBatchQuantityBaseUnits,
        long unitCostPaisa
) {

    public PurchaseReturnSourceLine {
        Objects.requireNonNull(originalPurchaseLineId, "originalPurchaseLineId");
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(productName, "productName");
        Objects.requireNonNull(batchId, "batchId");
        Objects.requireNonNull(batchNumber, "batchNumber");
        Objects.requireNonNull(expiryDate, "expiryDate");
    }
}
