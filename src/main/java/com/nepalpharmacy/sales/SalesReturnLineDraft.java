package com.nepalpharmacy.sales;

import java.util.UUID;

public record SalesReturnLineDraft(
        UUID originalSaleLineId,
        UUID batchId,
        int quantityReturnedBaseUnits,
        long unitPricePaisa
) {

    public long lineTotalPaisa() {
        return Math.multiplyExact(quantityReturnedBaseUnits, unitPricePaisa);
    }
}
