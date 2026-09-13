package com.nepalpharmacy.purchasing;

import java.util.List;
import java.util.Objects;

public record PurchaseDetail(PurchaseSummary summary, List<PurchaseDetailLine> lines) {
    public PurchaseDetail {
        Objects.requireNonNull(summary, "summary");
        lines = List.copyOf(lines);
    }

    public boolean hasReturnableQuantity() {
        return lines.stream().anyMatch(line -> line.currentlyReturnableBaseUnits() > 0);
    }
}
