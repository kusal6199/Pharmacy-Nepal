package com.nepalpharmacy.sales.ui;

import com.nepalpharmacy.sales.PaymentMethod;
import com.nepalpharmacy.sales.SalesReturn;
import com.nepalpharmacy.sales.SalesReturnDraft;
import com.nepalpharmacy.sales.SalesReturnLineDraft;
import com.nepalpharmacy.sales.SalesReturnReason;
import com.nepalpharmacy.sales.SalesReturnService;
import com.nepalpharmacy.sales.SalesReturnSource;
import com.nepalpharmacy.sales.SalesReturnSourceLine;
import com.nepalpharmacy.sales.SalesReturnValidationException;
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
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

public final class SalesReturnScreen {

    private final SalesReturnService returns;
    private final VBox root = new VBox();
    private final TextField invoiceNumber = new TextField();
    private final Label sourceSummary = new Label("Load an original sale to begin.");
    private final ObservableList<SalesReturnSourceLine> sourceLines =
            FXCollections.observableArrayList();
    private final TableView<SalesReturnSourceLine> sourceTable = new TableView<>(sourceLines);
    private final TextField quantity = new TextField();
    private final ObservableList<ReturnRow> draftLines = FXCollections.observableArrayList();
    private final TableView<ReturnRow> draftTable = new TableView<>(draftLines);
    private final DatePicker returnDate = new DatePicker(LocalDate.now());
    private final ComboBox<SalesReturnReason> reason = new ComboBox<>();
    private final ComboBox<PaymentMethod> refundMethod = new ComboBox<>();
    private final TextArea notes = new TextArea();
    private final Label total = new Label("Refund total: NPR 0.00");
    private final Label feedback = new Label();
    private SalesReturnSource source;

    public SalesReturnScreen(SalesReturnService returns) {
        this.returns = returns;
        configureView();
    }

    public SalesReturnScreen(SalesReturnService returns, long invoiceNumber) {
        this(returns);
        this.invoiceNumber.setText(Long.toString(invoiceNumber));
        loadSale();
    }

    public Parent view() {
        return root;
    }

    private void configureView() {
        Label title = new Label("Sales return");
        title.getStyleClass().add("section-title");
        Label subtitle = new Label(
                "Return items against an original sale line and restore its exact batch stock.");
        subtitle.getStyleClass().add("subtitle");
        VBox heading = new VBox(4, title, subtitle);
        heading.setPadding(new Insets(24, 24, 16, 24));

        configureSourceTable();
        configureDraftTable();
        reason.setItems(FXCollections.observableArrayList(SalesReturnReason.values()));
        resetRefundMethods();
        reason.setValue(SalesReturnReason.CUSTOMER_RETURN);
        notes.setPromptText("Optional, up to 500 characters");
        notes.setPrefRowCount(2);
        feedback.getStyleClass().add("feedback");
        feedback.setWrapText(true);
        sourceSummary.getStyleClass().add("table-hint");

        VBox content = new VBox(18, findSalePanel(), panel("Original sale lines", sourceTable),
                addLinePanel(), draftPanel(), detailsPanel(), saveBar(), feedback);
        content.setPadding(new Insets(0, 24, 28, 24));
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("pos-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        root.getChildren().addAll(heading, scroll);
    }

    private VBox findSalePanel() {
        invoiceNumber.setPromptText("Original sale invoice number");
        invoiceNumber.setOnAction(event -> loadSale());
        Button load = new Button("Load sale");
        load.getStyleClass().add("secondary-button");
        load.setOnAction(event -> loadSale());
        HBox row = new HBox(10, label("Invoice number *"), invoiceNumber, load);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(invoiceNumber, Priority.ALWAYS);
        return panel("Find original sale", new VBox(10, row, sourceSummary));
    }

    private void configureSourceTable() {
        sourceTable.setPlaceholder(new Label("No sale loaded."));
        sourceTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        sourceTable.setPrefHeight(210);
        sourceTable.getColumns().add(sourceColumn("Product", SalesReturnSourceLine::productName, 190));
        sourceTable.getColumns().add(sourceColumn("Batch", SalesReturnSourceLine::batchNumber, 90));
        sourceTable.getColumns().add(sourceColumn("Expiry", line -> line.expiryDate().toString(), 95));
        sourceTable.getColumns().add(sourceColumn("Sold", line -> Integer.toString(line.soldBaseUnits()), 65));
        sourceTable.getColumns().add(sourceColumn("Returned", line -> Long.toString(line.previouslyReturnedBaseUnits()), 75));
        sourceTable.getColumns().add(sourceColumn("Remaining", line -> Long.toString(line.remainingReturnableBaseUnits()), 80));
        sourceTable.getColumns().add(sourceColumn("Unit NPR", line -> formatPaisa(line.unitPricePaisa()), 85));
    }

    private VBox addLinePanel() {
        quantity.setPromptText("Base units to return");
        quantity.setOnAction(event -> addLine());
        Button add = new Button("Add selected line");
        add.getStyleClass().add("secondary-button");
        add.setOnAction(event -> addLine());
        HBox row = new HBox(10, label("Return quantity *"), quantity, add);
        row.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(quantity, Priority.ALWAYS);
        return panel("Add return line", row);
    }

    private void configureDraftTable() {
        draftTable.setPlaceholder(new Label("No return lines yet."));
        draftTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        draftTable.setPrefHeight(180);
        draftTable.getColumns().add(draftColumn("Product", row -> row.source().productName(), 220));
        draftTable.getColumns().add(draftColumn("Batch", row -> row.source().batchNumber(), 100));
        draftTable.getColumns().add(draftColumn("Quantity", row -> Integer.toString(row.draft().quantityReturnedBaseUnits()), 85));
        draftTable.getColumns().add(draftColumn("Unit NPR", row -> formatPaisa(row.draft().unitPricePaisa()), 90));
        draftTable.getColumns().add(draftColumn("Line total", row -> formatPaisa(row.draft().lineTotalPaisa()), 100));
    }

    private VBox draftPanel() {
        Button remove = new Button("Remove selected");
        remove.getStyleClass().add("secondary-button");
        remove.disableProperty().bind(draftTable.getSelectionModel().selectedItemProperty().isNull());
        remove.setOnAction(event -> {
            draftLines.remove(draftTable.getSelectionModel().getSelectedItem());
            updateTotal();
        });
        HBox actions = new HBox(remove);
        actions.setAlignment(Pos.CENTER_RIGHT);
        return panel("Return draft", new VBox(10, draftTable, actions));
    }

    private VBox detailsPanel() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.addRow(0, label("Return date *"), returnDate, label("Reason *"), reason);
        grid.addRow(1, label("Refund method *"), refundMethod, label("Notes"), notes);
        GridPane.setHgrow(reason, Priority.ALWAYS);
        GridPane.setHgrow(notes, Priority.ALWAYS);
        return panel("Return details", grid);
    }

