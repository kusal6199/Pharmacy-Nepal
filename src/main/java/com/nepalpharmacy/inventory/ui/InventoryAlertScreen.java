package com.nepalpharmacy.inventory.ui;

import com.nepalpharmacy.inventory.ExpiryBatchAlert;
import com.nepalpharmacy.inventory.ExpiryHorizon;
import com.nepalpharmacy.inventory.InventoryAlertCriteria;
import com.nepalpharmacy.inventory.InventoryAlertDashboard;
import com.nepalpharmacy.inventory.InventoryAlertService;
import com.nepalpharmacy.inventory.InventoryAlertSummary;
import com.nepalpharmacy.inventory.ProductStockAlert;
import com.nepalpharmacy.inventory.ProductStockFilter;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Objects;
import java.util.function.Function;

public final class InventoryAlertScreen {

    private final InventoryAlertService alerts;
    private final VBox root = new VBox();
    private final ComboBox<ExpiryHorizon> expiryHorizon = new ComboBox<>();
    private final TextField expirySearch = new TextField();
    private final ComboBox<ProductStockFilter> stockFilter = new ComboBox<>();
    private final TextField productSearch = new TextField();
    private final ObservableList<ExpiryBatchAlert> expiryRows =
            FXCollections.observableArrayList();
    private final ObservableList<ProductStockAlert> stockRows =
            FXCollections.observableArrayList();
    private final TableView<ExpiryBatchAlert> expiryTable = new TableView<>(expiryRows);
    private final TableView<ProductStockAlert> stockTable = new TableView<>(stockRows);
    private final FlowPane summaryCards = new FlowPane(10, 10);
    private final Label asOf = new Label();
    private final Label expiryFeedback = new Label();
    private final Label stockFeedback = new Label();
    private final Label feedback = new Label();

    public InventoryAlertScreen(InventoryAlertService alerts) {
        this.alerts = Objects.requireNonNull(alerts, "alerts");
        configureView();
        refresh();
    }

    public Parent view() {
        return root;
    }

