package com.nepalpharmacy.sales;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record SaleDraft(
        UUID customerId,
        LocalDate saleDate,
        PaymentMethod paymentMethod,
        List<SaleLineDraft> lines,
        UUID createdBy
) {

    public SaleDraft normalized() {
        return new SaleDraft(
                customerId,
                saleDate,
                paymentMethod,
                lines == null ? null : lines.stream().toList(),
                createdBy);
    }
}
