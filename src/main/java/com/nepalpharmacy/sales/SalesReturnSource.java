package com.nepalpharmacy.sales;

import java.util.List;
import java.util.Objects;

public record SalesReturnSource(Sale sale, List<SalesReturnSourceLine> lines) {

    public SalesReturnSource {
        Objects.requireNonNull(sale, "sale");
        lines = List.copyOf(lines);
    }
}
