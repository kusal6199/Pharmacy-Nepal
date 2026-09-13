package com.nepalpharmacy.sales;

import java.time.LocalDate;

public record SaleReceiptLine(
        String productName,
        String batchNumber,
        LocalDate expiryDate,
        int quantitySoldBaseUnits,
        long unitSalePricePaisa,
        long lineTotalPaisa
) {
}
