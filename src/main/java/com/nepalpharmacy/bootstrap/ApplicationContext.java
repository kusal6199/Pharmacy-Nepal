package com.nepalpharmacy.bootstrap;

import com.nepalpharmacy.credit.CustomerAccountEntryRepository;
import com.nepalpharmacy.credit.CustomerAccountRepository;
import com.nepalpharmacy.credit.CustomerAccountService;
import com.nepalpharmacy.credit.SupplierAccountEntryRepository;
import com.nepalpharmacy.credit.SupplierAccountRepository;
import com.nepalpharmacy.credit.SupplierAccountService;
import com.nepalpharmacy.credit.infrastructure.JdbcCustomerAccountEntryRepository;
import com.nepalpharmacy.credit.infrastructure.JdbcCustomerAccountRepository;
import com.nepalpharmacy.credit.infrastructure.JdbcSupplierAccountEntryRepository;
import com.nepalpharmacy.credit.infrastructure.JdbcSupplierAccountRepository;
import com.nepalpharmacy.inventory.BatchRepository;
import com.nepalpharmacy.inventory.InventoryAlertRepository;
import com.nepalpharmacy.inventory.InventoryAlertService;
import com.nepalpharmacy.inventory.InventoryMovementRepository;
import com.nepalpharmacy.inventory.infrastructure.JdbcBatchRepository;
import com.nepalpharmacy.inventory.infrastructure.JdbcInventoryAlertRepository;
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
import com.nepalpharmacy.purchasing.PurchaseHistoryRepository;
import com.nepalpharmacy.purchasing.PurchaseHistoryService;
import com.nepalpharmacy.purchasing.PurchaseLineRepository;
import com.nepalpharmacy.purchasing.PurchaseRepository;
import com.nepalpharmacy.purchasing.PurchaseReturnEntryRepository;
import com.nepalpharmacy.purchasing.PurchaseReturnLineRepository;
import com.nepalpharmacy.purchasing.PurchaseReturnRepository;
import com.nepalpharmacy.purchasing.PurchaseReturnService;
import com.nepalpharmacy.purchasing.PurchaseService;
import com.nepalpharmacy.purchasing.infrastructure.JdbcPurchaseEntryRepository;
import com.nepalpharmacy.purchasing.infrastructure.JdbcPurchaseHistoryRepository;
import com.nepalpharmacy.purchasing.infrastructure.JdbcPurchaseLineRepository;
import com.nepalpharmacy.purchasing.infrastructure.JdbcPurchaseRepository;
import com.nepalpharmacy.purchasing.infrastructure.JdbcPurchaseReturnEntryRepository;
import com.nepalpharmacy.purchasing.infrastructure.JdbcPurchaseReturnLineRepository;
import com.nepalpharmacy.purchasing.infrastructure.JdbcPurchaseReturnRepository;
import com.nepalpharmacy.shared.infrastructure.ConnectionProvider;
import com.nepalpharmacy.shared.infrastructure.JdbcTransactionRunner;
import com.nepalpharmacy.shared.persistence.TransactionRunner;
import com.nepalpharmacy.sales.SaleEntryRepository;
import com.nepalpharmacy.sales.SaleHistoryRepository;
import com.nepalpharmacy.sales.SaleHistoryService;
import com.nepalpharmacy.sales.SaleLineRepository;
import com.nepalpharmacy.sales.SaleRepository;
import com.nepalpharmacy.sales.SaleService;
import com.nepalpharmacy.sales.SalesReturnEntryRepository;
import com.nepalpharmacy.sales.SalesReturnLineRepository;
import com.nepalpharmacy.sales.SalesReturnRepository;
import com.nepalpharmacy.sales.SalesReturnService;
import com.nepalpharmacy.sales.infrastructure.JdbcSaleEntryRepository;
import com.nepalpharmacy.sales.infrastructure.JdbcSaleHistoryRepository;
import com.nepalpharmacy.sales.infrastructure.JdbcSaleLineRepository;
import com.nepalpharmacy.sales.infrastructure.JdbcSaleRepository;
import com.nepalpharmacy.sales.infrastructure.JdbcSalesReturnEntryRepository;
import com.nepalpharmacy.sales.infrastructure.JdbcSalesReturnLineRepository;
import com.nepalpharmacy.sales.infrastructure.JdbcSalesReturnRepository;

import java.util.Objects;

public final class ApplicationContext {

