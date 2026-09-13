package com.nepalpharmacy.sales.ui;

import com.nepalpharmacy.sales.PaymentMethod;
import com.nepalpharmacy.sales.SaleDetail;
import com.nepalpharmacy.sales.SaleDetailLine;
import com.nepalpharmacy.sales.SaleHistoryResult;
import com.nepalpharmacy.sales.SaleHistoryService;
import com.nepalpharmacy.sales.SaleHistoryValidationException;
import com.nepalpharmacy.sales.SaleSummary;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
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
import java.util.function.LongConsumer;
import java.util.stream.Collectors;

public final class SaleHistoryScreen {

    private final SaleHistoryService history;
    private final LongConsumer openReturn;
    private final VBox root = new VBox();
    private final TextField invoice = new TextField();
    private final DatePicker fromDate = new DatePicker();
    private final DatePicker toDate = new DatePicker();
    private final TextField customer = new TextField();
    private final ComboBox<PaymentFilter> payment = new ComboBox<>();
    private final ObservableList<SaleSummary> sales = FXCollections.observableArrayList();
    private final TableView<SaleSummary> salesTable = new TableView<>(sales);
    private final ObservableList<SaleDetailLine> lines = FXCollections.observableArrayList();
    private final TableView<SaleDetailLine> detailTable = new TableView<>(lines);
    private final Label detailSummary = new Label("Select a sale to view its original lines.");
    private final Label feedback = new Label();
    private final Button createReturn = new Button("Create return");
    private SaleDetail selectedDetail;

    public SaleHistoryScreen(SaleHistoryService history, LongConsumer openReturn) {
        this.history = Objects.requireNonNull(history, "history");
        this.openReturn = Objects.requireNonNull(openReturn, "openReturn");
        configureView();
        showRecent();
    }

    public Parent view() {
        return root;
    }

