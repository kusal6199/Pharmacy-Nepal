package com.nepalpharmacy.purchasing.ui;

import com.nepalpharmacy.purchasing.PurchaseDetail;
import com.nepalpharmacy.purchasing.PurchaseDetailLine;
import com.nepalpharmacy.purchasing.PurchaseHistoryResult;
import com.nepalpharmacy.purchasing.PurchaseHistoryService;
import com.nepalpharmacy.purchasing.PurchaseHistoryValidationException;
import com.nepalpharmacy.purchasing.PurchaseSummary;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class PurchaseHistoryScreen {

    private final PurchaseHistoryService history;
    private final Consumer<UUID> openReturn;
    private final VBox root = new VBox();
    private final DatePicker fromDate = new DatePicker();
    private final DatePicker toDate = new DatePicker();
    private final TextField supplier = new TextField();
    private final TextField supplierInvoice = new TextField();
    private final ObservableList<PurchaseSummary> purchases = FXCollections.observableArrayList();
    private final TableView<PurchaseSummary> purchaseTable = new TableView<>(purchases);
    private final ObservableList<PurchaseDetailLine> lines = FXCollections.observableArrayList();
    private final TableView<PurchaseDetailLine> detailTable = new TableView<>(lines);
    private final Label detailSummary = new Label("Select a purchase to view its original lines.");
    private final Label feedback = new Label();
    private final Button createReturn = new Button("Return to supplier");
    private PurchaseDetail selectedDetail;

    public PurchaseHistoryScreen(PurchaseHistoryService history, Consumer<UUID> openReturn) {
        this.history = Objects.requireNonNull(history, "history");
        this.openReturn = Objects.requireNonNull(openReturn, "openReturn");
        configureView();
        showRecent();
    }

    public Parent view() {
        return root;
    }

    private void configureView() {
        Label title = new Label("Purchase history");
        title.getStyleClass().add("section-title");
        Label subtitle = new Label(
                "Find supplier purchases, inspect saved costs and stock, and start a return.");
        subtitle.getStyleClass().add("subtitle");
        VBox heading = new VBox(4, title, subtitle);
        heading.setPadding(new Insets(24, 24, 16, 24));

        supplier.setPromptText("Supplier name contains");
        supplierInvoice.setPromptText("Supplier invoice contains");
        supplier.setOnAction(event -> search());
        supplierInvoice.setOnAction(event -> search());
        configurePurchaseTable();
        configureDetailTable();
        feedback.getStyleClass().add("feedback");
        feedback.setWrapText(true);
        detailSummary.getStyleClass().add("table-hint");
        detailSummary.setWrapText(true);

        createReturn.getStyleClass().add("primary-button");
        createReturn.setDisable(true);
        createReturn.setOnAction(event -> {
            if (selectedDetail != null && selectedDetail.hasReturnableQuantity()) {
                openReturn.accept(selectedDetail.summary().id());
            }
        });
        HBox detailActions = new HBox(createReturn);
        detailActions.setAlignment(Pos.CENTER_RIGHT);

        VBox content = new VBox(18, filtersPanel(), panel("Supplier purchases", purchaseTable),
                panel("Purchase detail", new VBox(10, detailSummary, detailTable, detailActions)),
                feedback);
        content.setPadding(new Insets(0, 24, 28, 24));
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("purchase-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        root.getChildren().addAll(heading, scroll);
    }

    private VBox filtersPanel() {
        GridPane filters = new GridPane();
        filters.setHgap(10);
        filters.setVgap(10);
        filters.addRow(0, label("Supplier"), supplier,
                label("Supplier invoice"), supplierInvoice);
        filters.addRow(1, label("From date"), fromDate, label("To date"), toDate);
        GridPane.setHgrow(supplier, Priority.ALWAYS);
        GridPane.setHgrow(supplierInvoice, Priority.ALWAYS);

        Button search = new Button("Search");
        search.getStyleClass().add("primary-button");
        search.setOnAction(event -> search());
        Button reset = new Button("Reset / show latest 50");
        reset.getStyleClass().add("secondary-button");
        reset.setOnAction(event -> reset());
        HBox actions = new HBox(10, search, reset);
        actions.setAlignment(Pos.CENTER_RIGHT);
        return panel("Search purchases", new VBox(10, filters, actions));
    }

    private void configurePurchaseTable() {
        purchaseTable.setPlaceholder(new Label("No purchases found."));
        purchaseTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        purchaseTable.setPrefHeight(245);
        purchaseTable.getColumns().add(summaryColumn("Date",
                purchase -> purchase.purchaseDate().toString(), 95));
        purchaseTable.getColumns().add(summaryColumn("Supplier",
                PurchaseSummary::supplierName, 180));
        purchaseTable.getColumns().add(summaryColumn("Supplier invoice",
                purchase -> purchase.supplierInvoice() == null
                        ? "—" : purchase.supplierInvoice(), 150));
        purchaseTable.getColumns().add(summaryColumn("Payment",
                purchase -> purchase.paymentMethod().displayName(), 120));
        purchaseTable.getColumns().add(summaryColumn("Total NPR",
                purchase -> formatPaisa(purchase.totalAmountPaisa()), 100));
        purchaseTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, previous, selected) -> loadDetail(selected));
    }

    private void configureDetailTable() {
        detailTable.setPlaceholder(new Label("No purchase selected."));
        detailTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        detailTable.setPrefHeight(220);
        detailTable.getColumns().add(detailColumn("Product",
                PurchaseDetailLine::productName, 150));
        detailTable.getColumns().add(detailColumn("Batch",
                PurchaseDetailLine::batchNumber, 80));
        detailTable.getColumns().add(detailColumn("Expiry",
                line -> line.expiryDate().toString(), 90));
        detailTable.getColumns().add(detailColumn("Received",
                line -> Integer.toString(line.quantityReceivedBaseUnits()), 65));
        detailTable.getColumns().add(detailColumn("Unit NPR",
                line -> formatPaisa(line.unitPurchasePricePaisa()), 75));
        detailTable.getColumns().add(detailColumn("Line total",
                line -> formatPaisa(line.lineTotalPaisa()), 80));
        detailTable.getColumns().add(detailColumn("Returned",
                line -> Long.toString(line.previouslyReturnedBaseUnits()), 65));
        detailTable.getColumns().add(detailColumn("Remaining",
                line -> Long.toString(line.remainingReturnableBaseUnits()), 65));
        detailTable.getColumns().add(detailColumn("Current stock",
                line -> Long.toString(line.currentBatchStockBaseUnits()), 80));
    }

    private void showRecent() {
        try {
            show(history.findRecent(), "Showing the latest supplier purchases.",
                    PurchaseHistoryService.RECENT_LIMIT);
        } catch (RuntimeException exception) {
            feedback.setText(message(exception));
        }
    }

    private void search() {
        try {
            show(history.search(fromDate.getValue(), toDate.getValue(),
                    supplier.getText(), supplierInvoice.getText()), "Search complete.",
                    PurchaseHistoryService.SEARCH_LIMIT);
        } catch (RuntimeException exception) {
            feedback.setText(message(exception));
        }
    }

    private void reset() {
        fromDate.setValue(null);
        toDate.setValue(null);
        supplier.clear();
        supplierInvoice.clear();
        showRecent();
    }

    private void show(PurchaseHistoryResult result, String successMessage, int resultLimit) {
        clearDetail();
        purchases.setAll(result.purchases());
        feedback.setText(result.truncated()
                ? "Showing the first " + resultLimit
                        + " results. Narrow your search to see the rest."
                : successMessage + " " + result.purchases().size() + " result(s)." );
        if (!purchases.isEmpty()) {
            purchaseTable.getSelectionModel().selectFirst();
        }
    }

    private void loadDetail(PurchaseSummary selected) {
        clearDetail();
        if (selected == null) {
            return;
        }
        try {
            selectedDetail = history.findDetail(selected.id()).orElse(null);
            if (selectedDetail == null) {
                detailSummary.setText("That purchase no longer exists.");
                return;
            }
            lines.setAll(selectedDetail.lines());
            PurchaseSummary summary = selectedDetail.summary();
            String invoiceText = summary.supplierInvoice() == null
                    ? "no supplier invoice" : "invoice " + summary.supplierInvoice();
            detailSummary.setText(summary.purchaseDate() + " • " + summary.supplierName()
                    + " • " + invoiceText + " • " + summary.paymentMethod().displayName()
                    + " • total NPR "
                    + formatPaisa(summary.totalAmountPaisa()));
            boolean returnable = selectedDetail.hasReturnableQuantity();
            createReturn.setDisable(!returnable);
            createReturn.setText(returnable
                    ? "Return to supplier" : "Nothing currently returnable");
        } catch (RuntimeException exception) {
            feedback.setText(message(exception));
        }
    }

    private void clearDetail() {
        selectedDetail = null;
        lines.clear();
        detailSummary.setText("Select a purchase to view its original lines.");
        createReturn.setDisable(true);
        createReturn.setText("Return to supplier");
    }

    private static <T> TableColumn<T, String> column(
            String title, java.util.function.Function<T, String> value, double width) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        column.setPrefWidth(width);
        return column;
    }

    private static TableColumn<PurchaseSummary, String> summaryColumn(
            String title, java.util.function.Function<PurchaseSummary, String> value,
            double width) {
        return column(title, value, width);
    }

    private static TableColumn<PurchaseDetailLine, String> detailColumn(
            String title, java.util.function.Function<PurchaseDetailLine, String> value,
            double width) {
        return column(title, value, width);
    }

    private static VBox panel(String title, javafx.scene.Node content) {
        Label heading = new Label(title);
        heading.getStyleClass().add("panel-title");
        VBox panel = new VBox(10, heading, content);
        panel.getStyleClass().add("form-card");
        panel.setPadding(new Insets(16));
        return panel;
    }

    private static Label label(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("field-label");
        return label;
    }

    private static String formatPaisa(long paisa) {
        return BigDecimal.valueOf(paisa, 2).setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }

    private static String message(RuntimeException exception) {
        if (exception instanceof PurchaseHistoryValidationException validation) {
            return validation.fieldErrors().values().stream().collect(Collectors.joining(" "));
        }
        return exception.getMessage() == null
                ? "Could not load purchase history." : exception.getMessage();
    }
}