    private HBox saveBar() {
        total.getStyleClass().add("purchase-total");
        Button save = new Button("Complete sales return");
        save.getStyleClass().add("primary-button");
        save.setOnAction(event -> save());
        HBox bar = new HBox(16, total, save);
        bar.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(total, Priority.ALWAYS);
        return bar;
    }

    private void loadSale() {
        try {
            long value = Long.parseLong(invoiceNumber.getText().trim());
            source = returns.findSourceByInvoiceNumber(value).orElse(null);
            draftLines.clear();
            updateTotal();
            if (source == null) {
                sourceLines.clear();
                resetRefundMethods();
                sourceSummary.setText("No sale found for invoice " + value + ".");
                feedback.setText("");
                return;
            }
            sourceLines.setAll(source.lines());
            refundMethod.setItems(FXCollections.observableArrayList(
                    returns.allowedRefundMethods(source)));
            refundMethod.setValue(refundMethod.getItems().get(0));
            sourceSummary.setText("Invoice " + source.sale().invoiceNumber()
                    + " • " + source.sale().saleDate()
                    + " • original method " + source.sale().paymentMethod()
                    + " • original total NPR " + formatPaisa(source.sale().totalAmountPaisa()));
            feedback.setText("Original sale loaded. Select a line and enter a return quantity.");
        } catch (NumberFormatException exception) {
            feedback.setText("Enter a positive numeric invoice number.");
        } catch (RuntimeException exception) {
            feedback.setText(message(exception));
        }
    }

    private void resetRefundMethods() {
        refundMethod.setItems(FXCollections.observableArrayList(PaymentMethod.values()));
        refundMethod.setValue(PaymentMethod.CASH);
    }

    private void addLine() {
        SalesReturnSourceLine selected = sourceTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            feedback.setText("Select an original sale line first.");
            return;
        }
        if (draftLines.stream().anyMatch(row -> row.source().originalSaleLineId()
                .equals(selected.originalSaleLineId()))) {
            feedback.setText("That original line is already in this return draft.");
            return;
        }
        try {
            SalesReturnLineDraft draft = returns.prepareLine(
                    selected, Integer.parseInt(quantity.getText().trim()));
            draftLines.add(new ReturnRow(selected, draft));
            quantity.clear();
            updateTotal();
            feedback.setText("");
        } catch (RuntimeException exception) {
            feedback.setText(message(exception));
        }
    }

    private void save() {
        if (source == null) {
            feedback.setText("Load an original sale before recording a return.");
            return;
        }
        try {
            SalesReturn completed = returns.record(new SalesReturnDraft(
                    source.sale().id(), returnDate.getValue(), reason.getValue(),
                    refundMethod.getValue(), notes.getText(),
                    draftLines.stream().map(ReturnRow::draft).toList(), null));
            feedback.setText("Sales return #" + completed.returnNumber()
                    + " completed. Refunded NPR " + formatPaisa(completed.totalAmountPaisa())
                    + " by " + completed.refundMethod() + ". Batch stock was restored.");
            draftLines.clear();
            notes.clear();
            updateTotal();
            returns.findSourceByInvoiceNumber(source.sale().invoiceNumber()).ifPresent(updated -> {
                source = updated;
                sourceLines.setAll(updated.lines());
            });
        } catch (RuntimeException exception) {
            feedback.setText(message(exception));
        }
    }

    private void updateTotal() {
        try {
            total.setText("Refund total: NPR " + formatPaisa(returns.calculateTotal(
                    draftLines.stream().map(ReturnRow::draft).toList())));
        } catch (RuntimeException exception) {
            total.setText("Refund total: invalid");
        }
    }

    private static <T> TableColumn<T, String> column(
            String title, java.util.function.Function<T, String> value, double width) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        column.setPrefWidth(width);
        return column;
    }

    private static TableColumn<SalesReturnSourceLine, String> sourceColumn(
            String title, java.util.function.Function<SalesReturnSourceLine, String> value,
            double width) {
        return column(title, value, width);
    }

    private static TableColumn<ReturnRow, String> draftColumn(
            String title, java.util.function.Function<ReturnRow, String> value, double width) {
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
        if (exception instanceof SalesReturnValidationException validation) {
            return validation.fieldErrors().values().stream().collect(Collectors.joining(" "));
        }
        return exception.getMessage() == null ? "Could not complete sales return." : exception.getMessage();
    }

    private record ReturnRow(SalesReturnSourceLine source, SalesReturnLineDraft draft) {
    }
}
