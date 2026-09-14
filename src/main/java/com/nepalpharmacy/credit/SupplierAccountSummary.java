package com.nepalpharmacy.credit;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record SupplierAccountSummary(
        UUID supplierId,
        String supplierName,
        String phone,
        boolean active,
        long balancePaisa,
        LocalDate lastActivityDate
) {
    public SupplierAccountSummary {
        Objects.requireNonNull(supplierId, "supplierId");
        Objects.requireNonNull(supplierName, "supplierName");
    }
}
