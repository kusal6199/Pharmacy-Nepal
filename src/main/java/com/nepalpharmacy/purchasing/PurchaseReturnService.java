package com.nepalpharmacy.purchasing;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class PurchaseReturnService {

    private final PurchaseReturnEntryRepository repository;
    private final Clock clock;

    public PurchaseReturnService(PurchaseReturnEntryRepository repository) {
        this(repository, Clock.systemUTC());
    }

    public PurchaseReturnService(PurchaseReturnEntryRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public List<RecentPurchase> findRecent() {
        return repository.findRecent(25);
    }

    public Optional<PurchaseReturnSource> findSource(UUID purchaseId) {
        return repository.findSource(purchaseId);
    }

    public PurchaseReturnLineDraft prepareLine(PurchaseReturnSourceLine source, int quantity) {
        Objects.requireNonNull(source, "source");
        PurchaseReturnLineDraft line = new PurchaseReturnLineDraft(
                source.originalPurchaseLineId(), source.batchId(), quantity, source.unitCostPaisa());
        PurchaseReturnValidator.validateLine(line);
        return line;
    }

    public long calculateTotal(List<PurchaseReturnLineDraft> lines) {
        return lines.isEmpty() ? 0 : PurchaseReturnValidator.totalPaisa(lines);
    }

    public PurchaseReturn record(PurchaseReturnDraft input) {
        PurchaseReturnDraft draft = input == null ? null : input.normalized();
        PurchaseReturnValidator.validate(draft);
        return repository.save(draft, clock.instant());
    }
}
