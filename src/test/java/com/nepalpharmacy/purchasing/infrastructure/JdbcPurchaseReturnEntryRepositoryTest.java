package com.nepalpharmacy.purchasing.infrastructure;

import com.nepalpharmacy.bootstrap.DatabaseBootstrap;
import com.nepalpharmacy.inventory.BatchStock;
import com.nepalpharmacy.inventory.InventoryMovement;
import com.nepalpharmacy.inventory.InventoryMovementRepository;
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
import com.nepalpharmacy.purchasing.Purchase;
import com.nepalpharmacy.purchasing.PurchaseDraft;
import com.nepalpharmacy.purchasing.PurchaseLineDraft;
import com.nepalpharmacy.purchasing.PurchaseReturn;
import com.nepalpharmacy.purchasing.PurchaseReturnDraft;
import com.nepalpharmacy.purchasing.PurchaseReturnLineDraft;
import com.nepalpharmacy.purchasing.PurchaseReturnReason;
import com.nepalpharmacy.purchasing.PurchaseReturnService;
import com.nepalpharmacy.purchasing.PurchaseReturnSource;
import com.nepalpharmacy.purchasing.PurchaseReturnValidationException;
import com.nepalpharmacy.purchasing.PurchaseService;
import com.nepalpharmacy.sales.PaymentMethod;
import com.nepalpharmacy.sales.SaleDraft;
import com.nepalpharmacy.sales.SaleLineDraft;
import com.nepalpharmacy.sales.SaleService;
import com.nepalpharmacy.sales.infrastructure.JdbcSaleEntryRepository;
import com.nepalpharmacy.sales.infrastructure.JdbcSaleLineRepository;
import com.nepalpharmacy.sales.infrastructure.JdbcSaleRepository;
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

