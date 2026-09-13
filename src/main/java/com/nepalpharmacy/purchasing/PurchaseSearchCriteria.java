package com.nepalpharmacy.purchasing;

import java.time.LocalDate;

public record PurchaseSearchCriteria(
        LocalDate fromDate,
        LocalDate toDate,
        String supplierName,
        String supplierInvoice
) {
}
