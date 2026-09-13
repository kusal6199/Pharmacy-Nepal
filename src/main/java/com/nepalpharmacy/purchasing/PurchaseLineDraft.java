package com.nepalpharmacy.purchasing;

import java.time.LocalDate;
import java.util.UUID;

public record PurchaseLineDraft(
        UUID productId,
        String batchNumber,
        LocalDate expiryDate,
        LocalDate manufacturingDate,
        int quantityReceivedBaseUnits,
        long unitPurchasePricePaisa
) {

    public PurchaseLineDraft normalized() {
        return new PurchaseLineDraft(
                productId,
                batchNumber == null ? null : batchNumber.trim(),
                expiryDate,
                manufacturingDate,
                quantityReceivedBaseUnits,
                unitPurchasePricePaisa);
    }

    public long lineTotalPaisa() {
        return Math.multiplyExact(quantityReceivedBaseUnits, unitPurchasePricePaisa);
    }
}
