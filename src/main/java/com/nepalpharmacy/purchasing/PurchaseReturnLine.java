package com.nepalpharmacy.purchasing;

import java.util.Objects;
import java.util.UUID;

public record PurchaseReturnLine(
        UUID id,
        UUID purchaseReturnId,
        UUID originalPurchaseLineId,
        UUID productId,
        UUID batchId,
        int quantityReturnedBaseUnits,
        long unitCostPaisa,
        long lineTotalPaisa
) {

    public PurchaseReturnLine {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(purchaseReturnId, "purchaseReturnId");
        Objects.requireNonNull(originalPurchaseLineId, "originalPurchaseLineId");
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(batchId, "batchId");
        if (quantityReturnedBaseUnits <= 0) {
            throw new IllegalArgumentException("Purchase return quantity must be positive.");
        }
        if (unitCostPaisa < 0 || lineTotalPaisa < 0) {
            throw new IllegalArgumentException("Purchase return cost and total cannot be negative.");
        }
        if (lineTotalPaisa != Math.multiplyExact(quantityReturnedBaseUnits, unitCostPaisa)) {
            throw new IllegalArgumentException(
                    "Purchase return line total does not match quantity and unit cost.");
        }
    }
}
