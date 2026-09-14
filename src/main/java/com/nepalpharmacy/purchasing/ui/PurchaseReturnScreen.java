package com.nepalpharmacy.purchasing.ui;

import com.nepalpharmacy.purchasing.PurchaseReturn;
import com.nepalpharmacy.purchasing.PurchasePaymentMethod;
import com.nepalpharmacy.purchasing.PurchaseReturnDraft;
import com.nepalpharmacy.purchasing.PurchaseReturnLineDraft;
import com.nepalpharmacy.purchasing.PurchaseReturnReason;
import com.nepalpharmacy.purchasing.PurchaseReturnService;
import com.nepalpharmacy.purchasing.PurchaseReturnSource;
import com.nepalpharmacy.purchasing.PurchaseReturnSourceLine;
import com.nepalpharmacy.purchasing.PurchaseReturnValidationException;
import com.nepalpharmacy.purchasing.RecentPurchase;
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
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.stream.Collectors;

public final class PurchaseReturnScreen {

    private final PurchaseReturnService returns;
    private final VBox root = new VBox();
    private final ComboBox<RecentPurchase> recentPurchase = new ComboBox<>();
    private final Label sourceSummary = new Label("Select a recent purchase to begin.");
    private final ObservableList<PurchaseReturnSourceLine> sourceLines =
            FXCollections.observableArrayList();
    private final TableView<PurchaseReturnSourceLine> sourceTable = new TableView<>(sourceLines);
    private final TextField quantity = new TextField();
    private final ObservableList<ReturnRow> draftLines = FXCollections.observableArrayList();
    private final TableView<ReturnRow> draftTable = new TableView<>(draftLines);
    private final DatePicker returnDate = new DatePicker(LocalDate.now());
    private final ComboBox<PurchaseReturnReason> reason = new ComboBox<>();
    private final ComboBox<PurchasePaymentMethod> settlementMethod = new ComboBox<>();
    private final TextArea notes = new TextArea();
    private final Label total = new Label("Return total: NPR 0.00");
    private final Label feedback = new Label();
    private PurchaseReturnSource source;

    public PurchaseReturnScreen(PurchaseReturnService returns) {
        this.returns = returns;
        configureView();
        refreshPurchases();
    }

    public PurchaseReturnScreen(PurchaseReturnService returns, java.util.UUID purchaseId) {
        this(returns);
        recentPurchase.getItems().stream()
                .filter(item -> item.id().equals(purchaseId))
                .findFirst()
                .ifPresent(recentPurchase::setValue);
        loadPurchase(purchaseId);
    }

    public Parent view() {
        return root;
    }

