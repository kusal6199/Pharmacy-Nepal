package com.nepalpharmacy.sales;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record SalesReturnDraft(
        UUID originalSaleId,
        LocalDate returnDate,
        SalesReturnReason reason,
        PaymentMethod refundMethod,
        String notes,
        List<SalesReturnLineDraft> lines,
        UUID createdBy
) {

    public SalesReturnDraft normalized() {
        return new SalesReturnDraft(
                originalSaleId,
                returnDate,
                reason,
                refundMethod,
                notes == null || notes.isBlank() ? null : notes.trim(),
                lines == null ? null : lines.stream().toList(),
                createdBy);
    }
}
