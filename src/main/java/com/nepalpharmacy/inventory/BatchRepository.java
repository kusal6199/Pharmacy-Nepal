package com.nepalpharmacy.inventory;

import com.nepalpharmacy.shared.persistence.TransactionContext;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BatchRepository {

    Optional<Batch> findByIdentity(
            TransactionContext transaction,
            UUID productId,
            String batchNumber,
            LocalDate expiryDate);

    Optional<BatchStock> findByIdWithStock(TransactionContext transaction, UUID batchId);

    void insert(TransactionContext transaction, Batch batch);

    List<BatchStock> findAvailableByProduct(UUID productId, LocalDate asOfDate);

    long count();
}
