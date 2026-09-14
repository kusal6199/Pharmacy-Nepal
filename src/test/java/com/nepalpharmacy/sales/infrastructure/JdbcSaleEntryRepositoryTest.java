package com.nepalpharmacy.sales.infrastructure;

import com.nepalpharmacy.bootstrap.DatabaseBootstrap;
import com.nepalpharmacy.inventory.BatchStock;
import com.nepalpharmacy.inventory.infrastructure.JdbcBatchRepository;
import com.nepalpharmacy.inventory.infrastructure.JdbcInventoryMovementRepository;
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
import com.nepalpharmacy.sales.SaleLine;
import com.nepalpharmacy.sales.SaleLineDraft;
import com.nepalpharmacy.sales.SaleLineRepository;
import com.nepalpharmacy.sales.SaleReceipt;
import com.nepalpharmacy.sales.SaleService;
import com.nepalpharmacy.sales.SaleValidationException;
import com.nepalpharmacy.shared.infrastructure.DataAccessException;
import com.nepalpharmacy.shared.infrastructure.JdbcTransactionRunner;
import com.nepalpharmacy.shared.persistence.TransactionContext;
import com.nepalpharmacy.shared.persistence.TransactionRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JdbcSaleEntryRepositoryTest {

    private static final LocalDate SALE_DATE = LocalDate.of(2026, 9, 13);

    @TempDir
    Path temporaryDirectory;

    private DatabaseBootstrap database;
    private TransactionRunner transactions;
    private JdbcProductRepository products;
    private JdbcCustomerRepository customers;
    private Product product;
    private Supplier supplier;
    private PurchaseService purchaseService;
    private SaleService saleService;
    private JdbcBatchRepository batches;
    private JdbcSaleRepository sales;
    private JdbcSaleLineRepository saleLines;
    private JdbcInventoryMovementRepository movements;

    @BeforeEach
    void setUp() {
        database = new DatabaseBootstrap(temporaryDirectory.resolve("pharmacy.db"));
        database.migrate();
        transactions = new JdbcTransactionRunner(database::openConnection);
        products = new JdbcProductRepository(database::openConnection);
        var suppliers = new JdbcSupplierRepository(database::openConnection);
        customers = new JdbcCustomerRepository(database::openConnection);
        batches = new JdbcBatchRepository(database::openConnection);
        movements = new JdbcInventoryMovementRepository(database::openConnection);

        product = new ProductService(products).create(new ProductDraft(
                "Paracetamol 500mg", "Paracetamol", "Nepal Pharma",
                ProductCategory.TABLET, UnitOfSale.TABLET, 10,
                100, 150, 180L, 0, 10, true));
        supplier = new SupplierService(suppliers).create(new SupplierDraft(
                "Kathmandu Medical Suppliers", null, null, null, true));

        purchaseService = new PurchaseService(new JdbcPurchaseEntryRepository(
                transactions,
                suppliers,
                batches,
                new JdbcPurchaseRepository(database::openConnection),
                new JdbcPurchaseLineRepository(database::openConnection),
                movements));
        sales = new JdbcSaleRepository(database::openConnection);
        saleLines = new JdbcSaleLineRepository(database::openConnection);
        saleService = new SaleService(new JdbcSaleEntryRepository(
                transactions,
                customers,
                batches,
                products,
                sales,
                saleLines,
                movements), batches);
    }

    @Test
    void successfulSaleReducesAvailableStockThroughSaleMovement() {
        BatchStock stock = receive("B-001", SALE_DATE.plusYears(1), 10);

        SaleReceipt receipt = saleService.record(sale(
                new SaleLineDraft(stock.batch().id(), 4, product.salePricePaisa())));

        assertEquals(1, receipt.sale().invoiceNumber());
        assertEquals(600, receipt.sale().totalAmountPaisa());
        assertEquals(1, sales.count());
        assertEquals(1, saleLines.count());
        assertEquals(6, batches.findAvailableByProduct(product.id(), SALE_DATE)
                .get(0).quantityBaseUnits());
        assertEquals(1, countSaleMovements());
        assertEquals(4, saleMovementQuantity());
    }

    @Test
    void oversellRollsBackWholeSaleAndDoesNotConsumeInvoiceNumber() {
        BatchStock first = receive("FIRST", SALE_DATE.plusYears(1), 5);
        BatchStock second = receive("SECOND", SALE_DATE.plusYears(2), 2);
        SaleDraft oversell = new SaleDraft(
                null,
                SALE_DATE,
                PaymentMethod.CASH,
                List.of(
                        new SaleLineDraft(first.batch().id(), 1, product.salePricePaisa()),
                        new SaleLineDraft(second.batch().id(), 3, product.salePricePaisa())),
                null);

        assertThrows(SaleValidationException.class, () -> saleService.record(oversell));

        assertEquals(0, sales.count());
        assertEquals(0, saleLines.count());
        assertEquals(0, countSaleMovements());
        assertEquals(1, sales.nextCounterValue());
        assertEquals(5, currentQuantity(first.batch().id()));
        assertEquals(2, currentQuantity(second.batch().id()));
    }

    @Test
    void fefoSuggestionReturnsSoonestAvailableBatchFirst() {
        receive("LATER", SALE_DATE.plusYears(2), 20);
        receive("SOONER", SALE_DATE.plusMonths(6), 7);

        List<BatchStock> available = saleService.findAvailableBatches(product.id(), SALE_DATE);

        assertEquals(List.of("SOONER", "LATER"),
                available.stream().map(stock -> stock.batch().batchNumber()).toList());
    }

    @Test
    void expiredBatchIsExcludedEvenWhenItHasStock() {
        LocalDate oldPurchaseDate = LocalDate.of(2024, 1, 1);
        LocalDate expired = LocalDate.of(2025, 12, 31);
        receive("EXPIRED", oldPurchaseDate, expired, 8);
        receive("VALID", SALE_DATE.plusYears(1), 5);

        List<BatchStock> available = saleService.findAvailableBatches(product.id(), SALE_DATE);

        assertEquals(List.of("VALID"),
                available.stream().map(stock -> stock.batch().batchNumber()).toList());
    }

    @Test
    void committedInvoicesAreStrictlyIncreasing() {
        BatchStock stock = receive("SEQUENCE", SALE_DATE.plusYears(1), 5);

        SaleReceipt first = saleService.record(sale(
                new SaleLineDraft(stock.batch().id(), 1, product.salePricePaisa())));
        SaleReceipt second = saleService.record(sale(
                new SaleLineDraft(stock.batch().id(), 1, product.salePricePaisa())));

        assertEquals(1, first.sale().invoiceNumber());
        assertEquals(2, second.sale().invoiceNumber());
        assertEquals(3, sales.nextCounterValue());
    }

    @Test
    void persistenceUsesCurrentProductSalePriceInsteadOfUiDraftPrice() {
        BatchStock stock = receive("PRICE-SNAPSHOT", SALE_DATE.plusYears(1), 5);

        SaleReceipt receipt = saleService.record(sale(
                new SaleLineDraft(stock.batch().id(), 2, 1)));

        assertEquals(product.salePricePaisa(), receipt.lines().get(0).unitSalePricePaisa());
        assertEquals(product.salePricePaisa() * 2, receipt.sale().totalAmountPaisa());
        assertEquals(product.salePricePaisa(), scalar(
                "SELECT unit_sale_price_paisa FROM sale_line", null));
    }

    @Test
    void failureAfterInvoiceAllocationRollsBackCounterHeaderAndStock() {
        BatchStock stock = receive("ROLLBACK", SALE_DATE.plusYears(1), 5);
        SaleLineRepository failingLines = new SaleLineRepository() {
            @Override
            public java.util.Optional<SaleLine> findById(UUID id) {
                return java.util.Optional.empty();
            }

            @Override
            public List<SaleLine> findBySaleId(UUID saleId) {
                return List.of();
            }

            @Override
            public void insert(TransactionContext transaction, SaleLine line) {
                throw new DataAccessException("Forced line failure.");
            }

            @Override
            public java.util.Optional<SaleLine> findById(
                    TransactionContext transaction, UUID id) {
                return java.util.Optional.empty();
            }

            @Override
            public List<SaleLine> findBySaleId(
                    TransactionContext transaction, UUID saleId) {
                return List.of();
            }

            @Override
            public long count() {
                return 0;
            }
        };
        SaleService failingService = new SaleService(new JdbcSaleEntryRepository(
                transactions,
                customers,
                batches,
                products,
                sales,
                failingLines,
                movements), batches);

        assertThrows(DataAccessException.class, () -> failingService.record(sale(
                new SaleLineDraft(stock.batch().id(), 1, product.salePricePaisa()))));

        assertEquals(0, sales.count());
        assertEquals(1, sales.nextCounterValue());
        assertEquals(0, countSaleMovements());
        assertEquals(5, currentQuantity(stock.batch().id()));
    }

    private BatchStock receive(String batch, LocalDate expiry, int quantity) {
        return receive(batch, SALE_DATE.minusMonths(1), expiry, quantity);
    }

    private BatchStock receive(
            String batch, LocalDate purchaseDate, LocalDate expiry, int quantity) {
        purchaseService.record(new PurchaseDraft(
                supplier.id(),
                purchaseDate,
                "PURCHASE-" + batch,
                com.nepalpharmacy.purchasing.PurchasePaymentMethod.CASH,
                List.of(new PurchaseLineDraft(
                        product.id(), batch, expiry, null, quantity, 100)),
                null));
        return batches.findAvailableByProduct(product.id(), purchaseDate).stream()
                .filter(stock -> stock.batch().batchNumber().equals(batch))
                .findFirst()
                .orElseThrow();
    }

    private SaleDraft sale(SaleLineDraft line) {
        return new SaleDraft(null, SALE_DATE, PaymentMethod.CASH, List.of(line), null);
    }

    private long countSaleMovements() {
        return scalar("SELECT COUNT(*) FROM inventory_movement WHERE movement_type = 'SALE'", null);
    }

    private long saleMovementQuantity() {
        return scalar("SELECT quantity_base_units FROM inventory_movement WHERE movement_type = 'SALE'", null);
    }

    private long currentQuantity(java.util.UUID batchId) {
        return scalar("SELECT quantity_base_units FROM batch_stock WHERE batch_id = ?", batchId);
    }

    private long scalar(String sql, java.util.UUID batchId) {
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement(sql)) {
            if (batchId != null) {
                statement.setString(1, batchId.toString());
            }
            try (var results = statement.executeQuery()) {
                results.next();
                return results.getLong(1);
            }
        } catch (java.sql.SQLException exception) {
            throw new AssertionError(exception);
        }
    }
}
