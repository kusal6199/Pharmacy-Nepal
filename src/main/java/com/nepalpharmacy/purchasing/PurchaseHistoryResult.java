package com.nepalpharmacy.purchasing;

import java.util.List;

public record PurchaseHistoryResult(List<PurchaseSummary> purchases, boolean truncated) {
    public PurchaseHistoryResult {
        purchases = List.copyOf(purchases);
    }
}
