package com.nepalpharmacy.party;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class CustomerService {

    private final CustomerRepository repository;
    private final Clock clock;
    private final java.util.function.Supplier<UUID> idGenerator;

    public CustomerService(CustomerRepository repository) {
        this(repository, Clock.systemUTC(), UUID::randomUUID);
    }

    public CustomerService(
            CustomerRepository repository,
            Clock clock,
            java.util.function.Supplier<UUID> idGenerator
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
    }

    public Customer create(CustomerDraft input) {
        CustomerDraft draft = input == null ? null : input.normalized();
        CustomerValidator.validate(draft);
        Instant now = clock.instant();
        Customer customer = new Customer(
                idGenerator.get(), draft.name(), draft.phone(), draft.address(),
                draft.active(), now, now);
        repository.insert(customer);
        return customer;
    }

    public List<Customer> findAllActive() {
        return repository.findAllActive();
    }
}
