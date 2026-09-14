package com.nepalpharmacy.credit;

import com.nepalpharmacy.sales.PaymentMethod;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record AccountEntry(
        UUID id,
        UUID partyId,
        LocalDate entryDate,
        AccountEntryType entryType,
        long amountPaisa,
        PaymentMethod paymentMethod,
        String referenceText,
        String notes,
        Instant createdAt,
        UUID createdBy
) {
    public AccountEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(entryDate, "entryDate");
        Objects.requireNonNull(entryType, "entryType");
        Objects.requireNonNull(createdAt, "createdAt");
        if (amountPaisa <= 0) {
            throw new IllegalArgumentException("Account-entry amount must be positive.");
        }
    }
}