    private final ProductService productService;
    private final SupplierService supplierService;
    private final PurchaseService purchaseService;
    private final CustomerService customerService;
    private final SaleService saleService;
    private final SalesReturnService salesReturnService;
    private final PurchaseReturnService purchaseReturnService;
    private final SaleHistoryService saleHistoryService;
    private final PurchaseHistoryService purchaseHistoryService;
    private final InventoryAlertService inventoryAlertService;
    private final CustomerAccountService customerAccountService;
    private final SupplierAccountService supplierAccountService;

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
        InventoryAlertRepository inventoryAlertRepository =
                new JdbcInventoryAlertRepository(connections);
        PurchaseRepository purchaseRepository = new JdbcPurchaseRepository(connections);
        PurchaseLineRepository purchaseLineRepository =
                new JdbcPurchaseLineRepository(connections);
        PurchaseHistoryRepository purchaseHistoryRepository =
                new JdbcPurchaseHistoryRepository(connections);
        PurchaseEntryRepository purchaseEntryRepository = new JdbcPurchaseEntryRepository(
                transactions,
                supplierRepository,
                batchRepository,
                purchaseRepository,
                purchaseLineRepository,
                movementRepository);
        SaleRepository saleRepository = new JdbcSaleRepository(connections);
        SaleLineRepository saleLineRepository = new JdbcSaleLineRepository(connections);
        SaleHistoryRepository saleHistoryRepository =
                new JdbcSaleHistoryRepository(connections);
        SaleEntryRepository saleEntryRepository = new JdbcSaleEntryRepository(
                transactions,
                customerRepository,
                batchRepository,
                productRepository,
                saleRepository,
                saleLineRepository,
                movementRepository);
        SalesReturnRepository salesReturnRepository =
                new JdbcSalesReturnRepository(connections);
        SalesReturnLineRepository salesReturnLineRepository =
                new JdbcSalesReturnLineRepository(connections);
        SalesReturnEntryRepository salesReturnEntryRepository =
                new JdbcSalesReturnEntryRepository(
                        transactions,
                        saleRepository,
                        saleLineRepository,
                        batchRepository,
                        productRepository,
                        salesReturnRepository,
                        salesReturnLineRepository,
                        movementRepository);
        PurchaseReturnRepository purchaseReturnRepository =
                new JdbcPurchaseReturnRepository(connections);
        PurchaseReturnLineRepository purchaseReturnLineRepository =
                new JdbcPurchaseReturnLineRepository(connections);
        PurchaseReturnEntryRepository purchaseReturnEntryRepository =
                new JdbcPurchaseReturnEntryRepository(
                        transactions,
                        purchaseRepository,
                        purchaseLineRepository,
                        supplierRepository,
                        batchRepository,
                        productRepository,
                        purchaseReturnRepository,
                        purchaseReturnLineRepository,
                        movementRepository);
        CustomerAccountRepository customerAccountRepository =
                new JdbcCustomerAccountRepository(connections);
        CustomerAccountEntryRepository customerAccountEntryRepository =
                new JdbcCustomerAccountEntryRepository();
        SupplierAccountRepository supplierAccountRepository =
                new JdbcSupplierAccountRepository(connections);
        SupplierAccountEntryRepository supplierAccountEntryRepository =
                new JdbcSupplierAccountEntryRepository();

        productService = new ProductService(productRepository);
        supplierService = new SupplierService(supplierRepository);
        purchaseService = new PurchaseService(purchaseEntryRepository);
        customerService = new CustomerService(customerRepository);
        saleService = new SaleService(saleEntryRepository, batchRepository);
        salesReturnService = new SalesReturnService(salesReturnEntryRepository);
        purchaseReturnService = new PurchaseReturnService(purchaseReturnEntryRepository);
        saleHistoryService = new SaleHistoryService(saleHistoryRepository);
        purchaseHistoryService = new PurchaseHistoryService(purchaseHistoryRepository);
        inventoryAlertService = new InventoryAlertService(inventoryAlertRepository);
        customerAccountService = new CustomerAccountService(
                customerAccountRepository, customerAccountEntryRepository,
                customerRepository, transactions);
        supplierAccountService = new SupplierAccountService(
                supplierAccountRepository, supplierAccountEntryRepository,
                supplierRepository, transactions);
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

    public SalesReturnService salesReturnService() {
        return salesReturnService;
    }

    public PurchaseReturnService purchaseReturnService() {
        return purchaseReturnService;
    }

    public SaleHistoryService saleHistoryService() {
        return saleHistoryService;
    }

    public PurchaseHistoryService purchaseHistoryService() {
        return purchaseHistoryService;
    }

    public InventoryAlertService inventoryAlertService() {
        return inventoryAlertService;
    }

    public CustomerAccountService customerAccountService() {
        return customerAccountService;
    }

    public SupplierAccountService supplierAccountService() {
        return supplierAccountService;
    }
}
