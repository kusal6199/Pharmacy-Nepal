package com.nepalpharmacy.party;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class SupplierService {

    private final SupplierRepository repository;
    private final Clock clock;
    private final java.util.function.Supplier<UUID> idGenerator;

    public SupplierService(SupplierRepository repository) {
        this(repository, Clock.systemUTC(), UUID::randomUUID);
    }

    SupplierService(
            SupplierRepository repository,
            Clock clock,
            java.util.function.Supplier<UUID> idGenerator
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
    }

    public Supplier create(SupplierDraft input) {
        SupplierDraft draft = input == null ? null : input.normalized();
        SupplierValidator.validate(draft);
        Instant now = clock.instant();
        Supplier supplier = new Supplier(
                idGenerator.get(), draft.name(), draft.phone(), draft.address(), draft.pan(),
                draft.active(), now, now);
        repository.insert(supplier);
        return supplier;
    }

    public List<Supplier> findAllActive() {
        return repository.findAllActive();
    }
}
