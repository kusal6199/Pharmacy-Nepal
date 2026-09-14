package com.nepalpharmacy.credit;

import java.util.List;
import java.util.Objects;

public record CustomerAccountDetail(
        CustomerAccountSummary summary,
        List<AccountLedgerEntry> entries
) {
    public CustomerAccountDetail {
        Objects.requireNonNull(summary, "summary");
        entries = List.copyOf(entries);
    }
}
