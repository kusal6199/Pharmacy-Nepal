package com.nepalpharmacy.sales;

import java.util.List;

public record SaleReceipt(Sale sale, List<SaleReceiptLine> lines) {

    public SaleReceipt {
        lines = List.copyOf(lines);
    }
}
