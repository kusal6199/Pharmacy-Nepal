package com.nepalpharmacy.party;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Supplier(
        UUID id,
        String name,
        String phone,
        String address,
        String pan,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {

    public Supplier {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        SupplierValidator.validate(new SupplierDraft(name, phone, address, pan, active));
    }
}
