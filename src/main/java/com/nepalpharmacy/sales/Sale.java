package com.nepalpharmacy.sales;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record Sale(
        UUID id,
        UUID customerId,
        LocalDate saleDate,
        long invoiceNumber,
        PaymentMethod paymentMethod,
        long totalAmountPaisa,
        Instant createdAt,
        UUID createdBy
) {

    public Sale {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(saleDate, "saleDate");
        Objects.requireNonNull(paymentMethod, "paymentMethod");
        Objects.requireNonNull(createdAt, "createdAt");
        if (invoiceNumber <= 0) {
            throw new IllegalArgumentException("Invoice number must be positive.");
        }
        if (paymentMethod == PaymentMethod.CREDIT && customerId == null) {
            throw new IllegalArgumentException("Customer is required for a credit sale.");
        }
        if (totalAmountPaisa <= 0) {
            throw new IllegalArgumentException("Sale total must be greater than zero.");
        }
    }
}
