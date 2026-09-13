package com.nepalpharmacy.sales;

import java.util.List;
import java.util.Objects;

public record SaleDetail(SaleSummary summary, List<SaleDetailLine> lines) {
    public SaleDetail {
        Objects.requireNonNull(summary, "summary");
        lines = List.copyOf(lines);
    }

    public boolean hasReturnableQuantity() {
        return lines.stream().anyMatch(line -> line.remainingReturnableBaseUnits() > 0);
    }
}
