package com.nepalpharmacy.purchasing.infrastructure;

import com.nepalpharmacy.bootstrap.DatabaseBootstrap;
import com.nepalpharmacy.inventory.BatchStock;
import com.nepalpharmacy.inventory.infrastructure.JdbcBatchRepository;
import com.nepalpharmacy.inventory.infrastructure.JdbcInventoryMovementRepository;
import com.nepalpharmacy.party.Supplier;
import com.nepalpharmacy.party.SupplierDraft;
import com.nepalpharmacy.party.SupplierService;
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
import com.nepalpharmacy.shared.infrastructure.DataAccessException;
import com.nepalpharmacy.shared.infrastructure.JdbcTransactionRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JdbcPurchaseEntryRepositoryTest {

    @TempDir
    Path temporaryDirectory;

    private DatabaseBootstrap database;
    private PurchaseService service;
    private JdbcBatchRepository batches;
    private JdbcPurchaseRepository purchases;
    private JdbcPurchaseLineRepository lines;
    private JdbcInventoryMovementRepository movements;
    private Supplier supplier;
    private Product product;

    @BeforeEach
    void setUp() {
        database = new DatabaseBootstrap(temporaryDirectory.resolve("pharmacy.db"));
        database.migrate();

        JdbcSupplierRepository supplierRepository = new JdbcSupplierRepository(database::openConnection);
        supplier = new SupplierService(supplierRepository).create(
                new SupplierDraft("Kathmandu Medical Suppliers", "01-5555555", "Kathmandu", "123456789", true));

        product = new ProductService(new JdbcProductRepository(database::openConnection)).create(
                new ProductDraft(
                        "Paracetamol 500mg", "Paracetamol", "Nepal Pharma",
                        ProductCategory.TABLET, UnitOfSale.TABLET, 10,
                        100, 150, 180L, 0, 10, true));

        batches = new JdbcBatchRepository(database::openConnection);
        purchases = new JdbcPurchaseRepository(database::openConnection);
        lines = new JdbcPurchaseLineRepository(database::openConnection);
        movements = new JdbcInventoryMovementRepository(database::openConnection);
        service = new PurchaseService(new JdbcPurchaseEntryRepository(
                new JdbcTransactionRunner(database::openConnection),
                supplierRepository,
                batches,
                purchases,
                lines,
                movements));
    }

    @Test
    void reusesMatchingBatchAndAppendsAStockMovement() {
        LocalDate purchaseDate = LocalDate.of(2026, 9, 13);
        LocalDate expiry = LocalDate.of(2027, 12, 31);

        service.record(draft("INV-1", purchaseDate, line(product.id(), "B-001", expiry, 10, 100)));
        service.record(draft("INV-2", purchaseDate.plusDays(1), line(product.id(), "b-001", expiry, 5, 110)));

        assertEquals(1, batches.count());
        assertEquals(2, purchases.count());
        assertEquals(2, lines.count());
        assertEquals(2, movements.count());
        assertEquals(15, batches.findAvailableByProduct(product.id(), purchaseDate).get(0).quantityBaseUnits());
    }

    @Test
    void rollsBackHeaderLinesBatchesAndMovementsWhenAnyLineFails() {
        LocalDate purchaseDate = LocalDate.of(2026, 9, 13);
        PurchaseLineDraft valid = line(
                product.id(), "VALID", purchaseDate.plusYears(1), 4, 100);
        PurchaseLineDraft missingProduct = line(
                UUID.randomUUID(), "INVALID", purchaseDate.plusYears(1), 2, 100);

        assertThrows(DataAccessException.class, () -> service.record(
                new PurchaseDraft(
                        supplier.id(), purchaseDate, "INV-ROLLBACK",
                        List.of(valid, missingProduct), null)));

        assertEquals(0, purchases.count());
        assertEquals(0, lines.count());
        assertEquals(0, movements.count());
        assertEquals(0, batches.count());
    }

    @Test
    void returnsAvailableBatchesInFefoOrderWithCurrentQuantities() {
        LocalDate purchaseDate = LocalDate.of(2026, 9, 13);
        LocalDate later = purchaseDate.plusYears(2);
        LocalDate sooner = purchaseDate.plusMonths(8);

        service.record(new PurchaseDraft(
                supplier.id(), purchaseDate, "INV-FEFO",
                List.of(
                        line(product.id(), "LATER", later, 20, 100),
                        line(product.id(), "SOONER", sooner, 7, 100)),
                null));

        List<BatchStock> available = batches.findAvailableByProduct(product.id(), purchaseDate);

        assertEquals(List.of("SOONER", "LATER"),
                available.stream().map(stock -> stock.batch().batchNumber()).toList());
        assertEquals(List.of(7L, 20L),
                available.stream().map(BatchStock::quantityBaseUnits).toList());
    }

    private PurchaseDraft draft(
            String invoice, LocalDate date, PurchaseLineDraft line) {
        return new PurchaseDraft(supplier.id(), date, invoice, List.of(line), null);
    }

    private static PurchaseLineDraft line(
            UUID productId, String batch, LocalDate expiry, int quantity, long unitPrice) {
        return new PurchaseLineDraft(productId, batch, expiry, null, quantity, unitPrice);
    }
}
