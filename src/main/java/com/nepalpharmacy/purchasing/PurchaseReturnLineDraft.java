package com.nepalpharmacy.purchasing;

import java.util.UUID;

public record PurchaseReturnLineDraft(
        UUID originalPurchaseLineId,
        UUID batchId,
        int quantityReturnedBaseUnits,
        long unitCostPaisa
) {

    public long lineTotalPaisa() {
        return Math.multiplyExact(quantityReturnedBaseUnits, unitCostPaisa);
    }
}
