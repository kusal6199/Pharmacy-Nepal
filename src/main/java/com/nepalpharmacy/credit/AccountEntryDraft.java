package com.nepalpharmacy.credit;

import com.nepalpharmacy.sales.PaymentMethod;

import java.time.LocalDate;
import java.util.UUID;

public record AccountEntryDraft(
        UUID partyId,
        LocalDate entryDate,
        AccountEntryType entryType,
        long amountPaisa,
        PaymentMethod paymentMethod,
        String referenceText,
        String notes,
        UUID createdBy
) {
    public AccountEntryDraft normalized() {
        return new AccountEntryDraft(
                partyId, entryDate, entryType, amountPaisa, paymentMethod,
                normalize(referenceText), normalize(notes), createdBy);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
