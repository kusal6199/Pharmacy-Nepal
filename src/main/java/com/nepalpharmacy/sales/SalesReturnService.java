package com.nepalpharmacy.sales;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class SalesReturnService {

    private final SalesReturnEntryRepository repository;
    private final Clock clock;

    public SalesReturnService(SalesReturnEntryRepository repository) {
        this(repository, Clock.systemUTC());
    }

    public SalesReturnService(SalesReturnEntryRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Optional<SalesReturnSource> findSourceByInvoiceNumber(long invoiceNumber) {
        return repository.findSourceByInvoiceNumber(invoiceNumber);
    }

    public SalesReturnLineDraft prepareLine(SalesReturnSourceLine source, int quantity) {
        Objects.requireNonNull(source, "source");
        SalesReturnLineDraft line = new SalesReturnLineDraft(
                source.originalSaleLineId(), source.batchId(), quantity, source.unitPricePaisa());
        SalesReturnValidator.validateLine(line);
        return line;
    }

    public long calculateTotal(List<SalesReturnLineDraft> lines) {
        return lines.isEmpty() ? 0 : SalesReturnValidator.totalPaisa(lines);
    }

    public SalesReturn record(SalesReturnDraft input) {
        SalesReturnDraft draft = input == null ? null : input.normalized();
        SalesReturnValidator.validate(draft);
        return repository.save(draft, clock.instant());
    }
}
