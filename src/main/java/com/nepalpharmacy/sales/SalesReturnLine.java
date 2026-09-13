package com.nepalpharmacy.sales;

import java.util.Objects;
import java.util.UUID;

public record SalesReturnLine(
        UUID id,
        UUID salesReturnId,
        UUID originalSaleLineId,
        UUID productId,
        UUID batchId,
        int quantityReturnedBaseUnits,
        long unitPricePaisa,
        long lineTotalPaisa
) {

    public SalesReturnLine {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(salesReturnId, "salesReturnId");
        Objects.requireNonNull(originalSaleLineId, "originalSaleLineId");
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(batchId, "batchId");
        if (quantityReturnedBaseUnits <= 0 || unitPricePaisa <= 0 || lineTotalPaisa <= 0) {
            throw new IllegalArgumentException(
                    "Sales return quantity, unit price, and line total must be positive.");
        }
        if (lineTotalPaisa != Math.multiplyExact(quantityReturnedBaseUnits, unitPricePaisa)) {
            throw new IllegalArgumentException(
                    "Sales return line total does not match quantity and unit price.");
        }
    }
}
