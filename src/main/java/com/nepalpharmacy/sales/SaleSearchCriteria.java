package com.nepalpharmacy.sales;

import java.time.LocalDate;

public record SaleSearchCriteria(
        Long invoiceNumber,
        LocalDate fromDate,
        LocalDate toDate,
        String customerName,
        PaymentMethod paymentMethod
) {
}
