package com.nepalpharmacy.sales.infrastructure;

import com.nepalpharmacy.bootstrap.DatabaseBootstrap;
import com.nepalpharmacy.credit.infrastructure.JdbcCustomerAccountRepository;
import com.nepalpharmacy.inventory.BatchStock;
import com.nepalpharmacy.inventory.InventoryMovement;
import com.nepalpharmacy.inventory.InventoryMovementRepository;
import com.nepalpharmacy.inventory.infrastructure.JdbcBatchRepository;
import com.nepalpharmacy.inventory.infrastructure.JdbcInventoryMovementRepository;
import com.nepalpharmacy.party.Customer;
import com.nepalpharmacy.party.CustomerDraft;
import com.nepalpharmacy.party.CustomerService;
import com.nepalpharmacy.party.Supplier;
import com.nepalpharmacy.party.SupplierDraft;
import com.nepalpharmacy.party.SupplierService;
import com.nepalpharmacy.party.infrastructure.JdbcCustomerRepository;
import com.nepalpharmacy.party.infrastructure.JdbcSupplierRepository;
import com.nepalpharmacy.product.Product;
import com.nepalpharmacy.product.ProductCategory;
import com.nepalpharmacy.product.ProductDraft;
import com.nepalpharmacy.product.ProductService;
import com.nepalpharmacy.product.UnitOfSale;
import com.nepalpharmacy.product.infrastructure.JdbcProductRepository;
import com.nepalpharmacy.purchasing.PurchaseDraft;
import com.nepalpharmacy.purchasing.PurchaseLineDraft;
import com.nepalpharmacy.purchasing.PurchaseService;
import com.nepalpharmacy.purchasing.infrastructure.JdbcPurchaseEntryRepository;
import com.nepalpharmacy.purchasing.infrastructure.JdbcPurchaseLineRepository;
import com.nepalpharmacy.purchasing.infrastructure.JdbcPurchaseRepository;
import com.nepalpharmacy.sales.PaymentMethod;
import com.nepalpharmacy.sales.SaleDraft;
import com.nepalpharmacy.sales.SaleLineDraft;
import com.nepalpharmacy.sales.SaleReceipt;
import com.nepalpharmacy.sales.SaleService;
import com.nepalpharmacy.sales.SalesReturn;
import com.nepalpharmacy.sales.SalesReturnDraft;
import com.nepalpharmacy.sales.SalesReturnLineDraft;
import com.nepalpharmacy.sales.SalesReturnReason;
import com.nepalpharmacy.sales.SalesReturnService;
import com.nepalpharmacy.sales.SalesReturnSource;
import com.nepalpharmacy.sales.SalesReturnValidationException;
import com.nepalpharmacy.sales.SalesReturnValidator;
import com.nepalpharmacy.shared.infrastructure.DataAccessException;
import com.nepalpharmacy.shared.infrastructure.JdbcTransactionRunner;
import com.nepalpharmacy.shared.persistence.TransactionContext;
import com.nepalpharmacy.shared.persistence.TransactionRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcSalesReturnEntryRepositoryTest {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 13);

    @TempDir
    Path temporaryDirectory;

    private DatabaseBootstrap database;
    private TransactionRunner transactions;
    private JdbcProductRepository productRepository;
    private JdbcCustomerRepository customerRepository;
    private JdbcCustomerAccountRepository customerAccounts;
    private ProductService productService;
    private Product product;
    private Customer customer;
    private Supplier supplier;
    private PurchaseService purchaseService;
    private SaleService saleService;
    private SalesReturnService returnService;
    private JdbcBatchRepository batches;
    private JdbcSaleRepository sales;
    private JdbcSaleLineRepository saleLines;
    private JdbcSalesReturnRepository returns;
    private JdbcSalesReturnLineRepository returnLines;
    private JdbcInventoryMovementRepository movements;

    @BeforeEach
    void setUp() {
        database = new DatabaseBootstrap(temporaryDirectory.resolve("pharmacy.db"));
        database.migrate();
        transactions = new JdbcTransactionRunner(database::openConnection);
        productRepository = new JdbcProductRepository(database::openConnection);
        customerRepository = new JdbcCustomerRepository(database::openConnection);
        customerAccounts = new JdbcCustomerAccountRepository(database::openConnection);
        productService = new ProductService(productRepository);
        var suppliers = new JdbcSupplierRepository(database::openConnection);
        batches = new JdbcBatchRepository(database::openConnection);
        movements = new JdbcInventoryMovementRepository(database::openConnection);
        sales = new JdbcSaleRepository(database::openConnection);
        saleLines = new JdbcSaleLineRepository(database::openConnection);
        returns = new JdbcSalesReturnRepository(database::openConnection);
        returnLines = new JdbcSalesReturnLineRepository(database::openConnection);

        product = productService.create(productDraft(150));
        customer = new CustomerService(customerRepository).create(new CustomerDraft(
                "Asha Shrestha", "9800000000", null, true));
        supplier = new SupplierService(suppliers).create(new SupplierDraft(
                "Kathmandu Medical Suppliers", null, null, null, true));
        purchaseService = new PurchaseService(new JdbcPurchaseEntryRepository(
                transactions, suppliers, batches,
                new JdbcPurchaseRepository(database::openConnection),
                new JdbcPurchaseLineRepository(database::openConnection), movements));
        saleService = new SaleService(new JdbcSaleEntryRepository(
                transactions, customerRepository,
                batches, productRepository, sales, saleLines, movements), batches);
        returnService = serviceWith(movements);
    }

    @Test
    void fullReturnSucceedsRestoresStockAndAppendsTypedMovement() {
        SaleReceipt sale = sell("FULL", 8, 5);
        SalesReturnSource source = source(sale);

        SalesReturn completed = record(source, 5);

        assertEquals(1, completed.returnNumber());
        assertEquals(750, completed.totalAmountPaisa());
        assertEquals(1, returns.count());
        assertEquals(1, returnLines.count());
        assertEquals(8, stock(source.lines().get(0).batchId()));
        assertEquals("SALE_RETURN", textScalar(
                "SELECT movement_type FROM inventory_movement WHERE reference_id = ?",
                completed.id()));
        assertEquals(completed.id().toString(), textScalar(
                "SELECT reference_id FROM inventory_movement WHERE movement_type = 'SALE_RETURN'",
                null));
    }

    @Test
    void partialReturnSucceedsAndLeavesRemainingReturnableQuantity() {
        SaleReceipt sale = sell("PARTIAL", 10, 6);

        record(source(sale), 2);

        SalesReturnSource refreshed = source(sale);
        assertEquals(2, refreshed.lines().get(0).previouslyReturnedBaseUnits());
        assertEquals(4, refreshed.lines().get(0).remainingReturnableBaseUnits());
        assertEquals(6, stock(refreshed.lines().get(0).batchId()));
    }

    @Test
    void multiplePartialReturnsUseLivePreviouslyReturnedQuantity() {
        SaleReceipt sale = sell("MULTIPLE", 10, 7);

        SalesReturn first = record(source(sale), 2);
        SalesReturn second = record(source(sale), 3);

        SalesReturnSource refreshed = source(sale);
        assertEquals(1, first.returnNumber());
        assertEquals(2, second.returnNumber());
        assertEquals(5, refreshed.lines().get(0).previouslyReturnedBaseUnits());
        assertEquals(2, refreshed.lines().get(0).remainingReturnableBaseUnits());
        assertEquals(5, returnedQuantity(refreshed.lines().get(0).originalSaleLineId()));
    }

    @Test
    void overReturnAfterPartialReturnIsRejectedWithoutPartialState() {
        SaleReceipt sale = sell("OVER", 10, 5);
        record(source(sale), 4);

        assertThrows(SalesReturnValidationException.class, () -> record(source(sale), 2));

        assertEquals(1, returns.count());
        assertEquals(1, returnLines.count());
        assertEquals(2, returns.nextCounterValue());
        assertEquals(9, stock(source(sale).lines().get(0).batchId()));
    }

    @Test
    void saleLineFromAnotherSaleIsRejected() {
        SaleReceipt first = sell("FIRST-SALE", 10, 2);
        SaleReceipt second = sellExistingBatch(first, 2);
        SalesReturnSource firstSource = source(first);
        SalesReturnSource secondSource = source(second);
        var foreignLine = secondSource.lines().get(0);

        SalesReturnDraft invalid = draft(first.sale().id(), new SalesReturnLineDraft(
                foreignLine.originalSaleLineId(), foreignLine.batchId(), 1,
                foreignLine.unitPricePaisa()));

        assertThrows(SalesReturnValidationException.class, () -> returnService.record(invalid));
        assertEquals(0, returns.count());
    }

    @Test
    void wrongBatchIsRejected() {
        SaleReceipt sale = sell("RIGHT-BATCH", 10, 2);
        SaleReceipt other = sell("OTHER-BATCH", 10, 1);
        var original = source(sale).lines().get(0);
        UUID wrongBatch = source(other).lines().get(0).batchId();

        SalesReturnDraft invalid = draft(sale.sale().id(), new SalesReturnLineDraft(
                original.originalSaleLineId(), wrongBatch, 1, original.unitPricePaisa()));

        assertThrows(SalesReturnValidationException.class, () -> returnService.record(invalid));
        assertEquals(0, returns.count());
    }

    @Test
    void zeroAndNegativeQuantitiesAreRejected() {
        SaleReceipt sale = sell("BAD-QUANTITY", 10, 2);
        var line = source(sale).lines().get(0);

        assertThrows(SalesReturnValidationException.class,
                () -> returnService.record(draft(sale.sale().id(), new SalesReturnLineDraft(
                        line.originalSaleLineId(), line.batchId(), 0, line.unitPricePaisa()))));
        assertThrows(SalesReturnValidationException.class,
                () -> returnService.record(draft(sale.sale().id(), new SalesReturnLineDraft(
                        line.originalSaleLineId(), line.batchId(), -1, line.unitPricePaisa()))));
        assertEquals(0, returns.count());
    }

    @Test
    void refundUsesOriginalSalePriceAfterCurrentProductPriceChanges() {
        SaleReceipt sale = sell("PRICE", 10, 2);
        var original = source(sale).lines().get(0);
        product = productService.update(product.id(), productDraft(999));

        SalesReturn completed = returnService.record(draft(sale.sale().id(),
                new SalesReturnLineDraft(original.originalSaleLineId(), original.batchId(), 2, 999)));

        assertEquals(300, completed.totalAmountPaisa());
        assertEquals(150, scalar("SELECT unit_price_paisa FROM sales_return_line", null));
    }

    @Test
    void creditSaleCashRefundIsRejectedWithoutChangingLedgerOrStock() {
        SaleReceipt sale = sell("CREDIT-CASH", 10, 2,
                PaymentMethod.CREDIT, customer.id());
        SalesReturnSource source = source(sale);
        long stockBefore = stock(source.lines().get(0).batchId());

        SalesReturnValidationException exception = assertThrows(
                SalesReturnValidationException.class,
                () -> record(source, 1, PaymentMethod.CASH));

        assertEquals(SalesReturnValidator.UNPAID_SALE_REFUND_MESSAGE,
                exception.fieldErrors().get("refundMethod"));
        assertEquals(300, customerBalance());
        assertEquals(stockBefore, stock(source.lines().get(0).batchId()));
        assertEquals(0, returns.count());
    }

    @Test
    void creditSaleQrRefundIsRejectedWithoutChangingLedgerOrStock() {
        SaleReceipt sale = sell("CREDIT-QR", 10, 2,
                PaymentMethod.CREDIT, customer.id());
        SalesReturnSource source = source(sale);
        long stockBefore = stock(source.lines().get(0).batchId());

        SalesReturnValidationException exception = assertThrows(
                SalesReturnValidationException.class,
                () -> record(source, 1, PaymentMethod.QR));

        assertEquals(SalesReturnValidator.UNPAID_SALE_REFUND_MESSAGE,
                exception.fieldErrors().get("refundMethod"));
        assertEquals(300, customerBalance());
        assertEquals(stockBefore, stock(source.lines().get(0).batchId()));
        assertEquals(0, returns.count());
    }

    @Test
    void creditSaleCreditRefundSucceedsAndReducesUdharoBalance() {
        SaleReceipt sale = sell("CREDIT-CREDIT", 10, 2,
                PaymentMethod.CREDIT, customer.id());
        SalesReturnSource source = source(sale);

        SalesReturn completed = record(source, 1, PaymentMethod.CREDIT);

        assertEquals(PaymentMethod.CREDIT, completed.refundMethod());
        assertEquals(150, completed.totalAmountPaisa());
        assertEquals(150, customerBalance());
        assertEquals(9, stock(source.lines().get(0).batchId()));
        assertEquals(1, returns.count());
    }

    @Test
    void cashSalesWithCustomerAcceptCashQrAndCreditRefundMethods() {
        int index = 0;
        for (PaymentMethod refundMethod : PaymentMethod.values()) {
            SaleReceipt sale = sell("CASH-REFUND-" + index++, 4, 1,
                    PaymentMethod.CASH, customer.id());

            SalesReturn completed = record(source(sale), 1, refundMethod);

            assertEquals(refundMethod, completed.refundMethod());
        }

        assertEquals(3, returns.count());
        assertEquals(-150, customerBalance());
    }

    @Test
    void customerlessSaleCannotBeRefundedToCredit() {
        SaleReceipt sale = sell("NO-CUSTOMER-CREDIT", 10, 2);
        var original = source(sale).lines().get(0);
        SalesReturnDraft creditRefund = new SalesReturnDraft(sale.sale().id(), DATE,
                SalesReturnReason.CUSTOMER_RETURN, PaymentMethod.CREDIT, null,
                List.of(new SalesReturnLineDraft(original.originalSaleLineId(),
                        original.batchId(), 1, original.unitPricePaisa())), null);

        SalesReturnValidationException exception = assertThrows(
                SalesReturnValidationException.class,
                () -> returnService.record(creditRefund));

        assertEquals("Credit refund requires a customer account.",
                exception.fieldErrors().get("refundMethod"));
        assertEquals(0, returns.count());
    }

    @Test
    void originalSaleAndLineRemainByteForByteUnchanged() {
        SaleReceipt sale = sell("IMMUTABLE", 10, 3);
        List<String> before = originalRows(sale.sale().id());

        record(source(sale), 2);

        assertEquals(before, originalRows(sale.sale().id()));
    }

    @Test
    void forcedMovementFailureRollsBackRowsCounterAndStock() {
        SaleReceipt sale = sell("ROLLBACK", 10, 3);
        SalesReturnSource source = source(sale);
        long beforeStock = stock(source.lines().get(0).batchId());
        InventoryMovementRepository failingMovements = new InventoryMovementRepository() {
            @Override
            public void insert(TransactionContext transaction, InventoryMovement movement) {
                throw new DataAccessException("Forced movement failure.");
            }

            @Override
            public long count() {
                return 0;
            }
        };
        SalesReturnService failingService = serviceWith(failingMovements);

        assertThrows(DataAccessException.class, () -> failingService.record(
                draftFor(source, 1)));

        assertEquals(0, returns.count());
        assertEquals(0, returnLines.count());
        assertEquals(1, returns.nextCounterValue());
        assertEquals(beforeStock, stock(source.lines().get(0).batchId()));
        assertEquals(0, scalar(
                "SELECT COUNT(*) FROM inventory_movement WHERE movement_type = 'SALE_RETURN'", null));
    }

    private SalesReturnService serviceWith(InventoryMovementRepository movementRepository) {
        return new SalesReturnService(new JdbcSalesReturnEntryRepository(
                transactions, sales, saleLines, batches, productRepository,
                returns, returnLines, movementRepository));
    }

    private SaleReceipt sell(String batchNumber, int received, int sold) {
        return sell(batchNumber, received, sold, PaymentMethod.CASH, null);
    }

    private SaleReceipt sell(
            String batchNumber,
            int received,
            int sold,
            PaymentMethod paymentMethod,
            UUID customerId
    ) {
        purchaseService.record(new PurchaseDraft(
                supplier.id(), DATE.minusDays(1), "PURCHASE-" + batchNumber,
                com.nepalpharmacy.purchasing.PurchasePaymentMethod.CASH,
                List.of(new PurchaseLineDraft(product.id(), batchNumber,
                        DATE.plusYears(1), null, received, 100)), null));
        BatchStock stock = batches.findAvailableByProduct(product.id(), DATE).stream()
                .filter(item -> item.batch().batchNumber().equals(batchNumber))
                .findFirst().orElseThrow();
        return saleService.record(new SaleDraft(customerId, DATE, paymentMethod,
                List.of(new SaleLineDraft(stock.batch().id(), sold, product.salePricePaisa())), null));
    }

    private SaleReceipt sellExistingBatch(SaleReceipt original, int sold) {
        UUID batchId = source(original).lines().get(0).batchId();
        return saleService.record(new SaleDraft(null, DATE, PaymentMethod.CASH,
                List.of(new SaleLineDraft(batchId, sold, product.salePricePaisa())), null));
    }

    private SalesReturnSource source(SaleReceipt sale) {
        return returnService.findSourceByInvoiceNumber(sale.sale().invoiceNumber()).orElseThrow();
    }

    private SalesReturn record(SalesReturnSource source, int quantity) {
        return returnService.record(draftFor(source, quantity));
    }

    private SalesReturn record(
            SalesReturnSource source, int quantity, PaymentMethod refundMethod) {
        var line = source.lines().get(0);
        return returnService.record(new SalesReturnDraft(
                source.sale().id(), DATE, SalesReturnReason.CUSTOMER_RETURN,
                refundMethod, "sealed package",
                List.of(new SalesReturnLineDraft(line.originalSaleLineId(), line.batchId(),
                        quantity, line.unitPricePaisa())), null));
    }

    private SalesReturnDraft draftFor(SalesReturnSource source, int quantity) {
        var line = source.lines().get(0);
        return draft(source.sale().id(), new SalesReturnLineDraft(
                line.originalSaleLineId(), line.batchId(), quantity, line.unitPricePaisa()));
    }

    private static SalesReturnDraft draft(UUID saleId, SalesReturnLineDraft line) {
        return new SalesReturnDraft(saleId, DATE, SalesReturnReason.CUSTOMER_RETURN,
                PaymentMethod.CASH, "  sealed package  ", List.of(line), null);
    }

    private ProductDraft productDraft(long salePrice) {
        return new ProductDraft("Paracetamol 500mg", "Paracetamol", "Nepal Pharma",
                ProductCategory.TABLET, UnitOfSale.TABLET, 10,
                100, salePrice, 1000L, 0, 10, true);
    }

    private long returnedQuantity(UUID originalLineId) {
        return transactions.inTransaction(transaction ->
                returnLines.returnedQuantityForOriginalLine(transaction, originalLineId));
    }

    private long stock(UUID batchId) {
        return scalar("SELECT quantity_base_units FROM batch_stock WHERE batch_id = ?", batchId);
    }

    private long customerBalance() {
        return customerAccounts.findDetail(customer.id()).orElseThrow().summary().balancePaisa();
    }

    private long scalar(String sql, UUID id) {
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement(sql)) {
            if (id != null) {
                statement.setString(1, id.toString());
            }
            try (var results = statement.executeQuery()) {
                results.next();
                return results.getLong(1);
            }
        } catch (java.sql.SQLException exception) {
            throw new AssertionError(exception);
        }
    }

    private String textScalar(String sql, UUID id) {
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement(sql)) {
            if (id != null) {
                statement.setString(1, id.toString());
            }
            try (var results = statement.executeQuery()) {
                assertTrue(results.next());
                return results.getString(1);
            }
        } catch (java.sql.SQLException exception) {
            throw new AssertionError(exception);
        }
    }

    private List<String> originalRows(UUID saleId) {
        List<String> rows = new ArrayList<>();
        try (var connection = database.openConnection()) {
            try (var statement = connection.prepareStatement("SELECT * FROM sale WHERE id = ?")) {
                statement.setString(1, saleId.toString());
                try (var results = statement.executeQuery()) {
                    var metadata = results.getMetaData();
                    while (results.next()) {
                        for (int index = 1; index <= metadata.getColumnCount(); index++) {
                            rows.add(metadata.getColumnName(index) + "=" + results.getString(index));
                        }
                    }
                }
            }
            try (var statement = connection.prepareStatement(
                    "SELECT * FROM sale_line WHERE sale_id = ? ORDER BY id")) {
                statement.setString(1, saleId.toString());
                try (var results = statement.executeQuery()) {
                    var metadata = results.getMetaData();
                    while (results.next()) {
                        for (int index = 1; index <= metadata.getColumnCount(); index++) {
                            rows.add(metadata.getColumnName(index) + "=" + results.getString(index));
                        }
                    }
                }
            }
            return rows;
        } catch (java.sql.SQLException exception) {
            throw new AssertionError(exception);
        }
    }
}
