package com.nepalpharmacy.sales;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record SaleSummary(
        UUID id,
        long invoiceNumber,
        LocalDate saleDate,
        String customerName,
        PaymentMethod paymentMethod,
        long totalAmountPaisa,
        SaleReturnStatus returnStatus
) {
    public SaleSummary {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(saleDate, "saleDate");
        Objects.requireNonNull(customerName, "customerName");
        Objects.requireNonNull(paymentMethod, "paymentMethod");
        Objects.requireNonNull(returnStatus, "returnStatus");
    }
}
