package com.nepalpharmacy.purchasing;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record PurchaseDetailLine(
        UUID purchaseLineId,
        String productName,
        String batchNumber,
        LocalDate expiryDate,
        int quantityReceivedBaseUnits,
        long unitPurchasePricePaisa,
        long lineTotalPaisa,
        long previouslyReturnedBaseUnits,
        long remainingReturnableBaseUnits,
        long currentBatchStockBaseUnits
) {
    public PurchaseDetailLine {
        Objects.requireNonNull(purchaseLineId, "purchaseLineId");
        Objects.requireNonNull(productName, "productName");
        Objects.requireNonNull(batchNumber, "batchNumber");
        Objects.requireNonNull(expiryDate, "expiryDate");
    }

    public long currentlyReturnableBaseUnits() {
        return Math.max(0, Math.min(remainingReturnableBaseUnits, currentBatchStockBaseUnits));
    }
}
