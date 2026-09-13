package com.nepalpharmacy.sales;

import java.util.UUID;

public record SaleLineDraft(
        UUID batchId,
        int quantitySoldBaseUnits,
        long unitSalePricePaisa
) {

    public long lineTotalPaisa() {
        return Math.multiplyExact(quantitySoldBaseUnits, unitSalePricePaisa);
    }
}
