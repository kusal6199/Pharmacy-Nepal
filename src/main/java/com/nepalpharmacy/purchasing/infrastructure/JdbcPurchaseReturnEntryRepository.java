package com.nepalpharmacy.purchasing.infrastructure;

import com.nepalpharmacy.inventory.BatchStock;
import com.nepalpharmacy.inventory.BatchRepository;
import com.nepalpharmacy.inventory.InventoryMovement;
import com.nepalpharmacy.inventory.InventoryMovementRepository;
import com.nepalpharmacy.inventory.InventoryMovementType;
import com.nepalpharmacy.party.Supplier;
import com.nepalpharmacy.party.SupplierRepository;
import com.nepalpharmacy.product.Product;
import com.nepalpharmacy.product.ProductRepository;
import com.nepalpharmacy.purchasing.Purchase;
import com.nepalpharmacy.purchasing.PurchaseLine;
import com.nepalpharmacy.purchasing.PurchaseLineRepository;
import com.nepalpharmacy.purchasing.PurchaseRepository;
import com.nepalpharmacy.purchasing.PurchaseReturn;
import com.nepalpharmacy.purchasing.PurchaseReturnDraft;
import com.nepalpharmacy.purchasing.PurchaseReturnEntryRepository;
import com.nepalpharmacy.purchasing.PurchaseReturnLine;
import com.nepalpharmacy.purchasing.PurchaseReturnLineAvailability;
import com.nepalpharmacy.purchasing.PurchaseReturnLineDraft;
import com.nepalpharmacy.purchasing.PurchaseReturnLineRepository;
import com.nepalpharmacy.purchasing.PurchaseReturnRepository;
import com.nepalpharmacy.purchasing.PurchaseReturnSource;
import com.nepalpharmacy.purchasing.PurchaseReturnSourceLine;
import com.nepalpharmacy.purchasing.PurchaseReturnValidationException;
import com.nepalpharmacy.purchasing.PurchaseReturnValidator;
import com.nepalpharmacy.purchasing.RecentPurchase;
import com.nepalpharmacy.shared.infrastructure.DataAccessException;
import com.nepalpharmacy.shared.persistence.TransactionContext;
import com.nepalpharmacy.shared.persistence.TransactionRunner;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class JdbcPurchaseReturnEntryRepository implements PurchaseReturnEntryRepository {

    private final TransactionRunner transactions;
    private final PurchaseRepository purchases;
    private final PurchaseLineRepository purchaseLines;
    private final SupplierRepository suppliers;
    private final BatchRepository batches;
    private final ProductRepository products;
    private final PurchaseReturnRepository returns;
    private final PurchaseReturnLineRepository returnLines;
    private final InventoryMovementRepository movements;

    public JdbcPurchaseReturnEntryRepository(
            TransactionRunner transactions,
            PurchaseRepository purchases,
            PurchaseLineRepository purchaseLines,
            SupplierRepository suppliers,
            BatchRepository batches,
            ProductRepository products,
            PurchaseReturnRepository returns,
            PurchaseReturnLineRepository returnLines,
            InventoryMovementRepository movements
    ) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.purchases = Objects.requireNonNull(purchases, "purchases");
        this.purchaseLines = Objects.requireNonNull(purchaseLines, "purchaseLines");
        this.suppliers = Objects.requireNonNull(suppliers, "suppliers");
        this.batches = Objects.requireNonNull(batches, "batches");
        this.products = Objects.requireNonNull(products, "products");
        this.returns = Objects.requireNonNull(returns, "returns");
        this.returnLines = Objects.requireNonNull(returnLines, "returnLines");
        this.movements = Objects.requireNonNull(movements, "movements");
    }

    @Override
    public List<RecentPurchase> findRecent(int limit) {
        return purchases.findRecent(limit);
    }

    @Override
    public Optional<PurchaseReturnSource> findSource(UUID purchaseId) {
        if (purchaseId == null) {
            return Optional.empty();
        }
        try {
            return transactions.inTransaction(transaction -> purchases
                    .findById(transaction, purchaseId)
                    .map(purchase -> source(transaction, purchase)));
        } catch (RuntimeException exception) {
            throw new DataAccessException("Could not load purchase for return.", exception);
        }
    }

    @Override
    public PurchaseReturn save(PurchaseReturnDraft input, Instant createdAt) {
        PurchaseReturnDraft draft = input == null ? null : input.normalized();
        PurchaseReturnValidator.validate(draft);
        try {
            return transactions.inTransaction(transaction -> save(transaction, draft, createdAt));
        } catch (PurchaseReturnValidationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new DataAccessException(
                    "Could not save purchase return; no stock was changed.", exception);
        }
    }

    private PurchaseReturn save(
            TransactionContext transaction, PurchaseReturnDraft draft, Instant createdAt) {
        Purchase originalPurchase = purchases.findById(transaction, draft.originalPurchaseId())
                .orElseThrow(() -> validationError(
                        "originalPurchase", "Original purchase no longer exists."));
        if (suppliers.findById(transaction, draft.supplierId()).isEmpty()) {
            throw validationError("supplier", "Supplier no longer exists.");
        }

        Map<UUID, PurchaseReturnLineAvailability> availability = new LinkedHashMap<>();
        Map<UUID, PurchaseReturnLineDraft> pricedByOriginalLine = new LinkedHashMap<>();
        for (PurchaseReturnLineDraft requested : draft.lines()) {
            PurchaseLine originalLine = purchaseLines
                    .findById(transaction, requested.originalPurchaseLineId())
                    .orElse(null);
            if (originalLine == null) {
                continue;
            }
            BatchStock stock = batches.findByIdWithStock(transaction, originalLine.batchId())
                    .orElseThrow(() -> validationError(
                            "batch", "Original purchase batch no longer exists."));
            availability.put(originalLine.id(), new PurchaseReturnLineAvailability(
                    originalLine,
                    stock.batch().productId(),
                    returnLines.returnedQuantityForOriginalLine(transaction, originalLine.id()),
                    stock.quantityBaseUnits()));
            pricedByOriginalLine.put(originalLine.id(), new PurchaseReturnLineDraft(
                    requested.originalPurchaseLineId(), requested.batchId(),
                    requested.quantityReturnedBaseUnits(), originalLine.unitPurchasePricePaisa()));
        }

        List<PurchaseReturnLineDraft> pricedLines = draft.lines().stream()
                .map(line -> pricedByOriginalLine.getOrDefault(line.originalPurchaseLineId(), line))
                .toList();
        PurchaseReturnDraft pricedDraft = new PurchaseReturnDraft(
                draft.originalPurchaseId(), draft.supplierId(), draft.returnDate(),
                draft.reason(), draft.notes(), pricedLines, draft.createdBy());
        PurchaseReturnValidator.validate(pricedDraft, originalPurchase, availability);

        long returnNumber = returns.nextReturnNumber(transaction);
        UUID returnId = UUID.randomUUID();
        PurchaseReturn purchaseReturn = new PurchaseReturn(
                returnId, returnNumber, originalPurchase.id(), originalPurchase.supplierId(),
                pricedDraft.returnDate(), pricedDraft.reason(), pricedDraft.notes(),
                PurchaseReturnValidator.totalPaisa(pricedDraft), createdAt,
                pricedDraft.createdBy());
        returns.insert(transaction, purchaseReturn);

        for (PurchaseReturnLineDraft line : pricedDraft.lines()) {
            PurchaseReturnLineAvailability available =
                    availability.get(line.originalPurchaseLineId());
            long lineTotal = line.lineTotalPaisa();
            returnLines.insert(transaction, new PurchaseReturnLine(
                    UUID.randomUUID(), returnId, line.originalPurchaseLineId(),
                    available.productId(), line.batchId(), line.quantityReturnedBaseUnits(),
                    line.unitCostPaisa(), lineTotal));
            movements.insert(transaction, new InventoryMovement(
                    UUID.randomUUID(), line.batchId(), InventoryMovementType.PURCHASE_RETURN,
                    line.quantityReturnedBaseUnits(), returnId, createdAt));
        }
        return purchaseReturn;
    }

    private PurchaseReturnSource source(TransactionContext transaction, Purchase purchase) {
        Supplier supplier = suppliers.findById(transaction, purchase.supplierId())
                .orElseThrow(() -> new DataAccessException(
                        "Supplier for original purchase no longer exists."));
        List<PurchaseReturnSourceLine> sourceLines = new ArrayList<>();
        for (PurchaseLine line : purchaseLines.findByPurchaseId(transaction, purchase.id())) {
            BatchStock stock = batches.findByIdWithStock(transaction, line.batchId())
                    .orElseThrow(() -> new DataAccessException(
                            "Original purchase batch no longer exists."));
            Product product = products.findById(transaction, stock.batch().productId())
                    .orElseThrow(() -> new DataAccessException(
                            "Product for original purchase line no longer exists."));
            long returned = returnLines.returnedQuantityForOriginalLine(transaction, line.id());
            sourceLines.add(new PurchaseReturnSourceLine(
                    line.id(), product.id(), product.name(), line.batchId(),
                    stock.batch().batchNumber(), stock.batch().expiryDate(),
                    line.quantityReceivedBaseUnits(), returned,
                    line.quantityReceivedBaseUnits() - returned,
                    stock.quantityBaseUnits(), line.unitPurchasePricePaisa()));
        }
        return new PurchaseReturnSource(purchase, supplier.name(), sourceLines);
    }

    private static PurchaseReturnValidationException validationError(
            String field, String message) {
        Map<String, String> errors = new LinkedHashMap<>();
        errors.put(field, message);
        return new PurchaseReturnValidationException(errors);
    }
}
