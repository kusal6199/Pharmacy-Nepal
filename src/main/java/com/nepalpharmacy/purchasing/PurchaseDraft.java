package com.nepalpharmacy.purchasing;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PurchaseDraft(
        UUID supplierId,
        LocalDate purchaseDate,
        String invoiceNumber,
        List<PurchaseLineDraft> lines,
        UUID createdBy
) {

    public PurchaseDraft normalized() {
        List<PurchaseLineDraft> normalizedLines = lines == null
                ? null
                : lines.stream().map(line -> line == null ? null : line.normalized()).toList();
        String normalizedInvoice = invoiceNumber == null || invoiceNumber.isBlank()
                ? null
                : invoiceNumber.trim();
        return new PurchaseDraft(supplierId, purchaseDate, normalizedInvoice, normalizedLines, createdBy);
    }
}