    private void configureView() {
        Label title = new Label("Inventory alerts");
        title.getStyleClass().add("section-title");
        Label subtitle = new Label(
                "Review expiring batches and active products that need replenishment.");
        subtitle.getStyleClass().add("subtitle");
        VBox headingText = new VBox(4, title, subtitle);

        asOf.getStyleClass().add("table-hint");
        Button refresh = new Button("Refresh");
        refresh.getStyleClass().add("primary-button");
        refresh.setOnAction(event -> refresh());
        VBox dateAndRefresh = new VBox(6, asOf, refresh);
        dateAndRefresh.setAlignment(Pos.CENTER_RIGHT);

        HBox heading = new HBox(16, headingText, dateAndRefresh);
        HBox.setHgrow(headingText, Priority.ALWAYS);
        heading.setAlignment(Pos.CENTER_LEFT);
        heading.setPadding(new Insets(24, 24, 16, 24));

        configureFilters();
        configureExpiryTable();
        configureStockTable();
        summaryCards.setPrefWrapLength(900);
        summaryCards.getStyleClass().add("alert-summary");

        expiryFeedback.getStyleClass().add("table-hint");
        stockFeedback.getStyleClass().add("table-hint");
        feedback.getStyleClass().add("feedback");
        feedback.setWrapText(true);

        VBox content = new VBox(18,
                summaryCards,
                panel("Expiry alerts", new VBox(10, expiryFilters(), expiryTable, expiryFeedback)),
                panel("Low-stock and out-of-stock products",
                        new VBox(10, stockFilters(), stockTable, stockFeedback)),
                feedback);
        content.setPadding(new Insets(0, 24, 28, 24));
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("pos-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        root.getChildren().addAll(heading, scroll);
    }

    private void configureFilters() {
        expiryHorizon.setItems(FXCollections.observableArrayList(ExpiryHorizon.values()));
        expiryHorizon.getSelectionModel().select(ExpiryHorizon.ALL);
        expiryHorizon.setMaxWidth(Double.MAX_VALUE);
        expiryHorizon.setOnAction(event -> refresh());
        expirySearch.setPromptText("Product, generic, or manufacturer");
        expirySearch.setOnAction(event -> refresh());

        stockFilter.setItems(FXCollections.observableArrayList(ProductStockFilter.values()));
        stockFilter.getSelectionModel().select(ProductStockFilter.ALL);
        stockFilter.setMaxWidth(Double.MAX_VALUE);
        stockFilter.setOnAction(event -> refresh());
        productSearch.setPromptText("Product name contains");
        productSearch.setOnAction(event -> refresh());
    }

    private Node expiryFilters() {
        GridPane filters = new GridPane();
        filters.setHgap(10);
        filters.setVgap(10);
        filters.addRow(0, fieldLabel("Expiry window"), expiryHorizon,
                fieldLabel("Search"), expirySearch);
        GridPane.setHgrow(expiryHorizon, Priority.ALWAYS);
        GridPane.setHgrow(expirySearch, Priority.ALWAYS);
        Button apply = new Button("Apply filters");
        apply.getStyleClass().add("secondary-button");
        apply.setOnAction(event -> refresh());
        HBox actions = new HBox(apply);
        actions.setAlignment(Pos.CENTER_RIGHT);
        return new VBox(10, filters, actions);
    }

    private Node stockFilters() {
        GridPane filters = new GridPane();
        filters.setHgap(10);
        filters.setVgap(10);
        filters.addRow(0, fieldLabel("Stock status"), stockFilter,
                fieldLabel("Product search"), productSearch);
        GridPane.setHgrow(stockFilter, Priority.ALWAYS);
        GridPane.setHgrow(productSearch, Priority.ALWAYS);
        Button apply = new Button("Apply filters");
        apply.getStyleClass().add("secondary-button");
        apply.setOnAction(event -> refresh());
        HBox actions = new HBox(apply);
        actions.setAlignment(Pos.CENTER_RIGHT);
        return new VBox(10, filters, actions);
    }

    private void configureExpiryTable() {
        expiryTable.setPlaceholder(new Label("No batches match the selected expiry filters."));
        expiryTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        expiryTable.setPrefHeight(270);
        expiryTable.getColumns().add(column("Status", row -> row.status().toString(), 95));
        expiryTable.getColumns().add(column("Product", ExpiryBatchAlert::productName, 150));
        expiryTable.getColumns().add(column("Generic", row -> display(row.genericName()), 110));
        expiryTable.getColumns().add(column("Batch", ExpiryBatchAlert::batchNumber, 90));
        expiryTable.getColumns().add(column("Expiry", row -> row.expiryDate().toString(), 95));
        expiryTable.getColumns().add(column("Days", row -> Long.toString(row.daysRemaining()), 55));
        expiryTable.getColumns().add(column("Physical stock",
                row -> Long.toString(row.physicalStockBaseUnits()), 95));
        expiryTable.getColumns().add(column("Unit", row -> row.unitOfSale().toString(), 70));
        expiryTable.getColumns().add(column("Manufacturer",
                row -> display(row.manufacturer()), 110));
        expiryTable.getColumns().add(column("Product state",
                row -> row.productActive() ? "Active" : "Inactive", 85));
    }

    private void configureStockTable() {
        stockTable.setPlaceholder(new Label("No active products match the stock alert filters."));
        stockTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        stockTable.setPrefHeight(250);
        stockTable.getColumns().add(column("Status", row -> row.status().toString(), 95));
        stockTable.getColumns().add(column("Product", ProductStockAlert::productName, 150));
        stockTable.getColumns().add(column("Generic", row -> display(row.genericName()), 110));
        stockTable.getColumns().add(column("Manufacturer",
                row -> display(row.manufacturer()), 110));
        stockTable.getColumns().add(column("Sellable",
                row -> Long.toString(row.sellableStockBaseUnits()), 75));
        stockTable.getColumns().add(column("Physical",
                row -> Long.toString(row.physicalStockBaseUnits()), 75));
        stockTable.getColumns().add(column("Expired",
                row -> Long.toString(row.expiredStockBaseUnits()), 70));
        stockTable.getColumns().add(column("Threshold",
                row -> Integer.toString(row.reorderThresholdBaseUnits()), 75));
        stockTable.getColumns().add(column("Unit", row -> row.unitOfSale().toString(), 70));
    }

    private void refresh() {
        try {
            InventoryAlertDashboard dashboard = alerts.loadDashboard(new InventoryAlertCriteria(
                    expiryHorizon.getValue(), expirySearch.getText(),
                    stockFilter.getValue(), productSearch.getText()));
            show(dashboard);
            feedback.setText("Inventory alerts refreshed from current movement data.");
        } catch (RuntimeException exception) {
            feedback.setText(exception.getMessage() == null
                    ? "Could not load inventory alerts." : exception.getMessage());
        }
    }

    private void show(InventoryAlertDashboard dashboard) {
        asOf.setText("As of " + dashboard.asOfDate());
        showSummary(dashboard.summary());
        expiryRows.setAll(dashboard.expiryAlerts().rows());
        stockRows.setAll(dashboard.productStockAlerts().rows());
        expiryFeedback.setText(resultMessage(
                "expiry batch", expiryRows.size(), dashboard.expiryAlerts().truncated()));
        stockFeedback.setText(resultMessage(
                "stock alert", stockRows.size(), dashboard.productStockAlerts().truncated()));
    }

    private void showSummary(InventoryAlertSummary summary) {
        summaryCards.getChildren().setAll(
                summaryCard("Expired batches", summary.expiredBatchCount()),
                summaryCard("0-30 day batches", summary.days0To30BatchCount()),
                summaryCard("31-60 day batches", summary.days31To60BatchCount()),
                summaryCard("61-90 day batches", summary.days61To90BatchCount()),
                summaryCard("Low-stock products", summary.lowStockProductCount()),
                summaryCard("Out-of-stock products", summary.outOfStockProductCount()));
    }

    private static VBox summaryCard(String title, int count) {
        Label titleNode = new Label(title);
        titleNode.getStyleClass().add("card-label");
        Label countNode = new Label(Integer.toString(count));
        countNode.getStyleClass().add("card-value");
        VBox card = new VBox(6, titleNode, countNode);
        card.getStyleClass().add("status-card");
        card.setPadding(new Insets(12));
        card.setPrefWidth(155);
        return card;
    }

    private static String resultMessage(String noun, int size, boolean truncated) {
        if (truncated) {
            return "Showing the first " + InventoryAlertService.RESULT_LIMIT + " " + noun
                    + " rows. Narrow the filters to see the rest.";
        }
        return size + " " + noun + (size == 1 ? "" : "s") + ".";
    }

    private static <T> TableColumn<T, String> column(
            String title, Function<T, String> value, double width) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        column.setPrefWidth(width);
        return column;
    }

    private static VBox panel(String title, Node content) {
        Label heading = new Label(title);
        heading.getStyleClass().add("form-title");
        VBox panel = new VBox(10, heading, content);
        panel.getStyleClass().add("form-panel");
        panel.setPadding(new Insets(16));
        return panel;
    }

    private static Label fieldLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("field-label");
        return label;
    }

    private static String display(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }
}
