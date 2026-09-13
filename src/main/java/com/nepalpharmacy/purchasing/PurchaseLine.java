package com.nepalpharmacy.purchasing;

import java.util.Objects;
import java.util.UUID;

public record PurchaseLine(
        UUID id,
        UUID purchaseId,
        UUID batchId,
        int quantityReceivedBaseUnits,
        long unitPurchasePricePaisa,
        long lineTotalPaisa
) {

    public PurchaseLine {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(purchaseId, "purchaseId");
        Objects.requireNonNull(batchId, "batchId");
        if (quantityReceivedBaseUnits <= 0) {
            throw new IllegalArgumentException("Quantity received must be greater than zero.");
        }
        if (unitPurchasePricePaisa < 0 || lineTotalPaisa < 0) {
            throw new IllegalArgumentException("Purchase prices cannot be negative.");
        }
        if (lineTotalPaisa != Math.multiplyExact(quantityReceivedBaseUnits, unitPurchasePricePaisa)) {
            throw new IllegalArgumentException("Line total does not match quantity and unit price.");
        }
    }
}
