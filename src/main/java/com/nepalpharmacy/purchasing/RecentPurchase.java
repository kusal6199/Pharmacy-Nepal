package com.nepalpharmacy.purchasing;

import java.time.LocalDate;
import java.util.UUID;

public record RecentPurchase(
        UUID id,
        LocalDate purchaseDate,
        String supplierName,
        String invoiceNumber,
        PurchasePaymentMethod paymentMethod,
        long totalAmountPaisa
) {
}
