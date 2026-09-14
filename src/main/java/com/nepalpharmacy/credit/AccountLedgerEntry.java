package com.nepalpharmacy.credit;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

public record AccountLedgerEntry(
        LocalDate businessDate,
        Instant createdAt,
        String stableKey,
        String activity,
        String reference,
        String notes,
        long increasePaisa,
        long decreasePaisa,
        long runningBalancePaisa
) {
    public AccountLedgerEntry {
        Objects.requireNonNull(businessDate, "businessDate");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(stableKey, "stableKey");
        Objects.requireNonNull(activity, "activity");
        if (increasePaisa < 0 || decreasePaisa < 0) {
            throw new IllegalArgumentException("Ledger columns cannot be negative.");
        }
    }
}
