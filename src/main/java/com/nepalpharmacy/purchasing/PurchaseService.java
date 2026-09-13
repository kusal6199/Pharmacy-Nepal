package com.nepalpharmacy.purchasing;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

public final class PurchaseService {

    private final PurchaseEntryRepository repository;
    private final Clock clock;

    public PurchaseService(PurchaseEntryRepository repository) {
        this(repository, Clock.systemUTC());
    }

    public PurchaseService(PurchaseEntryRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Purchase record(PurchaseDraft input) {
        PurchaseDraft draft = input == null ? null : input.normalized();
        PurchaseValidator.validate(draft);
        PurchaseValidator.totalPaisa(draft);
        return repository.save(draft, clock.instant());
    }

    public List<RecentPurchase> findRecent() {
        return repository.findRecent(25);
    }
}
