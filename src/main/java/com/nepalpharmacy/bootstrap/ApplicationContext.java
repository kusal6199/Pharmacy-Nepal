package com.nepalpharmacy.bootstrap;

import com.nepalpharmacy.inventory.BatchRepository;
import com.nepalpharmacy.inventory.InventoryMovementRepository;
import com.nepalpharmacy.inventory.infrastructure.JdbcBatchRepository;
import com.nepalpharmacy.inventory.infrastructure.JdbcInventoryMovementRepository;
import com.nepalpharmacy.party.SupplierRepository;
import com.nepalpharmacy.party.SupplierService;
import com.nepalpharmacy.party.CustomerRepository;
import com.nepalpharmacy.party.CustomerService;
import com.nepalpharmacy.party.infrastructure.JdbcCustomerRepository;
import com.nepalpharmacy.party.infrastructure.JdbcSupplierRepository;
import com.nepalpharmacy.product.ProductRepository;
import com.nepalpharmacy.product.ProductService;
import com.nepalpharmacy.product.infrastructure.JdbcProductRepository;
import com.nepalpharmacy.purchasing.PurchaseEntryRepository;
import com.nepalpharmacy.purchasing.PurchaseLineRepository;
import com.nepalpharmacy.purchasing.PurchaseRepository;
import com.nepalpharmacy.purchasing.PurchaseService;
import com.nepalpharmacy.purchasing.infrastructure.JdbcPurchaseEntryRepository;
import com.nepalpharmacy.purchasing.infrastructure.JdbcPurchaseLineRepository;
import com.nepalpharmacy.purchasing.infrastructure.JdbcPurchaseRepository;
import com.nepalpharmacy.shared.infrastructure.ConnectionProvider;
import com.nepalpharmacy.shared.infrastructure.JdbcTransactionRunner;
import com.nepalpharmacy.shared.persistence.TransactionRunner;
import com.nepalpharmacy.sales.SaleEntryRepository;
import com.nepalpharmacy.sales.SaleLineRepository;
import com.nepalpharmacy.sales.SaleRepository;
import com.nepalpharmacy.sales.SaleService;
import com.nepalpharmacy.sales.infrastructure.JdbcSaleEntryRepository;
import com.nepalpharmacy.sales.infrastructure.JdbcSaleLineRepository;
import com.nepalpharmacy.sales.infrastructure.JdbcSaleRepository;

import java.util.Objects;

public final class ApplicationContext {

    private final ProductService productService;
    private final SupplierService supplierService;
    private final PurchaseService purchaseService;
    private final CustomerService customerService;
    private final SaleService saleService;

    public ApplicationContext(DatabaseBootstrap database) {
        Objects.requireNonNull(database, "database");
        ConnectionProvider connections = database::openConnection;
        TransactionRunner transactions = new JdbcTransactionRunner(connections);

        ProductRepository productRepository = new JdbcProductRepository(connections);
        SupplierRepository supplierRepository = new JdbcSupplierRepository(connections);
        CustomerRepository customerRepository = new JdbcCustomerRepository(connections);
        BatchRepository batchRepository = new JdbcBatchRepository(connections);
        InventoryMovementRepository movementRepository =
                new JdbcInventoryMovementRepository(connections);
        PurchaseRepository purchaseRepository = new JdbcPurchaseRepository(connections);
        PurchaseLineRepository purchaseLineRepository =
                new JdbcPurchaseLineRepository(connections);
        PurchaseEntryRepository purchaseEntryRepository = new JdbcPurchaseEntryRepository(
                transactions,
                supplierRepository,
                batchRepository,
                purchaseRepository,
                purchaseLineRepository,
                movementRepository);
        SaleRepository saleRepository = new JdbcSaleRepository(connections);
        SaleLineRepository saleLineRepository = new JdbcSaleLineRepository(connections);
        SaleEntryRepository saleEntryRepository = new JdbcSaleEntryRepository(
                transactions,
                customerRepository,
                batchRepository,
                productRepository,
                saleRepository,
                saleLineRepository,
                movementRepository);

        productService = new ProductService(productRepository);
        supplierService = new SupplierService(supplierRepository);
        purchaseService = new PurchaseService(purchaseEntryRepository);
        customerService = new CustomerService(customerRepository);
        saleService = new SaleService(saleEntryRepository, batchRepository);
    }

    public ProductService productService() {
        return productService;
    }

    public SupplierService supplierService() {
        return supplierService;
    }

    public PurchaseService purchaseService() {
        return purchaseService;
    }

    public CustomerService customerService() {
        return customerService;
    }

    public SaleService saleService() {
        return saleService;
    }
}