    private void configureView() {
        Label title = new Label("Sales history");
        title.getStyleClass().add("section-title");
        Label subtitle = new Label(
                "Find completed invoices, inspect their saved values, and start a return.");
        subtitle.getStyleClass().add("subtitle");
        VBox heading = new VBox(4, title, subtitle);
        heading.setPadding(new Insets(24, 24, 16, 24));

        configureFilters();
        configureSalesTable();
        configureDetailTable();
        feedback.getStyleClass().add("feedback");
        feedback.setWrapText(true);
        detailSummary.getStyleClass().add("table-hint");
        detailSummary.setWrapText(true);

        createReturn.getStyleClass().add("primary-button");
        createReturn.setDisable(true);
        createReturn.setOnAction(event -> {
            if (selectedDetail != null && selectedDetail.hasReturnableQuantity()) {
                openReturn.accept(selectedDetail.summary().invoiceNumber());
            }
        });
        HBox detailActions = new HBox(createReturn);
        detailActions.setAlignment(Pos.CENTER_RIGHT);

        VBox content = new VBox(18, filtersPanel(), panel("Completed sales", salesTable),
                panel("Sale detail", new VBox(10, detailSummary, detailTable, detailActions)),
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
        invoice.setPromptText("2 or 000002");
        customer.setPromptText("Customer name contains");
        payment.setItems(FXCollections.observableArrayList(
                new PaymentFilter("All payment methods", null),
                new PaymentFilter(PaymentMethod.CASH.toString(), PaymentMethod.CASH),
                new PaymentFilter(PaymentMethod.QR.toString(), PaymentMethod.QR),
                new PaymentFilter(PaymentMethod.CREDIT.toString(), PaymentMethod.CREDIT)));
        payment.getSelectionModel().selectFirst();
        invoice.setOnAction(event -> search());
        customer.setOnAction(event -> search());
    }

    private VBox filtersPanel() {
        GridPane filters = new GridPane();
        filters.setHgap(10);
        filters.setVgap(10);
        filters.addRow(0, label("Invoice"), invoice, label("Customer"), customer);
        filters.addRow(1, label("From date"), fromDate, label("To date"), toDate);
        filters.addRow(2, label("Payment"), payment);
        GridPane.setHgrow(invoice, Priority.ALWAYS);
        GridPane.setHgrow(customer, Priority.ALWAYS);
        payment.setMaxWidth(Double.MAX_VALUE);

        Button search = new Button("Search");
        search.getStyleClass().add("primary-button");
        search.setOnAction(event -> search());
        Button reset = new Button("Reset / show latest 50");
        reset.getStyleClass().add("secondary-button");
        reset.setOnAction(event -> reset());
        HBox actions = new HBox(10, search, reset);
        actions.setAlignment(Pos.CENTER_RIGHT);
        return panel("Search sales", new VBox(10, filters, actions));
    }

    private void configureSalesTable() {
        salesTable.setPlaceholder(new Label("No completed sales found."));
        salesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        salesTable.setPrefHeight(245);
        salesTable.getColumns().add(summaryColumn("Invoice",
                sale -> String.format("%06d", sale.invoiceNumber()), 90));
        salesTable.getColumns().add(summaryColumn("Date",
                sale -> sale.saleDate().toString(), 95));
        salesTable.getColumns().add(summaryColumn("Customer", SaleSummary::customerName, 150));
        salesTable.getColumns().add(summaryColumn("Payment",
                sale -> sale.paymentMethod().toString(), 105));
        salesTable.getColumns().add(summaryColumn("Total NPR",
                sale -> formatPaisa(sale.totalAmountPaisa()), 100));
        salesTable.getColumns().add(summaryColumn("Return status",
                sale -> sale.returnStatus().toString(), 100));
        salesTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, previous, selected) -> loadDetail(selected));
    }

    private void configureDetailTable() {
        detailTable.setPlaceholder(new Label("No sale selected."));
        detailTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        detailTable.setPrefHeight(220);
        detailTable.getColumns().add(detailColumn("Product", SaleDetailLine::productName, 170));
        detailTable.getColumns().add(detailColumn("Batch", SaleDetailLine::batchNumber, 90));
        detailTable.getColumns().add(detailColumn("Expiry",
                line -> line.expiryDate().toString(), 95));
        detailTable.getColumns().add(detailColumn("Sold",
                line -> Integer.toString(line.quantitySoldBaseUnits()), 60));
        detailTable.getColumns().add(detailColumn("Unit NPR",
                line -> formatPaisa(line.unitSalePricePaisa()), 80));
        detailTable.getColumns().add(detailColumn("Line total",
                line -> formatPaisa(line.lineTotalPaisa()), 85));
        detailTable.getColumns().add(detailColumn("Returned",
                line -> Long.toString(line.previouslyReturnedBaseUnits()), 70));
        detailTable.getColumns().add(detailColumn("Remaining",
                line -> Long.toString(line.remainingReturnableBaseUnits()), 75));
    }

    private void showRecent() {
        try {
            show(history.findRecent(), "Showing the latest completed sales.",
                    SaleHistoryService.RECENT_LIMIT);
        } catch (RuntimeException exception) {
            feedback.setText(message(exception));
        }
    }

    private void search() {
        try {
            PaymentFilter filter = payment.getValue();
            show(history.search(invoice.getText(), fromDate.getValue(), toDate.getValue(),
                    customer.getText(), filter == null ? null : filter.method()),
                    "Search complete.", SaleHistoryService.SEARCH_LIMIT);
        } catch (RuntimeException exception) {
            feedback.setText(message(exception));
        }
    }

    private void reset() {
        invoice.clear();
        fromDate.setValue(null);
        toDate.setValue(null);
        customer.clear();
        payment.getSelectionModel().selectFirst();
        showRecent();
    }

    private void show(SaleHistoryResult result, String successMessage, int resultLimit) {
        clearDetail();
        sales.setAll(result.sales());
        feedback.setText(result.truncated()
                ? "Showing the first " + resultLimit
                        + " results. Narrow your search to see the rest."
                : successMessage + " " + result.sales().size() + " result(s)." );
        if (!sales.isEmpty()) {
            salesTable.getSelectionModel().selectFirst();
        }
    }

    private void loadDetail(SaleSummary selected) {
        clearDetail();
        if (selected == null) {
            return;
        }
        try {
            selectedDetail = history.findDetail(selected.id()).orElse(null);
            if (selectedDetail == null) {
                detailSummary.setText("That sale no longer exists.");
                return;
            }
            lines.setAll(selectedDetail.lines());
            SaleSummary summary = selectedDetail.summary();
            detailSummary.setText("Invoice " + String.format("%06d", summary.invoiceNumber())
                    + " • " + summary.saleDate() + " • " + summary.customerName()
                    + " • " + summary.paymentMethod() + " • total NPR "
                    + formatPaisa(summary.totalAmountPaisa()) + " • returns "
                    + summary.returnStatus());
            boolean returnable = selectedDetail.hasReturnableQuantity();
            createReturn.setDisable(!returnable);
            createReturn.setText(returnable ? "Create return" : "Fully returned");
        } catch (RuntimeException exception) {
            feedback.setText(message(exception));
        }
    }

    private void clearDetail() {
        selectedDetail = null;
        lines.clear();
        detailSummary.setText("Select a sale to view its original lines.");
        createReturn.setDisable(true);
        createReturn.setText("Create return");
    }

    private static <T> TableColumn<T, String> column(
            String title, java.util.function.Function<T, String> value, double width) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        column.setPrefWidth(width);
        return column;
    }

    private static TableColumn<SaleSummary, String> summaryColumn(
            String title, java.util.function.Function<SaleSummary, String> value, double width) {
        return column(title, value, width);
    }

    private static TableColumn<SaleDetailLine, String> detailColumn(
            String title, java.util.function.Function<SaleDetailLine, String> value, double width) {
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
        if (exception instanceof SaleHistoryValidationException validation) {
            return validation.fieldErrors().values().stream().collect(Collectors.joining(" "));
        }
        return exception.getMessage() == null
                ? "Could not load sales history." : exception.getMessage();
    }

    private record PaymentFilter(String label, PaymentMethod method) {
        @Override
        public String toString() {
            return label;
        }
    }
}