class JdbcPurchaseReturnEntryRepositoryTest {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 13);

    @TempDir
    Path temporaryDirectory;

    private DatabaseBootstrap database;
    private TransactionRunner transactions;
    private JdbcProductRepository productRepository;
    private ProductService productService;
    private Product product;
    private JdbcSupplierRepository supplierRepository;
    private SupplierService supplierService;
    private Supplier supplier;
    private PurchaseService purchaseService;
    private SaleService saleService;
    private PurchaseReturnService returnService;
    private JdbcBatchRepository batches;
    private JdbcPurchaseRepository purchases;
    private JdbcPurchaseLineRepository purchaseLines;
    private JdbcPurchaseReturnRepository returns;
    private JdbcPurchaseReturnLineRepository returnLines;
    private JdbcInventoryMovementRepository movements;

    @BeforeEach
    void setUp() {
        database = new DatabaseBootstrap(temporaryDirectory.resolve("pharmacy.db"));
        database.migrate();
        transactions = new JdbcTransactionRunner(database::openConnection);
        productRepository = new JdbcProductRepository(database::openConnection);
        productService = new ProductService(productRepository);
        supplierRepository = new JdbcSupplierRepository(database::openConnection);
        supplierService = new SupplierService(supplierRepository);
        batches = new JdbcBatchRepository(database::openConnection);
        movements = new JdbcInventoryMovementRepository(database::openConnection);
        purchases = new JdbcPurchaseRepository(database::openConnection);
        purchaseLines = new JdbcPurchaseLineRepository(database::openConnection);
        returns = new JdbcPurchaseReturnRepository(database::openConnection);
        returnLines = new JdbcPurchaseReturnLineRepository(database::openConnection);

        product = productService.create(productDraft(100));
        supplier = supplierService.create(new SupplierDraft(
                "Kathmandu Medical Suppliers", null, null, null, true));
        purchaseService = new PurchaseService(new JdbcPurchaseEntryRepository(
                transactions, supplierRepository, batches, purchases, purchaseLines, movements));
        saleService = new SaleService(new JdbcSaleEntryRepository(
                transactions, new JdbcCustomerRepository(database::openConnection),
                batches, productRepository,
                new JdbcSaleRepository(database::openConnection),
                new JdbcSaleLineRepository(database::openConnection), movements), batches);
        returnService = serviceWith(movements);
    }

    @Test
    void fullValidReturnSucceedsReducesStockAndAppendsTypedMovement() {
        Purchase purchase = purchase("FULL", 5, 125);
        PurchaseReturnSource source = source(purchase);

        PurchaseReturn completed = record(source, 5);

        assertEquals(1, completed.returnNumber());
        assertEquals(625, completed.totalAmountPaisa());
        assertEquals(1, returns.count());
        assertEquals(1, returnLines.count());
        assertEquals(0, stock(source.lines().get(0).batchId()));
        assertEquals("PURCHASE_RETURN", textScalar(
                "SELECT movement_type FROM inventory_movement WHERE reference_id = ?",
                completed.id()));
        assertEquals(completed.id().toString(), textScalar(
                "SELECT reference_id FROM inventory_movement WHERE movement_type = 'PURCHASE_RETURN'",
                null));
    }

    @Test
    void partialReturnSucceedsAndLeavesRemainingQuantities() {
        Purchase purchase = purchase("PARTIAL", 10, 100);

        record(source(purchase), 3);

        PurchaseReturnSource refreshed = source(purchase);
        assertEquals(3, refreshed.lines().get(0).previouslyReturnedBaseUnits());
        assertEquals(7, refreshed.lines().get(0).remainingReturnableBaseUnits());
        assertEquals(7, refreshed.lines().get(0).availableBatchQuantityBaseUnits());
    }

    @Test
    void multiplePartialReturnsUseLivePreviouslyReturnedQuantity() {
        Purchase purchase = purchase("MULTIPLE", 10, 100);

        PurchaseReturn first = record(source(purchase), 2);
        PurchaseReturn second = record(source(purchase), 3);

        PurchaseReturnSource refreshed = source(purchase);
        assertEquals(1, first.returnNumber());
        assertEquals(2, second.returnNumber());
        assertEquals(5, refreshed.lines().get(0).previouslyReturnedBaseUnits());
        assertEquals(5, refreshed.lines().get(0).remainingReturnableBaseUnits());
        assertEquals(5, returnedQuantity(refreshed.lines().get(0).originalPurchaseLineId()));
    }

    @Test
    void returnBeyondOriginallyPurchasedQuantityIsRejected() {
        Purchase purchase = purchase("OVER-ORIGINAL", 5, 100);
        record(source(purchase), 4);

        assertThrows(PurchaseReturnValidationException.class,
                () -> record(source(purchase), 2));

        assertEquals(1, returns.count());
        assertEquals(2, returns.nextCounterValue());
        assertEquals(1, stock(source(purchase).lines().get(0).batchId()));
    }

    @Test
    void returnBeyondCurrentStockIsRejectedAfterBatchWasSoldDown() {
        Purchase purchase = purchase("SOLD-DOWN", 100, 100);
        PurchaseReturnSource source = source(purchase);
        sell(source.lines().get(0).batchId(), 80);

        assertThrows(PurchaseReturnValidationException.class,
                () -> record(source(purchase), 30));

        assertEquals(0, returns.count());
        assertEquals(20, stock(source.lines().get(0).batchId()));
    }

    @Test
    void lineFromAnotherPurchaseIsRejected() {
        Purchase first = purchase("FIRST", 5, 100);
        Purchase second = purchase("SECOND", 5, 100);
        var foreignLine = source(second).lines().get(0);
        PurchaseReturnDraft invalid = draft(first.id(), first.supplierId(),
                new PurchaseReturnLineDraft(
                        foreignLine.originalPurchaseLineId(), foreignLine.batchId(), 1,
                        foreignLine.unitCostPaisa()));

        assertThrows(PurchaseReturnValidationException.class,
                () -> returnService.record(invalid));
        assertEquals(0, returns.count());
    }

    @Test
    void wrongBatchIsRejected() {
        Purchase first = purchase("RIGHT-BATCH", 5, 100);
        Purchase other = purchase("OTHER-BATCH", 5, 100);
        var original = source(first).lines().get(0);
        UUID wrongBatch = source(other).lines().get(0).batchId();
        PurchaseReturnDraft invalid = draft(first.id(), first.supplierId(),
                new PurchaseReturnLineDraft(
                        original.originalPurchaseLineId(), wrongBatch, 1,
                        original.unitCostPaisa()));

        assertThrows(PurchaseReturnValidationException.class,
                () -> returnService.record(invalid));
        assertEquals(0, returns.count());
    }

    @Test
    void supplierDifferentFromOriginalPurchaseIsRejected() {
        Purchase purchase = purchase("SUPPLIER", 5, 100);
        Supplier otherSupplier = supplierService.create(new SupplierDraft(
                "Pokhara Medical Suppliers", null, null, null, true));
        var line = source(purchase).lines().get(0);
        PurchaseReturnDraft invalid = draft(purchase.id(), otherSupplier.id(),
                new PurchaseReturnLineDraft(line.originalPurchaseLineId(), line.batchId(),
                        1, line.unitCostPaisa()));

        assertThrows(PurchaseReturnValidationException.class,
                () -> returnService.record(invalid));
        assertEquals(0, returns.count());
    }

    @Test
    void originalPurchaseCostIsUsedAfterCurrentProductCostChanges() {
        Purchase purchase = purchase("COST", 5, 125);
        var line = source(purchase).lines().get(0);
        product = productService.update(product.id(), productDraft(999));

        PurchaseReturn completed = returnService.record(draft(
                purchase.id(), purchase.supplierId(), new PurchaseReturnLineDraft(
                        line.originalPurchaseLineId(), line.batchId(), 2, 999)));

        assertEquals(250, completed.totalAmountPaisa());
        assertEquals(125, scalar("SELECT unit_cost_paisa FROM purchase_return_line", null));
    }

    @Test
    void originalPurchaseAndLineRemainByteForByteUnchanged() {
        Purchase purchase = purchase("IMMUTABLE", 8, 100);
        List<String> before = originalRows(purchase.id());

        record(source(purchase), 3);

        assertEquals(before, originalRows(purchase.id()));
    }

    @Test
    void forcedMovementFailureRollsBackRowsCounterAndStock() {
        Purchase purchase = purchase("ROLLBACK", 8, 100);
        PurchaseReturnSource source = source(purchase);
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
        PurchaseReturnService failingService = serviceWith(failingMovements);

        assertThrows(DataAccessException.class,
                () -> failingService.record(draftFor(source, 2)));

        assertEquals(0, returns.count());
        assertEquals(0, returnLines.count());
        assertEquals(1, returns.nextCounterValue());
        assertEquals(beforeStock, stock(source.lines().get(0).batchId()));
        assertEquals(0, scalar(
                "SELECT COUNT(*) FROM inventory_movement WHERE movement_type = 'PURCHASE_RETURN'",
                null));
    }

    private PurchaseReturnService serviceWith(InventoryMovementRepository movementRepository) {
        return new PurchaseReturnService(new JdbcPurchaseReturnEntryRepository(
                transactions, purchases, purchaseLines, supplierRepository, batches,
                productRepository, returns, returnLines, movementRepository));
    }

    private Purchase purchase(String batchNumber, int quantity, long unitCost) {
        return purchaseService.record(new PurchaseDraft(
                supplier.id(), DATE.minusDays(1), "INV-" + batchNumber,
                List.of(new PurchaseLineDraft(product.id(), batchNumber,
                        DATE.plusYears(1), null, quantity, unitCost)), null));
    }

    private void sell(UUID batchId, int quantity) {
        saleService.record(new SaleDraft(null, DATE, PaymentMethod.CASH,
                List.of(new SaleLineDraft(batchId, quantity, product.salePricePaisa())), null));
    }

    private PurchaseReturnSource source(Purchase purchase) {
        return returnService.findSource(purchase.id()).orElseThrow();
    }

    private PurchaseReturn record(PurchaseReturnSource source, int quantity) {
        return returnService.record(draftFor(source, quantity));
    }

    private PurchaseReturnDraft draftFor(PurchaseReturnSource source, int quantity) {
        var line = source.lines().get(0);
        return draft(source.purchase().id(), source.purchase().supplierId(),
                new PurchaseReturnLineDraft(line.originalPurchaseLineId(), line.batchId(),
                        quantity, line.unitCostPaisa()));
    }

    private static PurchaseReturnDraft draft(
            UUID purchaseId, UUID supplierId, PurchaseReturnLineDraft line) {
        return new PurchaseReturnDraft(purchaseId, supplierId, DATE,
                PurchaseReturnReason.DAMAGED, "  damaged carton  ", List.of(line), null);
    }

    private ProductDraft productDraft(long purchasePrice) {
        return new ProductDraft("Paracetamol 500mg", "Paracetamol", "Nepal Pharma",
                ProductCategory.TABLET, UnitOfSale.TABLET, 10,
                purchasePrice, 150, 180L, 0, 10, true);
    }

    private long returnedQuantity(UUID originalLineId) {
        return transactions.inTransaction(transaction ->
                returnLines.returnedQuantityForOriginalLine(transaction, originalLineId));
    }

    private long stock(UUID batchId) {
        return scalar("SELECT quantity_base_units FROM batch_stock WHERE batch_id = ?", batchId);
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

    private List<String> originalRows(UUID purchaseId) {
        List<String> rows = new ArrayList<>();
        try (var connection = database.openConnection()) {
            try (var statement = connection.prepareStatement(
                    "SELECT * FROM purchase WHERE id = ?")) {
                statement.setString(1, purchaseId.toString());
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
                    "SELECT * FROM purchase_line WHERE purchase_id = ? ORDER BY id")) {
                statement.setString(1, purchaseId.toString());
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