    private void configureView() {
        Label title = new Label("Purchase return");
        title.getStyleClass().add("section-title");
        Label subtitle = new Label(
                "Return received items to their original supplier and exact batch.");
        subtitle.getStyleClass().add("subtitle");
        VBox heading = new VBox(4, title, subtitle);
        heading.setPadding(new Insets(24, 24, 16, 24));

        configurePurchaseConverter();
        configureSourceTable();
        configureDraftTable();
        reason.setItems(FXCollections.observableArrayList(PurchaseReturnReason.values()));
        reason.setValue(PurchaseReturnReason.DAMAGED);
        settlementMethod.setItems(FXCollections.observableArrayList(
                PurchasePaymentMethod.selectableValues()));
        settlementMethod.setPromptText("Select settlement method");
        notes.setPromptText("Optional, up to 500 characters");
        notes.setPrefRowCount(2);
        feedback.getStyleClass().add("feedback");
        feedback.setWrapText(true);
        sourceSummary.getStyleClass().add("table-hint");

        VBox content = new VBox(18, findPurchasePanel(),
                panel("Original purchase lines", sourceTable), addLinePanel(), draftPanel(),
                detailsPanel(), saveBar(), feedback);
        content.setPadding(new Insets(0, 24, 28, 24));
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("purchase-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        root.getChildren().addAll(heading, scroll);
    }

    private VBox findPurchasePanel() {
        recentPurchase.setPromptText("Choose one of the latest 25 purchases");
        recentPurchase.setMaxWidth(Double.MAX_VALUE);
        Button load = new Button("Load purchase");
        load.getStyleClass().add("secondary-button");
        load.setOnAction(event -> loadPurchase());
        HBox row = new HBox(10, label("Original purchase *"), recentPurchase, load);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(recentPurchase, Priority.ALWAYS);
        return panel("Find original purchase", new VBox(10, row, sourceSummary));
    }

    private void configurePurchaseConverter() {
        recentPurchase.setConverter(new StringConverter<>() {
            @Override
            public String toString(RecentPurchase value) {
                if (value == null) {
                    return "";
                }
                String invoice = value.invoiceNumber() == null ? "no supplier invoice"
                        : "invoice " + value.invoiceNumber();
                return value.purchaseDate() + " • " + value.supplierName() + " • " + invoice;
            }

            @Override
            public RecentPurchase fromString(String value) {
                return null;
            }
        });
    }

    private void configureSourceTable() {
        sourceTable.setPlaceholder(new Label("No purchase loaded."));
        sourceTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        sourceTable.setPrefHeight(210);
        sourceTable.getColumns().add(sourceColumn("Product", PurchaseReturnSourceLine::productName, 175));
        sourceTable.getColumns().add(sourceColumn("Batch", PurchaseReturnSourceLine::batchNumber, 85));
        sourceTable.getColumns().add(sourceColumn("Expiry", line -> line.expiryDate().toString(), 90));
        sourceTable.getColumns().add(sourceColumn("Received", line -> Integer.toString(line.receivedBaseUnits()), 70));
        sourceTable.getColumns().add(sourceColumn("Returned", line -> Long.toString(line.previouslyReturnedBaseUnits()), 70));
        sourceTable.getColumns().add(sourceColumn("Remaining", line -> Long.toString(line.remainingReturnableBaseUnits()), 70));
        sourceTable.getColumns().add(sourceColumn("In stock", line -> Long.toString(line.availableBatchQuantityBaseUnits()), 70));
        sourceTable.getColumns().add(sourceColumn("Unit NPR", line -> formatPaisa(line.unitCostPaisa()), 80));
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
        draftTable.getColumns().add(draftColumn("Unit NPR", row -> formatPaisa(row.draft().unitCostPaisa()), 90));
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
        grid.addRow(1, label("Settlement method *"), settlementMethod);
        grid.addRow(2, label("Notes"), notes);
        GridPane.setHgrow(reason, Priority.ALWAYS);
        GridPane.setHgrow(settlementMethod, Priority.ALWAYS);
        GridPane.setHgrow(notes, Priority.ALWAYS);
        return panel("Return details", grid);
    }

    private HBox saveBar() {
        total.getStyleClass().add("purchase-total");
        Button save = new Button("Complete purchase return");
        save.getStyleClass().add("primary-button");
        save.setOnAction(event -> save());
        HBox bar = new HBox(16, total, save);
        bar.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(total, Priority.ALWAYS);
        return bar;
    }

    private void refreshPurchases() {
        try {
            RecentPurchase selected = recentPurchase.getValue();
            recentPurchase.setItems(FXCollections.observableArrayList(returns.findRecent()));
            if (selected != null) {
                recentPurchase.getItems().stream().filter(item -> item.id().equals(selected.id()))
                        .findFirst().ifPresent(recentPurchase::setValue);
            }
        } catch (RuntimeException exception) {
            feedback.setText(message(exception));
        }
    }

    private void loadPurchase() {
        RecentPurchase selected = recentPurchase.getValue();
        if (selected == null) {
            feedback.setText("Select an original purchase.");
            return;
        }
        loadPurchase(selected.id());
    }

    private void loadPurchase(java.util.UUID purchaseId) {
        try {
            source = returns.findSource(purchaseId).orElse(null);
            draftLines.clear();
            updateTotal();
            if (source == null) {
                sourceLines.clear();
                sourceSummary.setText("That purchase no longer exists.");
                return;
            }
            sourceLines.setAll(source.lines());
            String invoice = source.purchase().invoiceNumber() == null
                    ? "no supplier invoice" : "invoice " + source.purchase().invoiceNumber();
            sourceSummary.setText(source.purchase().purchaseDate() + " • " + source.supplierName()
                    + " • " + invoice + " • original total NPR "
                    + formatPaisa(source.purchase().totalAmountPaisa()));
            feedback.setText("Original purchase loaded. Select a line and enter a return quantity.");
        } catch (RuntimeException exception) {
            feedback.setText(message(exception));
        }
    }

    private void addLine() {
        PurchaseReturnSourceLine selected = sourceTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            feedback.setText("Select an original purchase line first.");
            return;
        }
        if (draftLines.stream().anyMatch(row -> row.source().originalPurchaseLineId()
                .equals(selected.originalPurchaseLineId()))) {
            feedback.setText("That original line is already in this return draft.");
            return;
        }
        try {
            PurchaseReturnLineDraft draft = returns.prepareLine(
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
            feedback.setText("Load an original purchase before recording a return.");
            return;
        }
        try {
            PurchaseReturn completed = returns.record(new PurchaseReturnDraft(
                    source.purchase().id(), source.purchase().supplierId(), returnDate.getValue(),
                    reason.getValue(), settlementMethod.getValue(), notes.getText(),
                    draftLines.stream().map(ReturnRow::draft).toList(), null));
            feedback.setText("Purchase return #" + completed.returnNumber()
                    + " completed for " + source.supplierName() + ". Value NPR "
                    + formatPaisa(completed.totalAmountPaisa()) + ". Batch stock was reduced.");
            draftLines.clear();
            notes.clear();
            settlementMethod.setValue(null);
            updateTotal();
            returns.findSource(source.purchase().id()).ifPresent(updated -> {
                source = updated;
                sourceLines.setAll(updated.lines());
            });
            refreshPurchases();
        } catch (RuntimeException exception) {
            feedback.setText(message(exception));
        }
    }

    private void updateTotal() {
        try {
            total.setText("Return total: NPR " + formatPaisa(returns.calculateTotal(
                    draftLines.stream().map(ReturnRow::draft).toList())));
        } catch (RuntimeException exception) {
            total.setText("Return total: invalid");
        }
    }

    private static <T> TableColumn<T, String> column(
            String title, java.util.function.Function<T, String> value, double width) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        column.setPrefWidth(width);
        return column;
    }

    private static TableColumn<PurchaseReturnSourceLine, String> sourceColumn(
            String title, java.util.function.Function<PurchaseReturnSourceLine, String> value,
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
        if (exception instanceof PurchaseReturnValidationException validation) {
            return validation.fieldErrors().values().stream().collect(Collectors.joining(" "));
        }
        return exception.getMessage() == null
                ? "Could not complete purchase return." : exception.getMessage();
    }

    private record ReturnRow(PurchaseReturnSourceLine source, PurchaseReturnLineDraft draft) {
    }
}
