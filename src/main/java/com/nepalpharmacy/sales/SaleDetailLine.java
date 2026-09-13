package com.nepalpharmacy.sales;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record SaleDetailLine(
        UUID saleLineId,
        String productName,
        String batchNumber,
        LocalDate expiryDate,
        int quantitySoldBaseUnits,
        long unitSalePricePaisa,
        long lineTotalPaisa,
        long previouslyReturnedBaseUnits,
        long remainingReturnableBaseUnits
) {
    public SaleDetailLine {
        Objects.requireNonNull(saleLineId, "saleLineId");
        Objects.requireNonNull(productName, "productName");
        Objects.requireNonNull(batchNumber, "batchNumber");
        Objects.requireNonNull(expiryDate, "expiryDate");
    }
}
