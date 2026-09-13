package com.nepalpharmacy.party;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Customer(
        UUID id,
        String name,
        String phone,
        String address,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {

    public Customer {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        CustomerValidator.validate(new CustomerDraft(name, phone, address, active));
    }
}
