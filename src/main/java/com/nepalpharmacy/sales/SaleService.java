package com.nepalpharmacy.sales;

import com.nepalpharmacy.inventory.BatchRepository;
import com.nepalpharmacy.inventory.BatchStock;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class SaleService {

    private final SaleEntryRepository entryRepository;
    private final BatchRepository batches;
    private final Clock clock;

    public SaleService(SaleEntryRepository entryRepository, BatchRepository batches) {
        this(entryRepository, batches, Clock.systemUTC());
    }

    public SaleService(
            SaleEntryRepository entryRepository,
            BatchRepository batches,
            Clock clock
    ) {
        this.entryRepository = Objects.requireNonNull(entryRepository, "entryRepository");
        this.batches = Objects.requireNonNull(batches, "batches");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public List<BatchStock> findAvailableBatches(UUID productId, LocalDate saleDate) {
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(saleDate, "saleDate");
        return batches.findAvailableByProduct(productId, saleDate);
    }

    public SaleLineDraft prepareLine(UUID batchId, int quantity, long currentSalePricePaisa) {
        SaleLineDraft line = new SaleLineDraft(batchId, quantity, currentSalePricePaisa);
        SaleValidator.validateLine(line);
        return line;
    }

    public long calculateTotal(List<SaleLineDraft> lines) {
        return lines.isEmpty() ? 0 : SaleValidator.totalPaisa(lines);
    }

    public SaleReceipt record(SaleDraft input) {
        SaleDraft draft = input == null ? null : input.normalized();
        SaleValidator.validate(draft);
        return entryRepository.save(draft, clock.instant());
    }
}
