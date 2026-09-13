package com.nepalpharmacy.sales.infrastructure;

import com.nepalpharmacy.inventory.BatchStock;
import com.nepalpharmacy.inventory.BatchRepository;
import com.nepalpharmacy.inventory.InventoryMovement;
import com.nepalpharmacy.inventory.InventoryMovementRepository;
import com.nepalpharmacy.inventory.InventoryMovementType;
import com.nepalpharmacy.product.Product;
import com.nepalpharmacy.product.ProductRepository;
import com.nepalpharmacy.sales.Sale;
import com.nepalpharmacy.sales.SaleLine;
import com.nepalpharmacy.sales.SaleLineRepository;
import com.nepalpharmacy.sales.SaleRepository;
import com.nepalpharmacy.sales.SalesReturn;
import com.nepalpharmacy.sales.SalesReturnDraft;
import com.nepalpharmacy.sales.SalesReturnEntryRepository;
import com.nepalpharmacy.sales.SalesReturnLine;
import com.nepalpharmacy.sales.SalesReturnLineAvailability;
import com.nepalpharmacy.sales.SalesReturnLineDraft;
import com.nepalpharmacy.sales.SalesReturnLineRepository;
import com.nepalpharmacy.sales.SalesReturnRepository;
import com.nepalpharmacy.sales.SalesReturnSource;
import com.nepalpharmacy.sales.SalesReturnSourceLine;
import com.nepalpharmacy.sales.SalesReturnValidationException;
import com.nepalpharmacy.sales.SalesReturnValidator;
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

public final class JdbcSalesReturnEntryRepository implements SalesReturnEntryRepository {

    private final TransactionRunner transactions;
    private final SaleRepository sales;
    private final SaleLineRepository saleLines;
    private final BatchRepository batches;
    private final ProductRepository products;
    private final SalesReturnRepository returns;
    private final SalesReturnLineRepository returnLines;
    private final InventoryMovementRepository movements;

