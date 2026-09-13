package com.nepalpharmacy.purchasing;

import java.util.List;
import java.util.Objects;

public record PurchaseReturnSource(
        Purchase purchase,
        String supplierName,
        List<PurchaseReturnSourceLine> lines
) {

    public PurchaseReturnSource {
        Objects.requireNonNull(purchase, "purchase");
        Objects.requireNonNull(supplierName, "supplierName");
        lines = List.copyOf(lines);
    }
}
