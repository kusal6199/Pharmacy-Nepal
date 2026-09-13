package com.nepalpharmacy.sales;

import java.util.List;

public record SaleHistoryResult(List<SaleSummary> sales, boolean truncated) {
    public SaleHistoryResult {
        sales = List.copyOf(sales);
    }
}
