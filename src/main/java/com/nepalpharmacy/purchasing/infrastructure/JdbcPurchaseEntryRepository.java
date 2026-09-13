package com.nepalpharmacy.purchasing.infrastructure;

import com.nepalpharmacy.inventory.Batch;
import com.nepalpharmacy.inventory.BatchRepository;
import com.nepalpharmacy.inventory.InventoryMovement;
import com.nepalpharmacy.inventory.InventoryMovementRepository;
import com.nepalpharmacy.inventory.InventoryMovementType;
import com.nepalpharmacy.party.Supplier;
import com.nepalpharmacy.party.SupplierRepository;
import com.nepalpharmacy.purchasing.Purchase;
import com.nepalpharmacy.purchasing.PurchaseDraft;
import com.nepalpharmacy.purchasing.PurchaseEntryRepository;
import com.nepalpharmacy.purchasing.PurchaseLine;
import com.nepalpharmacy.purchasing.PurchaseLineDraft;
import com.nepalpharmacy.purchasing.PurchaseLineRepository;
import com.nepalpharmacy.purchasing.PurchaseRepository;
import com.nepalpharmacy.purchasing.PurchaseValidationException;
import com.nepalpharmacy.purchasing.PurchaseValidator;
import com.nepalpharmacy.purchasing.RecentPurchase;
import com.nepalpharmacy.shared.infrastructure.DataAccessException;
import com.nepalpharmacy.shared.persistence.TransactionContext;
import com.nepalpharmacy.shared.persistence.TransactionRunner;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class JdbcPurchaseEntryRepository implements PurchaseEntryRepository {

    private final TransactionRunner transactions;
    private final SupplierRepository suppliers;
    private final BatchRepository batches;
    private final PurchaseRepository purchases;
    private final PurchaseLineRepository lines;
    private final InventoryMovementRepository movements;

    public JdbcPurchaseEntryRepository(
            TransactionRunner transactions,
            SupplierRepository suppliers,
            BatchRepository batches,
            PurchaseRepository purchases,
            PurchaseLineRepository lines,
            InventoryMovementRepository movements
    ) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.suppliers = Objects.requireNonNull(suppliers, "suppliers");
        this.batches = Objects.requireNonNull(batches, "batches");
        this.purchases = Objects.requireNonNull(purchases, "purchases");
        this.lines = Objects.requireNonNull(lines, "lines");
        this.movements = Objects.requireNonNull(movements, "movements");
    }

    @Override
    public Purchase save(PurchaseDraft draft, Instant createdAt) {
        try {
            return transactions.inTransaction(transaction -> save(transaction, draft, createdAt));
        } catch (PurchaseValidationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new DataAccessException("Could not save purchase; no stock was changed.", exception);
        }
    }

    @Override
    public List<RecentPurchase> findRecent(int limit) {
        return purchases.findRecent(limit);
    }

    private Purchase save(TransactionContext transaction, PurchaseDraft draft, Instant createdAt) {
        Supplier supplier = suppliers.findById(transaction, draft.supplierId())
                .orElseThrow(() -> validationError("supplier", "Selected supplier no longer exists."));
        if (!supplier.active()) {
            throw validationError("supplier", "Selected supplier is inactive.");
        }

        UUID purchaseId = UUID.randomUUID();
        Purchase purchase = new Purchase(
                purchaseId,
                draft.supplierId(),
                draft.purchaseDate(),
                draft.invoiceNumber(),
                PurchaseValidator.totalPaisa(draft),
                createdAt,
                draft.createdBy());
        purchases.insert(transaction, purchase);

        for (PurchaseLineDraft line : draft.lines()) {
            Batch batch = batches.findByIdentity(
                            transaction, line.productId(), line.batchNumber(), line.expiryDate())
                    .orElseGet(() -> createBatch(transaction, line, createdAt));

            lines.insert(transaction, new PurchaseLine(
                    UUID.randomUUID(),
                    purchaseId,
                    batch.id(),
                    line.quantityReceivedBaseUnits(),
                    line.unitPurchasePricePaisa(),
                    line.lineTotalPaisa()));

            movements.insert(transaction, new InventoryMovement(
                    UUID.randomUUID(),
                    batch.id(),
                    InventoryMovementType.PURCHASE_RECEIPT,
                    line.quantityReceivedBaseUnits(),
                    purchaseId,
                    createdAt));
        }
        return purchase;
    }

    private Batch createBatch(
            TransactionContext transaction,
            PurchaseLineDraft line,
            Instant createdAt
    ) {
        Batch batch = new Batch(
                UUID.randomUUID(),
                line.productId(),
                line.batchNumber(),
                line.expiryDate(),
                line.manufacturingDate(),
                line.unitPurchasePricePaisa(),
                createdAt);
        batches.insert(transaction, batch);
        return batch;
    }

    private static PurchaseValidationException validationError(String field, String message) {
        LinkedHashMap<String, String> errors = new LinkedHashMap<>();
        errors.put(field, message);
        return new PurchaseValidationException(errors);
    }
}
