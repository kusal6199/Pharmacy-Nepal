package com.nepalpharmacy.sales;

import java.util.Objects;
import java.util.UUID;

public record SaleLine(
        UUID id,
        UUID saleId,
        UUID batchId,
        int quantitySoldBaseUnits,
        long unitSalePricePaisa,
        long lineTotalPaisa
) {

    public SaleLine {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(saleId, "saleId");
        Objects.requireNonNull(batchId, "batchId");
        if (quantitySoldBaseUnits <= 0 || unitSalePricePaisa <= 0 || lineTotalPaisa <= 0) {
            throw new IllegalArgumentException("Sale line quantity, price, and total must be positive.");
        }
        if (lineTotalPaisa != Math.multiplyExact(quantitySoldBaseUnits, unitSalePricePaisa)) {
            throw new IllegalArgumentException("Line total does not match quantity and unit price.");
        }
    }
}
