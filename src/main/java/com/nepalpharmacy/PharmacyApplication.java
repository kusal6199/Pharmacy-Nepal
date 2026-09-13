package com.nepalpharmacy;

import com.nepalpharmacy.bootstrap.DatabaseBootstrap;
import com.nepalpharmacy.product.ProductService;
import com.nepalpharmacy.product.infrastructure.JdbcProductRepository;
import com.nepalpharmacy.product.ui.ProductScreen;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public final class PharmacyApplication extends Application {

    private final BorderPane shell = new BorderPane();
    private ProductService productService;
    private int migrationsExecuted;

    @Override
    public void start(Stage stage) {
        DatabaseBootstrap database = DatabaseBootstrap.inConfiguredDataDirectory();
        migrationsExecuted = database.migrate();
        productService = new ProductService(new JdbcProductRepository(database::openConnection));

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

        VBox navigation = new VBox(8, dashboard, products);
        navigation.getStyleClass().add("navigation");
        navigation.setPadding(new Insets(12));
        navigation.setPrefWidth(150);
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
                statusCard("Current capability", "Product master"));
        cards.setPadding(new Insets(12, 28, 28, 28));

        Label guidance = new Label(
                "Use the product master to add medicines and other shop products, review the catalog, " +
                "and edit or deactivate existing entries.");
        guidance.getStyleClass().add("guidance");
        guidance.setWrapText(true);
        guidance.setMaxWidth(760);

        Button openProducts = new Button("Open product master");
        openProducts.getStyleClass().add("primary-button");
        openProducts.setOnAction(event -> showProducts());

        VBox content = new VBox(20, cards, guidance, openProducts);
        content.setPadding(new Insets(8, 28, 28, 28));
        shell.setCenter(content);
    }

    private void showProducts() {
        shell.setCenter(new ProductScreen(productService).view());
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