    public JdbcSalesReturnEntryRepository(
            TransactionRunner transactions,
            SaleRepository sales,
            SaleLineRepository saleLines,
            BatchRepository batches,
            ProductRepository products,
            SalesReturnRepository returns,
            SalesReturnLineRepository returnLines,
            InventoryMovementRepository movements
    ) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.sales = Objects.requireNonNull(sales, "sales");
        this.saleLines = Objects.requireNonNull(saleLines, "saleLines");
        this.batches = Objects.requireNonNull(batches, "batches");
        this.products = Objects.requireNonNull(products, "products");
        this.returns = Objects.requireNonNull(returns, "returns");
        this.returnLines = Objects.requireNonNull(returnLines, "returnLines");
        this.movements = Objects.requireNonNull(movements, "movements");
    }

    @Override
    public Optional<SalesReturnSource> findSourceByInvoiceNumber(long invoiceNumber) {
        if (invoiceNumber <= 0) {
            return Optional.empty();
        }
        try {
            return transactions.inTransaction(transaction -> sales
                    .findByInvoiceNumber(transaction, invoiceNumber)
                    .map(sale -> source(transaction, sale)));
        } catch (RuntimeException exception) {
            throw new DataAccessException("Could not load sale for return.", exception);
        }
    }

    @Override
    public SalesReturn save(SalesReturnDraft input, Instant createdAt) {
        SalesReturnDraft draft = input == null ? null : input.normalized();
        SalesReturnValidator.validate(draft);
        try {
            return transactions.inTransaction(transaction -> save(transaction, draft, createdAt));
        } catch (SalesReturnValidationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new DataAccessException(
                    "Could not save sales return; no stock was changed.", exception);
        }
    }

    private SalesReturn save(
            TransactionContext transaction, SalesReturnDraft draft, Instant createdAt) {
        Sale originalSale = sales.findById(transaction, draft.originalSaleId())
                .orElseThrow(() -> validationError(
                        "originalSale", "Original sale no longer exists."));

        Map<UUID, SalesReturnLineAvailability> availability = new LinkedHashMap<>();
        Map<UUID, SalesReturnLineDraft> pricedByOriginalLine = new LinkedHashMap<>();
        for (SalesReturnLineDraft requested : draft.lines()) {
            SaleLine originalLine = saleLines
                    .findById(transaction, requested.originalSaleLineId())
                    .orElse(null);
            if (originalLine == null) {
                continue;
            }
            BatchStock stock = batches.findByIdWithStock(transaction, originalLine.batchId())
                    .orElseThrow(() -> validationError(
                            "batch", "Original sale batch no longer exists."));
            availability.put(originalLine.id(), new SalesReturnLineAvailability(
                    originalLine,
                    stock.batch().productId(),
                    returnLines.returnedQuantityForOriginalLine(transaction, originalLine.id())));
            pricedByOriginalLine.put(originalLine.id(), new SalesReturnLineDraft(
                    requested.originalSaleLineId(),
                    requested.batchId(),
                    requested.quantityReturnedBaseUnits(),
                    originalLine.unitSalePricePaisa()));
        }

        List<SalesReturnLineDraft> pricedLines = draft.lines().stream()
                .map(line -> pricedByOriginalLine.getOrDefault(line.originalSaleLineId(), line))
                .toList();
        SalesReturnDraft pricedDraft = new SalesReturnDraft(
                draft.originalSaleId(), draft.returnDate(), draft.reason(),
                draft.refundMethod(), draft.notes(), pricedLines, draft.createdBy());
        SalesReturnValidator.validate(pricedDraft, availability);

        long returnNumber = returns.nextReturnNumber(transaction);
        UUID returnId = UUID.randomUUID();
        SalesReturn salesReturn = new SalesReturn(
                returnId, returnNumber, originalSale.id(), pricedDraft.returnDate(),
                pricedDraft.reason(), pricedDraft.refundMethod(),
                SalesReturnValidator.totalPaisa(pricedDraft), pricedDraft.notes(),
                createdAt, pricedDraft.createdBy());
        returns.insert(transaction, salesReturn);

        for (SalesReturnLineDraft line : pricedDraft.lines()) {
            SalesReturnLineAvailability available = availability.get(line.originalSaleLineId());
            long lineTotal = line.lineTotalPaisa();
            returnLines.insert(transaction, new SalesReturnLine(
                    UUID.randomUUID(), returnId, line.originalSaleLineId(),
                    available.productId(), line.batchId(), line.quantityReturnedBaseUnits(),
                    line.unitPricePaisa(), lineTotal));
            movements.insert(transaction, new InventoryMovement(
                    UUID.randomUUID(), line.batchId(), InventoryMovementType.SALE_RETURN,
                    line.quantityReturnedBaseUnits(), returnId, createdAt));
        }
        return salesReturn;
    }

    private SalesReturnSource source(TransactionContext transaction, Sale sale) {
        List<SalesReturnSourceLine> sourceLines = new ArrayList<>();
        for (SaleLine line : saleLines.findBySaleId(transaction, sale.id())) {
            BatchStock stock = batches.findByIdWithStock(transaction, line.batchId())
                    .orElseThrow(() -> new DataAccessException(
                            "Original sale batch no longer exists."));
            Product product = products.findById(transaction, stock.batch().productId())
                    .orElseThrow(() -> new DataAccessException(
                            "Product for original sale line no longer exists."));
            long returned = returnLines.returnedQuantityForOriginalLine(transaction, line.id());
            sourceLines.add(new SalesReturnSourceLine(
                    line.id(), product.id(), product.name(), line.batchId(),
                    stock.batch().batchNumber(), stock.batch().expiryDate(),
                    line.quantitySoldBaseUnits(), returned,
                    line.quantitySoldBaseUnits() - returned, line.unitSalePricePaisa()));
        }
        return new SalesReturnSource(sale, sourceLines);
    }

    private static SalesReturnValidationException validationError(String field, String message) {
        Map<String, String> errors = new LinkedHashMap<>();
        errors.put(field, message);
        return new SalesReturnValidationException(errors);
    }
}
