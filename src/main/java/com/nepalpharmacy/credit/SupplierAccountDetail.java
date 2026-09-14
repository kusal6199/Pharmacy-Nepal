package com.nepalpharmacy.credit;

import java.util.List;
import java.util.Objects;

public record SupplierAccountDetail(
        SupplierAccountSummary summary,
        List<AccountLedgerEntry> entries
) {
    public SupplierAccountDetail {
        Objects.requireNonNull(summary, "summary");
        entries = List.copyOf(entries);
    }
}
