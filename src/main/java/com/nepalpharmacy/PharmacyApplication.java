package com.nepalpharmacy;

import com.nepalpharmacy.bootstrap.ApplicationContext;
import com.nepalpharmacy.bootstrap.DatabaseBootstrap;
import com.nepalpharmacy.inventory.InventoryAlertService;
import com.nepalpharmacy.inventory.ui.InventoryAlertScreen;
import com.nepalpharmacy.party.CustomerService;
import com.nepalpharmacy.party.SupplierService;
import com.nepalpharmacy.product.ProductService;
import com.nepalpharmacy.product.ui.ProductScreen;
import com.nepalpharmacy.purchasing.PurchaseService;
import com.nepalpharmacy.purchasing.PurchaseHistoryService;
import com.nepalpharmacy.purchasing.PurchaseReturnService;
import com.nepalpharmacy.purchasing.ui.PurchaseScreen;
import com.nepalpharmacy.purchasing.ui.PurchaseHistoryScreen;
import com.nepalpharmacy.purchasing.ui.PurchaseReturnScreen;
import com.nepalpharmacy.sales.SaleService;
import com.nepalpharmacy.sales.SaleHistoryService;
import com.nepalpharmacy.sales.SalesReturnService;
import com.nepalpharmacy.sales.ui.POSScreen;
import com.nepalpharmacy.sales.ui.SaleHistoryScreen;
import com.nepalpharmacy.sales.ui.SalesReturnScreen;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public final class PharmacyApplication extends Application {

    private final BorderPane shell = new BorderPane();
    private ProductService productService;
    private SupplierService supplierService;
    private PurchaseService purchaseService;
    private CustomerService customerService;
    private SaleService saleService;
    private SalesReturnService salesReturnService;
    private PurchaseReturnService purchaseReturnService;
    private SaleHistoryService saleHistoryService;
    private PurchaseHistoryService purchaseHistoryService;
    private InventoryAlertService inventoryAlertService;
    private int migrationsExecuted;

    @Override
    public void start(Stage stage) {
        DatabaseBootstrap database = DatabaseBootstrap.inConfiguredDataDirectory();
        migrationsExecuted = database.migrate();
        ApplicationContext context = new ApplicationContext(database);
        productService = context.productService();
        supplierService = context.supplierService();
        purchaseService = context.purchaseService();
        customerService = context.customerService();
        saleService = context.saleService();
        salesReturnService = context.salesReturnService();
        purchaseReturnService = context.purchaseReturnService();
        saleHistoryService = context.saleHistoryService();
        purchaseHistoryService = context.purchaseHistoryService();
        inventoryAlertService = context.inventoryAlertService();

        shell.setTop(createHeader());
        shell.setLeft(createNavigation());
        shell.getStyleClass().add("app-root");
        showDashboard();

        Scene scene = new Scene(shell, 1180, 720);
        scene.getStylesheets().add(
                PharmacyApplication.class.getResource("/styles/app.css").toExternalForm());

        stage.setTitle("Nepal Pharmacy MVP");
        stage.setMinWidth(980);
        stage.setMinHeight(620);
        stage.setScene(scene);
        stage.show();
    }

    private VBox createHeader() {

        Label title = new Label("Nepal Pharmacy");
        title.getStyleClass().add("app-title");

        Label subtitle = new Label("Local-first pharmacy management");
        subtitle.getStyleClass().add("subtitle");

        VBox heading = new VBox(4, title, subtitle);
        heading.setPadding(new Insets(24, 28, 20, 28));
        return heading;
    }

    private VBox createNavigation() {
        Button dashboard = navigationButton("Dashboard");
        dashboard.setOnAction(event -> showDashboard());
        Button products = navigationButton("Products");
        products.setOnAction(event -> showProducts());
        Button purchases = navigationButton("Purchase entry");
        purchases.setOnAction(event -> showPurchases());
        Button pos = navigationButton("Point of sale");
        pos.setOnAction(event -> showPointOfSale());
        Button salesReturns = navigationButton("Sales returns");
        salesReturns.setOnAction(event -> showSalesReturns());
        Button purchaseReturns = navigationButton("Purchase returns");
        purchaseReturns.setOnAction(event -> showPurchaseReturns());
        Button salesHistory = navigationButton("Sales history");
        salesHistory.setOnAction(event -> showSalesHistory());
        Button purchaseHistory = navigationButton("Purchase history");
        purchaseHistory.setOnAction(event -> showPurchaseHistory());
        Button inventoryAlerts = navigationButton("Inventory alerts");
        inventoryAlerts.setOnAction(event -> showInventoryAlerts());

        VBox navigation = new VBox(8, dashboard, products, purchases, pos,
                inventoryAlerts, salesHistory, purchaseHistory, salesReturns, purchaseReturns);
        navigation.getStyleClass().add("navigation");
        navigation.setPadding(new Insets(12));
        navigation.setPrefWidth(175);
        return navigation;
    }

    private Button navigationButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("navigation-button");
        button.setMaxWidth(Double.MAX_VALUE);
        return button;
    }

    private void showDashboard() {

        HBox cards = new HBox(16,
                statusCard("Database", "Ready"),
                statusCard("Schema updates", Integer.toString(migrationsExecuted)),
                statusCard("Current capability", "Catalog + stock alerts + transactions"));
        cards.setPadding(new Insets(12, 28, 28, 28));

        Label guidance = new Label(
                "Maintain the product catalog, receive supplier stock by batch and expiry, then use " +
                "Point of sale for FEFO-guided cash, QR, or credit sales, and record returns " +
                "against their original transaction lines. Review Inventory alerts for expiring " +
                "batches and products that need replenishment.");
        guidance.getStyleClass().add("guidance");
        guidance.setWrapText(true);
        guidance.setMaxWidth(760);

        Button openProducts = new Button("Open product master");
        openProducts.getStyleClass().add("primary-button");
        openProducts.setOnAction(event -> showProducts());
        Button openPurchases = new Button("Record a purchase");
        openPurchases.getStyleClass().add("primary-button");
        openPurchases.setOnAction(event -> showPurchases());
        Button openPos = new Button("Open point of sale");
        openPos.getStyleClass().add("primary-button");
        openPos.setOnAction(event -> showPointOfSale());
        Button openSalesReturns = new Button("Record sales return");
        openSalesReturns.getStyleClass().add("secondary-button");
        openSalesReturns.setOnAction(event -> showSalesReturns());
        Button openPurchaseReturns = new Button("Record purchase return");
        openPurchaseReturns.getStyleClass().add("secondary-button");
        openPurchaseReturns.setOnAction(event -> showPurchaseReturns());
        Button openSalesHistory = new Button("View sales history");
        openSalesHistory.getStyleClass().add("secondary-button");
        openSalesHistory.setOnAction(event -> showSalesHistory());
        Button openPurchaseHistory = new Button("View purchase history");
        openPurchaseHistory.getStyleClass().add("secondary-button");
        openPurchaseHistory.setOnAction(event -> showPurchaseHistory());
        Button openInventoryAlerts = new Button("Review inventory alerts");
        openInventoryAlerts.getStyleClass().add("primary-button");
        openInventoryAlerts.setOnAction(event -> showInventoryAlerts());

        FlowPane actions = new FlowPane(10, 10, openInventoryAlerts, openProducts, openPurchases, openPos,
                openSalesHistory, openPurchaseHistory, openSalesReturns, openPurchaseReturns);
        VBox content = new VBox(20, cards, guidance, actions);
        content.setPadding(new Insets(8, 28, 28, 28));
        shell.setCenter(content);
    }

    private void showProducts() {
        shell.setCenter(new ProductScreen(productService).view());
    }

    private void showPurchases() {
        shell.setCenter(new PurchaseScreen(productService, supplierService, purchaseService).view());
    }

    private void showPointOfSale() {
        shell.setCenter(new POSScreen(saleService, customerService, productService).view());
    }

    private void showSalesReturns() {
        shell.setCenter(new SalesReturnScreen(salesReturnService).view());
    }

    private void showSalesReturns(long invoiceNumber) {
        shell.setCenter(new SalesReturnScreen(salesReturnService, invoiceNumber).view());
    }

    private void showPurchaseReturns() {
        shell.setCenter(new PurchaseReturnScreen(purchaseReturnService).view());
    }

    private void showPurchaseReturns(java.util.UUID purchaseId) {
        shell.setCenter(new PurchaseReturnScreen(purchaseReturnService, purchaseId).view());
    }

    private void showSalesHistory() {
        shell.setCenter(new SaleHistoryScreen(saleHistoryService, this::showSalesReturns).view());
    }

    private void showPurchaseHistory() {
        shell.setCenter(new PurchaseHistoryScreen(
                purchaseHistoryService, this::showPurchaseReturns).view());
    }

    private void showInventoryAlerts() {
        shell.setCenter(new InventoryAlertScreen(inventoryAlertService).view());
    }

    private VBox statusCard(String label, String value) {
        Label labelNode = new Label(label);
        labelNode.getStyleClass().add("card-label");

        Label valueNode = new Label(value);
        valueNode.getStyleClass().add("card-value");
        valueNode.setWrapText(true);

        VBox card = new VBox(10, labelNode, valueNode);
        card.getStyleClass().add("status-card");
        card.setAlignment(Pos.TOP_LEFT);
        card.setPadding(new Insets(18));
        HBox.setHgrow(card, Priority.ALWAYS);
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
