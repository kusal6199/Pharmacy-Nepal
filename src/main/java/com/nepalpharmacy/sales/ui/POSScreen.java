package com.nepalpharmacy.sales.ui;

import com.nepalpharmacy.inventory.BatchStock;
import com.nepalpharmacy.party.Customer;
import com.nepalpharmacy.party.CustomerService;
import com.nepalpharmacy.product.Product;
import com.nepalpharmacy.product.ProductService;
import com.nepalpharmacy.sales.PaymentMethod;
import com.nepalpharmacy.sales.SaleDraft;
import com.nepalpharmacy.sales.SaleLineDraft;
import com.nepalpharmacy.sales.SaleReceipt;
import com.nepalpharmacy.sales.SaleReceiptLine;
import com.nepalpharmacy.sales.SaleService;
import com.nepalpharmacy.sales.SaleValidationException;
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
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public final class POSScreen {

    private final SaleService sales;
    private final ProductService products;
    private final CustomerSelectionPane customerSelection;

    private final VBox root = new VBox();
    private final DatePicker saleDate = new DatePicker(LocalDate.now());
    private final TextField productSearch = new TextField();
    private final ComboBox<Product> product = new ComboBox<>();
    private final ComboBox<BatchStock> batch = new ComboBox<>();
    private final TextField quantity = new TextField();
    private final Label batchDetail = new Label("Search and select a product to see FEFO stock.");
    private final ObservableList<DraftLine> draftLines = FXCollections.observableArrayList();
    private final TableView<DraftLine> lineTable = new TableView<>(draftLines);
    private final Label runningTotal = new Label("Total: NPR 0.00");
    private final ComboBox<PaymentMethod> paymentMethod = new ComboBox<>();
    private final Label feedback = new Label();
    private final TextArea invoiceView = new TextArea();

    public POSScreen(
            SaleService sales,
            CustomerService customers,
            ProductService products
    ) {
        this.sales = sales;
        this.products = products;
        customerSelection = new CustomerSelectionPane(
                customers, "Select customer for Udharo", this::showSuccess, this::showError);
        configureView();
        refreshProducts("");
    }

    public Parent view() {
        return root;
    }

    private void configureView() {
        Label title = new Label("Point of sale");
        title.getStyleClass().add("section-title");
        Label subtitle = new Label(
                "Search products, use the suggested FEFO batch, and complete an immutable sale.");
        subtitle.getStyleClass().add("subtitle");
        VBox heading = new VBox(4, title, subtitle);
        heading.setPadding(new Insets(24, 24, 16, 24));

        feedback.getStyleClass().add("feedback");
        feedback.setWrapText(true);
        invoiceView.setEditable(false);
        invoiceView.setWrapText(false);
        invoiceView.setPrefRowCount(9);
        invoiceView.setPromptText("The completed invoice confirmation will appear here.");
        invoiceView.getStyleClass().add("invoice-view");

        VBox content = new VBox(18,
                createLineEntryPanel(), createLineTablePanel(), createPaymentPanel(),
                createSaveBar(), feedback, panel("Last completed invoice", invoiceView));
        content.setPadding(new Insets(0, 24, 28, 24));

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("pos-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        root.getChildren().addAll(heading, scroll);
    }

    private VBox createLineEntryPanel() {
        configureProductConverter();
        configureBatchConverter();
        productSearch.setPromptText("Type a product name");
        product.setPromptText("Select product from search results");
        product.setMaxWidth(Double.MAX_VALUE);
        batch.setPromptText("No available batch selected");
        batch.setMaxWidth(Double.MAX_VALUE);
        quantity.setPromptText("Smallest saleable base units");
        batchDetail.getStyleClass().add("table-hint");

        productSearch.textProperty().addListener((observable, previous, current) ->
                refreshProducts(current));
        product.setOnAction(event -> refreshBatches());
        batch.setOnAction(event -> updateBatchDetail());
        saleDate.valueProperty().addListener((observable, previous, current) -> refreshBatches());
        quantity.setOnAction(event -> addLine());

        GridPane fields = new GridPane();
        fields.setHgap(10);
        fields.setVgap(10);
        fields.addRow(0, label("Sale date *"), saleDate);
        fields.addRow(1, label("Product search"), productSearch,
                label("Product *"), product);
        fields.addRow(2, label("Batch / expiry *"), batch,
                label("Quantity (base units) *"), quantity);
        GridPane.setHgrow(productSearch, Priority.ALWAYS);
        GridPane.setHgrow(product, Priority.ALWAYS);
        GridPane.setHgrow(batch, Priority.ALWAYS);

        Button addLine = new Button("Add line");
        addLine.getStyleClass().add("secondary-button");
        addLine.setOnAction(event -> addLine());
        HBox actions = new HBox(12, batchDetail, addLine);
        actions.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(batchDetail, Priority.ALWAYS);

        return panel("Add sale line", new VBox(12, fields, actions));
    }

    private VBox createLineTablePanel() {
        lineTable.setPlaceholder(new Label("No sale lines yet."));
        lineTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        lineTable.setPrefHeight(220);
        lineTable.getColumns().add(lineColumn("Product", line -> line.product().name(), 220));
        lineTable.getColumns().add(lineColumn(
                "Batch", line -> line.stock().batch().batchNumber(), 100));
        lineTable.getColumns().add(lineColumn(
                "Expiry", line -> line.stock().batch().expiryDate().toString(), 100));
        lineTable.getColumns().add(lineColumn(
                "Quantity", line -> Integer.toString(line.draft().quantitySoldBaseUnits()), 80));
        lineTable.getColumns().add(lineColumn(
                "Unit NPR", line -> formatPaisa(line.draft().unitSalePricePaisa()), 90));
        lineTable.getColumns().add(lineColumn(
                "Line total", line -> formatPaisa(line.draft().lineTotalPaisa()), 100));

        Button remove = new Button("Remove selected");
        remove.getStyleClass().add("secondary-button");
        remove.disableProperty().bind(lineTable.getSelectionModel().selectedItemProperty().isNull());
        remove.setOnAction(event -> {
            draftLines.remove(lineTable.getSelectionModel().getSelectedItem());
            updateTotal();
        });
        HBox actions = new HBox(remove);
        actions.setAlignment(Pos.CENTER_RIGHT);
        return panel("Current sale", new VBox(10, lineTable, actions));
    }

    private VBox createPaymentPanel() {
        paymentMethod.setItems(FXCollections.observableArrayList(PaymentMethod.values()));
        paymentMethod.setValue(PaymentMethod.CASH);
        paymentMethod.setMaxWidth(Double.MAX_VALUE);
        paymentMethod.setOnAction(event -> updateCustomerVisibility());

        GridPane payment = new GridPane();
        payment.setHgap(10);
        payment.setVgap(10);
        payment.addRow(0, label("Payment method *"), paymentMethod);
        GridPane.setHgrow(paymentMethod, Priority.ALWAYS);

        VBox body = new VBox(12, payment, customerSelection.view());
        updateCustomerVisibility();
        return panel("Payment", body);
    }

    private HBox createSaveBar() {
        runningTotal.getStyleClass().add("purchase-total");
        Button save = new Button("Complete sale");
        save.getStyleClass().add("primary-button");
        save.setOnAction(event -> completeSale());
        HBox bar = new HBox(16, runningTotal, save);
        bar.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(runningTotal, Priority.ALWAYS);
        return bar;
    }

    private void refreshProducts(String query) {
        try {
            Product selected = product.getValue();
            product.setItems(FXCollections.observableArrayList(products.searchActiveByName(query)));
            if (selected != null) {
                product.getItems().stream()
                        .filter(item -> item.id().equals(selected.id()))
                        .findFirst()
                        .ifPresent(product::setValue);
            }
        } catch (RuntimeException exception) {
            showError("Products could not be searched. " + exception.getMessage());
        }
    }

    private void refreshBatches() {
        Product selected = product.getValue();
        LocalDate date = saleDate.getValue();
        if (selected == null || date == null) {
            batch.getItems().clear();
            batch.setValue(null);
            updateBatchDetail();
            return;
        }
        try {
            List<BatchStock> available = sales.findAvailableBatches(selected.id(), date);
            batch.setItems(FXCollections.observableArrayList(available));
            batch.setValue(available.isEmpty() ? null : available.get(0));
            updateBatchDetail();
        } catch (RuntimeException exception) {
            showError("Available batches could not be loaded. " + exception.getMessage());
        }
    }

    private void updateBatchDetail() {
        BatchStock selected = batch.getValue();
        if (selected == null) {
            batchDetail.setText("No unexpired stock is available for this product.");
        } else {
            batchDetail.setText("Available: " + selected.quantityBaseUnits()
                    + " base units · sale price NPR "
                    + formatPaisa(product.getValue().salePricePaisa()));
        }
    }

    private void addLine() {
        clearFeedbackStyle();
        try {
            Product selectedProduct = product.getValue();
            BatchStock selectedBatch = batch.getValue();
            if (selectedProduct == null) {
                throw new IllegalArgumentException("Product is required.");
            }
            if (selectedBatch == null) {
                throw new IllegalArgumentException("An available batch is required.");
            }
            SaleLineDraft draft = sales.prepareLine(
                    selectedBatch.batch().id(),
                    parseInteger(quantity.getText(), "Quantity"),
                    selectedProduct.salePricePaisa());
            draftLines.add(new DraftLine(selectedProduct, selectedBatch, draft));
            quantity.clear();
            updateTotal();
            showSuccess("Sale line added. Stock will be rechecked when the sale is completed.");
            productSearch.requestFocus();
        } catch (SaleValidationException exception) {
            showError(joinErrors(exception.fieldErrors().values()));
        } catch (IllegalArgumentException exception) {
            showError(exception.getMessage());
        }
    }

    private void updateTotal() {
        try {
            long total = sales.calculateTotal(draftLines.stream().map(DraftLine::draft).toList());
            runningTotal.setText("Total: NPR " + formatPaisa(total));
        } catch (SaleValidationException exception) {
            runningTotal.setText("Total is too large");
        }
    }

    private void updateCustomerVisibility() {
        boolean credit = paymentMethod.getValue() == PaymentMethod.CREDIT;
        customerSelection.setShown(credit);
    }

    private void completeSale() {
        clearFeedbackStyle();
        try {
            PaymentMethod method = paymentMethod.getValue();
            Customer selectedCustomer = customerSelection.selectedCustomer();
            SaleReceipt receipt = sales.record(new SaleDraft(
                    method == PaymentMethod.CREDIT && selectedCustomer != null
                            ? selectedCustomer.id() : null,
                    saleDate.getValue(),
                    method,
                    draftLines.stream().map(DraftLine::draft).toList(),
                    null));
            showInvoice(receipt, selectedCustomer);
            draftLines.clear();
            updateTotal();
            refreshBatches();
            paymentMethod.setValue(PaymentMethod.CASH);
            showSuccess("Sale completed as invoice "
                    + formatInvoiceNumber(receipt.sale().invoiceNumber()) + ".");
        } catch (SaleValidationException exception) {
            showError(joinErrors(exception.fieldErrors().values()));
        } catch (RuntimeException exception) {
            showError(exception.getMessage());
        }
    }

    private void showInvoice(SaleReceipt receipt, Customer selectedCustomer) {
        StringBuilder text = new StringBuilder();
        text.append("INVOICE ").append(formatInvoiceNumber(receipt.sale().invoiceNumber())).append('\n');
        text.append("Date: ").append(receipt.sale().saleDate()).append('\n');
        text.append("Payment: ").append(receipt.sale().paymentMethod()).append('\n');
        if (receipt.sale().customerId() != null && selectedCustomer != null) {
            text.append("Customer: ").append(selectedCustomer.name()).append('\n');
        }
        text.append('\n');
        for (SaleReceiptLine line : receipt.lines()) {
            text.append(line.productName())
                    .append(" | Batch ").append(line.batchNumber())
                    .append(" | Exp ").append(line.expiryDate()).append('\n')
                    .append("  ").append(line.quantitySoldBaseUnits())
                    .append(" × NPR ").append(formatPaisa(line.unitSalePricePaisa()))
                    .append(" = NPR ").append(formatPaisa(line.lineTotalPaisa())).append('\n');
        }
        text.append('\n').append("TOTAL: NPR ")
                .append(formatPaisa(receipt.sale().totalAmountPaisa()));
        invoiceView.setText(text.toString());
    }

    private void configureProductConverter() {
        product.setConverter(new StringConverter<>() {
            @Override
            public String toString(Product value) {
                if (value == null) {
                    return "";
                }
                String maker = value.manufacturer() == null ? "" : " — " + value.manufacturer();
                return value.name() + maker + " (" + value.unitOfSale() + ")";
            }

            @Override
            public Product fromString(String value) {
                return null;
            }
        });
    }

    private void configureBatchConverter() {
        batch.setConverter(new StringConverter<>() {
            @Override
            public String toString(BatchStock value) {
                return value == null ? "" : value.batch().batchNumber()
                        + " · expires " + value.batch().expiryDate()
                        + " · available " + value.quantityBaseUnits();
            }

            @Override
            public BatchStock fromString(String value) {
                return null;
            }
        });
    }

    private static VBox panel(String title, javafx.scene.Node content) {
        Label heading = new Label(title);
        heading.getStyleClass().add("form-title");
        VBox panel = new VBox(12, heading, content);
        panel.setPadding(new Insets(16));
        panel.getStyleClass().add("form-panel");
        return panel;
    }

    private static TableColumn<DraftLine, String> lineColumn(
            String title, java.util.function.Function<DraftLine, String> value, double width) {
        TableColumn<DraftLine, String> column = new TableColumn<>(title);
        column.setCellValueFactory(data -> new ReadOnlyStringWrapper(value.apply(data.getValue())));
        column.setPrefWidth(width);
        return column;
    }

    private void showError(String message) {
        feedback.setText(message);
        clearFeedbackStyle();
        feedback.getStyleClass().add("feedback-error");
    }

    private void showSuccess(String message) {
        feedback.setText(message);
        clearFeedbackStyle();
        feedback.getStyleClass().add("feedback-success");
    }

    private void clearFeedbackStyle() {
        feedback.getStyleClass().removeAll("feedback-error", "feedback-success");
    }

    private static int parseInteger(String text, String label) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " must be a whole number.");
        }
    }

    private static Label label(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("field-label");
        return label;
    }

    private static String formatPaisa(long paisa) {
        return BigDecimal.valueOf(paisa, 2).setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }

    private static String formatInvoiceNumber(long number) {
        return "%06d".formatted(number);
    }

    private static String joinErrors(Collection<String> errors) {
        return errors.stream().collect(Collectors.joining("\n"));
    }

    private record DraftLine(Product product, BatchStock stock, SaleLineDraft draft) {
    }
}
