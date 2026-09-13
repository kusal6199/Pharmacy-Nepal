package com.nepalpharmacy.sales.infrastructure;

import com.nepalpharmacy.inventory.BatchStock;
import com.nepalpharmacy.inventory.BatchRepository;
import com.nepalpharmacy.inventory.InventoryMovement;
import com.nepalpharmacy.inventory.InventoryMovementRepository;
import com.nepalpharmacy.inventory.InventoryMovementType;
import com.nepalpharmacy.party.Customer;
import com.nepalpharmacy.party.CustomerRepository;
import com.nepalpharmacy.product.Product;
import com.nepalpharmacy.product.ProductRepository;
import com.nepalpharmacy.sales.Sale;
import com.nepalpharmacy.sales.SaleDraft;
import com.nepalpharmacy.sales.SaleEntryRepository;
import com.nepalpharmacy.sales.SaleLine;
import com.nepalpharmacy.sales.SaleLineDraft;
import com.nepalpharmacy.sales.SaleLineRepository;
import com.nepalpharmacy.sales.SaleReceipt;
import com.nepalpharmacy.sales.SaleReceiptLine;
import com.nepalpharmacy.sales.SaleRepository;
import com.nepalpharmacy.sales.SaleValidationException;
import com.nepalpharmacy.sales.SaleValidator;
import com.nepalpharmacy.shared.infrastructure.DataAccessException;
import com.nepalpharmacy.shared.persistence.TransactionContext;
import com.nepalpharmacy.shared.persistence.TransactionRunner;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class JdbcSaleEntryRepository implements SaleEntryRepository {

    private final TransactionRunner transactions;
    private final CustomerRepository customers;
    private final BatchRepository batches;
    private final ProductRepository products;
    private final SaleRepository sales;
    private final SaleLineRepository lines;
    private final InventoryMovementRepository movements;

    public JdbcSaleEntryRepository(
            TransactionRunner transactions,
            CustomerRepository customers,
            BatchRepository batches,
            ProductRepository products,
            SaleRepository sales,
            SaleLineRepository lines,
            InventoryMovementRepository movements
    ) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.customers = Objects.requireNonNull(customers, "customers");
        this.batches = Objects.requireNonNull(batches, "batches");
        this.products = Objects.requireNonNull(products, "products");
        this.sales = Objects.requireNonNull(sales, "sales");
        this.lines = Objects.requireNonNull(lines, "lines");
        this.movements = Objects.requireNonNull(movements, "movements");
    }

    @Override
    public SaleReceipt save(SaleDraft draft, Instant createdAt) {
        try {
            return transactions.inTransaction(transaction -> save(transaction, draft, createdAt));
        } catch (SaleValidationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new DataAccessException("Could not save sale; no stock was changed.", exception);
        }
    }

    private SaleReceipt save(TransactionContext transaction, SaleDraft draft, Instant createdAt) {
        validateCustomer(transaction, draft);

        Map<UUID, BatchStock> stockByBatch = new LinkedHashMap<>();
        Map<UUID, Product> productByBatch = new LinkedHashMap<>();
        for (SaleLineDraft line : draft.lines()) {
            if (line == null || line.batchId() == null || stockByBatch.containsKey(line.batchId())) {
                continue;
            }
            batches.findByIdWithStock(transaction, line.batchId()).ifPresent(stock -> {
                stockByBatch.put(line.batchId(), stock);
                Product product = products.findById(transaction, stock.batch().productId())
                        .orElseThrow(() -> validationError(
                                "product", "Product for selected batch no longer exists."));
                if (!product.active()) {
                    throw validationError("product", "Inactive product cannot be sold: "
                            + product.name() + ".");
                }
                productByBatch.put(line.batchId(), product);
            });
        }

        List<SaleLineDraft> repricedLines = draft.lines().stream()
                .map(line -> reprice(line, productByBatch))
                .toList();
        SaleDraft pricedDraft = new SaleDraft(
                draft.customerId(), draft.saleDate(), draft.paymentMethod(),
                repricedLines, draft.createdBy());
        SaleValidator.validate(pricedDraft, stockByBatch);

        long invoiceNumber = sales.nextInvoiceNumber(transaction);
        UUID saleId = UUID.randomUUID();
        Sale sale = new Sale(
                saleId,
                pricedDraft.customerId(),
                pricedDraft.saleDate(),
                invoiceNumber,
                pricedDraft.paymentMethod(),
                SaleValidator.totalPaisa(pricedDraft),
                createdAt,
                pricedDraft.createdBy());
        sales.insert(transaction, sale);

        List<SaleReceiptLine> receiptLines = new ArrayList<>();
        for (SaleLineDraft line : pricedDraft.lines()) {
            BatchStock stock = stockByBatch.get(line.batchId());
            Product product = productByBatch.get(line.batchId());
            long lineTotal = line.lineTotalPaisa();

            lines.insert(transaction, new SaleLine(
                    UUID.randomUUID(), saleId, line.batchId(),
                    line.quantitySoldBaseUnits(), line.unitSalePricePaisa(), lineTotal));
            movements.insert(transaction, new InventoryMovement(
                    UUID.randomUUID(), line.batchId(), InventoryMovementType.SALE,
                    line.quantitySoldBaseUnits(), saleId, createdAt));
            receiptLines.add(new SaleReceiptLine(
                    product.name(),
                    stock.batch().batchNumber(),
                    stock.batch().expiryDate(),
                    line.quantitySoldBaseUnits(),
                    line.unitSalePricePaisa(),
                    lineTotal));
        }
        return new SaleReceipt(sale, receiptLines);
    }

    private void validateCustomer(TransactionContext transaction, SaleDraft draft) {
        if (draft.customerId() == null) {
            return;
        }
        Customer customer = customers.findById(transaction, draft.customerId())
                .orElseThrow(() -> validationError("customer", "Selected customer no longer exists."));
        if (!customer.active()) {
            throw validationError("customer", "Selected customer is inactive.");
        }
    }

    private static SaleLineDraft reprice(
            SaleLineDraft line, Map<UUID, Product> productByBatch) {
        if (line == null || line.batchId() == null) {
            return line;
        }
        Product product = productByBatch.get(line.batchId());
        return product == null
                ? line
                : new SaleLineDraft(
                        line.batchId(), line.quantitySoldBaseUnits(), product.salePricePaisa());
    }

    private static SaleValidationException validationError(String field, String message) {
        Map<String, String> errors = new LinkedHashMap<>();
        errors.put(field, message);
        return new SaleValidationException(errors);
    }
}
