package com.nepalpharmacy.purchasing;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PurchaseReturnDraft(
        UUID originalPurchaseId,
        UUID supplierId,
        LocalDate returnDate,
        PurchaseReturnReason reason,
        PurchasePaymentMethod settlementMethod,
        String notes,
        List<PurchaseReturnLineDraft> lines,
        UUID createdBy
) {

    public PurchaseReturnDraft normalized() {
        return new PurchaseReturnDraft(
                originalPurchaseId,
                supplierId,
                returnDate,
                reason,
                settlementMethod,
                notes == null || notes.isBlank() ? null : notes.trim(),
                lines == null ? null : lines.stream().toList(),
                createdBy);
    }
}
