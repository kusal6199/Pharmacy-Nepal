package com.nepalpharmacy.credit;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record CustomerAccountSummary(
        UUID customerId,
        String customerName,
        String phone,
        boolean active,
        long balancePaisa,
        LocalDate lastActivityDate
) {
    public CustomerAccountSummary {
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(customerName, "customerName");
    }
}
