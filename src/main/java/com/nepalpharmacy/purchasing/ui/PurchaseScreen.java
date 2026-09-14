package com.nepalpharmacy.purchasing.ui;

import com.nepalpharmacy.party.Supplier;
import com.nepalpharmacy.party.SupplierDraft;
import com.nepalpharmacy.party.SupplierService;
import com.nepalpharmacy.party.SupplierValidationException;
import com.nepalpharmacy.product.Product;
import com.nepalpharmacy.product.ProductService;
import com.nepalpharmacy.purchasing.Purchase;
import com.nepalpharmacy.purchasing.PurchaseDraft;
import com.nepalpharmacy.purchasing.PurchaseLineDraft;
import com.nepalpharmacy.purchasing.PurchasePaymentMethod;
import com.nepalpharmacy.purchasing.PurchaseService;
import com.nepalpharmacy.purchasing.PurchaseValidationException;
import com.nepalpharmacy.purchasing.PurchaseValidator;
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
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public final class PurchaseScreen {

    private final ProductService products;
    private final SupplierService suppliers;
    private final PurchaseService purchases;

    private final VBox root = new VBox();
    private final ComboBox<Supplier> supplier = new ComboBox<>();
    private final DatePicker purchaseDate = new DatePicker(LocalDate.now());
    private final TextField invoiceNumber = new TextField();
    private final ComboBox<PurchasePaymentMethod> paymentMethod = new ComboBox<>();
    private final ComboBox<Product> product = new ComboBox<>();
    private final TextField batchNumber = new TextField();
    private final DatePicker expiryDate = new DatePicker();
    private final DatePicker manufacturingDate = new DatePicker();
    private final TextField quantity = new TextField();
    private final TextField unitPrice = new TextField();
    private final Label feedback = new Label();
    private final Label total = new Label("Total: NPR 0.00");
    private final ObservableList<DraftLine> draftLines = FXCollections.observableArrayList();
    private final TableView<DraftLine> lineTable = new TableView<>(draftLines);
    private final TableView<RecentPurchase> recentTable = new TableView<>();

    private final TextField supplierName = new TextField();
    private final TextField supplierPhone = new TextField();
    private final TextField supplierAddress = new TextField();
    private final TextField supplierPan = new TextField();

    public PurchaseScreen(
            ProductService products,
            SupplierService suppliers,
            PurchaseService purchases
    ) {
        this.products = products;
        this.suppliers = suppliers;
        this.purchases = purchases;
        configureView();
        refreshReferenceData();
        refreshRecentPurchases();
    }

    public Parent view() {
        return root;
    }

    private void configureView() {
        Label title = new Label("Purchase entry");
        title.getStyleClass().add("section-title");
        Label subtitle = new Label(
                "Receive stock by supplier, product batch, expiry, and smallest saleable base unit.");
        subtitle.getStyleClass().add("subtitle");

        VBox heading = new VBox(4, title, subtitle);
        heading.setPadding(new Insets(24, 24, 16, 24));

        VBox content = new VBox(18,
                createPurchaseHeader(), createSupplierForm(), createLineEditor(),
                createDraftLinesPanel(), createSaveBar(), createRecentPanel());
        content.setPadding(new Insets(0, 24, 28, 24));

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("purchase-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        feedback.getStyleClass().add("feedback");
        feedback.setWrapText(true);
        root.getChildren().addAll(heading, scroll);
    }

    private VBox createPurchaseHeader() {
        configureSupplierConverter();
        supplier.setPromptText("Select supplier");
        supplier.setMaxWidth(Double.MAX_VALUE);
        invoiceNumber.setPromptText("Optional");
        paymentMethod.setItems(FXCollections.observableArrayList(
                PurchasePaymentMethod.selectableValues()));
        paymentMethod.setPromptText("Select settlement method");
        paymentMethod.setMaxWidth(Double.MAX_VALUE);

        GridPane fields = new GridPane();
        fields.setHgap(12);
        fields.setVgap(10);
        fields.addRow(0, label("Supplier *"), supplier, label("Purchase date *"), purchaseDate);
        fields.addRow(1, label("Invoice number"), invoiceNumber,
                label("Payment method *"), paymentMethod);
        GridPane.setHgrow(supplier, Priority.ALWAYS);
        GridPane.setHgrow(invoiceNumber, Priority.ALWAYS);
        GridPane.setHgrow(paymentMethod, Priority.ALWAYS);

        VBox panel = panel("Purchase details", fields);
        panel.getChildren().add(feedback);
        return panel;
    }

    private TitledPane createSupplierForm() {
        supplierName.setPromptText("Required");
        supplierPhone.setPromptText("Optional");
        supplierAddress.setPromptText("Optional");
        supplierPan.setPromptText("Optional");

        GridPane fields = new GridPane();
        fields.setHgap(10);
        fields.setVgap(10);
        fields.addRow(0, label("Name *"), supplierName, label("Phone"), supplierPhone);
        fields.addRow(1, label("Address"), supplierAddress, label("PAN"), supplierPan);
        GridPane.setHgrow(supplierName, Priority.ALWAYS);
        GridPane.setHgrow(supplierAddress, Priority.ALWAYS);

        Button add = new Button("Add and select supplier");
        add.getStyleClass().add("secondary-button");
        add.setOnAction(event -> addSupplier());
        HBox actions = new HBox(add);
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox body = new VBox(12, fields, actions);
        body.setPadding(new Insets(12));
        TitledPane pane = new TitledPane("Need a new supplier? Add one here", body);
        pane.setExpanded(false);
        return pane;
    }

    private VBox createLineEditor() {
        configureProductConverter();
        product.setPromptText("Select active product");
        product.setMaxWidth(Double.MAX_VALUE);
        product.setOnAction(event -> {
            Product selected = product.getValue();
            if (selected != null) {
                unitPrice.setText(formatPaisa(selected.purchasePricePaisa()));
            }
        });
        batchNumber.setPromptText("Required");
        quantity.setPromptText("Base units, e.g. tablets");
        unitPrice.setPromptText("Per base unit");

        GridPane fields = new GridPane();
        fields.setHgap(10);
        fields.setVgap(10);
        fields.addRow(0, label("Product *"), product, label("Batch number *"), batchNumber);
        fields.addRow(1, label("Expiry date *"), expiryDate,
                label("Manufacturing date"), manufacturingDate);
        fields.addRow(2, label("Quantity (base units) *"), quantity,
                label("Unit purchase price (NPR) *"), unitPrice);
        GridPane.setHgrow(product, Priority.ALWAYS);
        GridPane.setHgrow(batchNumber, Priority.ALWAYS);

        Button addLine = new Button("Add line");
        addLine.getStyleClass().add("secondary-button");
        addLine.setOnAction(event -> addLine());
        HBox actions = new HBox(addLine);
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox body = new VBox(12, fields, actions);
        return panel("Add purchase line", body);
    }

    private VBox createDraftLinesPanel() {
        lineTable.setPlaceholder(new Label("No purchase lines yet."));
        lineTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        lineTable.setPrefHeight(210);
        lineTable.getColumns().add(lineColumn("Product", line -> line.product().name(), 210));
        lineTable.getColumns().add(lineColumn("Batch", line -> line.draft().batchNumber(), 110));
        lineTable.getColumns().add(lineColumn("Expiry", line -> line.draft().expiryDate().toString(), 100));
        lineTable.getColumns().add(lineColumn(
                "Quantity", line -> Integer.toString(line.draft().quantityReceivedBaseUnits()), 85));
        lineTable.getColumns().add(lineColumn(
                "Unit NPR", line -> formatPaisa(line.draft().unitPurchasePricePaisa()), 90));
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

        return panel("Draft lines", new VBox(10, lineTable, actions));
    }

    private HBox createSaveBar() {
        total.getStyleClass().add("purchase-total");
        Button save = new Button("Save purchase and receive stock");
        save.getStyleClass().add("primary-button");
        save.setOnAction(event -> savePurchase());
        HBox bar = new HBox(16, total, save);
        bar.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(total, Priority.ALWAYS);
        return bar;
    }

    private VBox createRecentPanel() {
        recentTable.setPlaceholder(new Label("No purchases have been recorded."));
        recentTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        recentTable.setPrefHeight(220);
        recentTable.getColumns().add(recentColumn(
                "Date", purchase -> purchase.purchaseDate().toString(), 110));
        recentTable.getColumns().add(recentColumn(
                "Supplier", RecentPurchase::supplierName, 220));
        recentTable.getColumns().add(recentColumn(
                "Invoice", purchase -> optional(purchase.invoiceNumber()), 140));
        recentTable.getColumns().add(recentColumn(
                "Payment", purchase -> purchase.paymentMethod().displayName(), 130));
        recentTable.getColumns().add(recentColumn(
                "Total NPR", purchase -> formatPaisa(purchase.totalAmountPaisa()), 120));
        return panel("Recent purchases", recentTable);
    }

    private void addSupplier() {
        clearFeedbackStyle();
        try {
            Supplier created = suppliers.create(new SupplierDraft(
                    supplierName.getText(), supplierPhone.getText(), supplierAddress.getText(),
                    supplierPan.getText(), true));
            refreshSuppliers();
            supplier.setValue(created);
            supplierName.clear();
            supplierPhone.clear();
            supplierAddress.clear();
            supplierPan.clear();
            showSuccess("Added and selected supplier “" + created.name() + "”.");
        } catch (SupplierValidationException exception) {
            showError(joinErrors(exception.fieldErrors().values()));
        } catch (RuntimeException exception) {
            showError("The supplier could not be added. " + exception.getMessage());
        }
    }

    private void addLine() {
        clearFeedbackStyle();
        try {
            Product selectedProduct = product.getValue();
            if (selectedProduct == null) {
                throw new IllegalArgumentException("Product is required.");
            }
            PurchaseLineDraft draft = new PurchaseLineDraft(
                    selectedProduct.id(),
                    batchNumber.getText(),
                    expiryDate.getValue(),
                    manufacturingDate.getValue(),
                    parsePositiveInteger(quantity.getText(), "Quantity"),
                    parsePaisa(unitPrice.getText(), "Unit purchase price"))
                    .normalized();
            PurchaseDraft singleLine = new PurchaseDraft(
                    new UUID(0, 0), purchaseDate.getValue(), null,
                    PurchasePaymentMethod.CASH, List.of(draft), null);
            PurchaseValidator.validate(singleLine);

            draftLines.add(new DraftLine(selectedProduct, draft));
            clearLineEditor();
            updateTotal();
            showSuccess("Purchase line added. Save the purchase when all lines are ready.");
        } catch (PurchaseValidationException exception) {
            showError(joinErrors(exception.fieldErrors().values()));
        } catch (IllegalArgumentException exception) {
            showError(exception.getMessage());
        }
    }

    private void savePurchase() {
        clearFeedbackStyle();
        try {
            Purchase saved = purchases.record(new PurchaseDraft(
                    supplier.getValue() == null ? null : supplier.getValue().id(),
                    purchaseDate.getValue(),
                    invoiceNumber.getText(),
                    paymentMethod.getValue(),
                    draftLines.stream().map(DraftLine::draft).toList(),
                    null));
            String supplierName = supplier.getValue().name();
            invoiceNumber.clear();
            purchaseDate.setValue(LocalDate.now());
            paymentMethod.setValue(null);
            draftLines.clear();
            updateTotal();
            refreshRecentPurchases();
            showSuccess("Saved purchase from “" + supplierName + "” for NPR "
                    + formatPaisa(saved.totalAmountPaisa()) + ". Stock is now available by batch.");
        } catch (PurchaseValidationException exception) {
            showError(joinErrors(exception.fieldErrors().values()));
        } catch (RuntimeException exception) {
            showError(exception.getMessage());
        }
    }

    private void refreshReferenceData() {
        try {
            refreshSuppliers();
            product.setItems(FXCollections.observableArrayList(
                    products.findAll().stream().filter(Product::active).toList()));
        } catch (RuntimeException exception) {
            showError("Products and suppliers could not be loaded. " + exception.getMessage());
        }
    }

    private void refreshSuppliers() {
        Supplier selected = supplier.getValue();
        supplier.setItems(FXCollections.observableArrayList(suppliers.findAllActive()));
        if (selected != null) {
            supplier.getItems().stream()
                    .filter(item -> item.id().equals(selected.id()))
                    .findFirst()
                    .ifPresent(supplier::setValue);
        }
    }

    private void refreshRecentPurchases() {
        try {
            recentTable.setItems(FXCollections.observableArrayList(purchases.findRecent()));
        } catch (RuntimeException exception) {
            showError("Recent purchases could not be loaded. " + exception.getMessage());
        }
    }

    private void clearLineEditor() {
        product.setValue(null);
        batchNumber.clear();
        expiryDate.setValue(null);
        manufacturingDate.setValue(null);
        quantity.clear();
        unitPrice.clear();
    }

    private void updateTotal() {
        try {
            long paisa = 0;
            for (DraftLine line : draftLines) {
                paisa = Math.addExact(paisa, line.draft().lineTotalPaisa());
            }
            total.setText("Total: NPR " + formatPaisa(paisa));
        } catch (ArithmeticException exception) {
            total.setText("Total is too large");
        }
    }

    private void configureSupplierConverter() {
        supplier.setConverter(new StringConverter<>() {
            @Override
            public String toString(Supplier value) {
                return value == null ? "" : value.name();
            }

            @Override
            public Supplier fromString(String value) {
                return null;
            }
        });
    }

    private void configureProductConverter() {
        product.setConverter(new StringConverter<>() {
            @Override
            public String toString(Product value) {
                if (value == null) {
                    return "";
                }
                String maker = value.manufacturer() == null ? "" : " — " + value.manufacturer();
                return value.name() + maker + " (base unit: " + value.unitOfSale() + ")";
            }

            @Override
            public Product fromString(String value) {
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

    private static TableColumn<RecentPurchase, String> recentColumn(
            String title, java.util.function.Function<RecentPurchase, String> value, double width) {
        TableColumn<RecentPurchase, String> column = new TableColumn<>(title);
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

    private static Label label(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("field-label");
        return label;
    }

    private static int parsePositiveInteger(String text, String label) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " must be a whole number.");
        }
    }

    private static long parsePaisa(String text, String label) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        try {
            return new BigDecimal(text.trim()).movePointRight(2).longValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new IllegalArgumentException(
                    label + " must be a valid amount with at most 2 decimal places.");
        }
    }

    private static String formatPaisa(long paisa) {
        return BigDecimal.valueOf(paisa, 2).setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }

    private static String joinErrors(java.util.Collection<String> errors) {
        return errors.stream().collect(Collectors.joining("\n"));
    }

    private static String optional(String value) {
        return value == null ? "" : value;
    }

    private record DraftLine(Product product, PurchaseLineDraft draft) {
    }
}
