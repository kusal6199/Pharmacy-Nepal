package com.nepalpharmacy.product.ui;

import com.nepalpharmacy.product.Product;
import com.nepalpharmacy.product.ProductCategory;
import com.nepalpharmacy.product.ProductDraft;
import com.nepalpharmacy.product.ProductService;
import com.nepalpharmacy.product.ProductValidationException;
import com.nepalpharmacy.product.UnitOfSale;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.stream.Collectors;

public final class ProductScreen {

    private final ProductService service;
    private final BorderPane root = new BorderPane();
    private final TableView<Product> table = new TableView<>();

    private final Label formTitle = new Label("Add product");
    private final Label feedback = new Label();
    private final TextField name = new TextField();
    private final TextField genericName = new TextField();
    private final TextField manufacturer = new TextField();
    private final ComboBox<ProductCategory> category = new ComboBox<>();
    private final ComboBox<UnitOfSale> unitOfSale = new ComboBox<>();
    private final TextField packSize = new TextField();
    private final TextField purchasePrice = new TextField("0.00");
    private final TextField salePrice = new TextField();
    private final TextField mrp = new TextField();
    private final TextField taxRate = new TextField("0");
    private final TextField reorderThreshold = new TextField("0");
    private final CheckBox active = new CheckBox("Active product");
    private final Button save = new Button("Add product");

    private Product editingProduct;

    public ProductScreen(ProductService service) {
        this.service = service;
        configureView();
        refreshTable();
    }

    public Parent view() {
        return root;
    }

    private void configureView() {
        Label title = new Label("Medicine and product master");
        title.getStyleClass().add("section-title");
        Label subtitle = new Label("Catalog information only — batch stock and expiry are recorded during purchasing.");
        subtitle.getStyleClass().add("subtitle");

        VBox heading = new VBox(4, title, subtitle);
        heading.setPadding(new Insets(24, 24, 16, 24));
        root.setTop(heading);

        VBox tablePanel = configureTable();
        VBox form = createForm();

        SplitPane split = new SplitPane(form, tablePanel);
        split.setDividerPositions(0.37);
        BorderPane.setMargin(split, new Insets(0, 24, 24, 24));
        root.setCenter(split);
    }

    private VBox createForm() {
        formTitle.getStyleClass().add("form-title");
        feedback.setWrapText(true);
        feedback.getStyleClass().add("feedback");

        category.setItems(FXCollections.observableArrayList(ProductCategory.values()));
        category.setMaxWidth(Double.MAX_VALUE);
        unitOfSale.setItems(FXCollections.observableArrayList(UnitOfSale.values()));
        unitOfSale.setMaxWidth(Double.MAX_VALUE);
        active.setSelected(true);

        name.setPromptText("Paracetamol 500mg");
        genericName.setPromptText("Paracetamol");
        manufacturer.setPromptText("Optional");
        packSize.setPromptText("Optional, e.g. 10");
        salePrice.setPromptText("Required");
        mrp.setPromptText("Optional");

        GridPane fields = new GridPane();
        fields.setHgap(10);
        fields.setVgap(11);
        fields.addRow(0, label("Name *"), name);
        fields.addRow(1, label("Generic name"), genericName);
        fields.addRow(2, label("Manufacturer"), manufacturer);
        fields.addRow(3, label("Category *"), category);
        fields.addRow(4, label("Unit of sale *"), unitOfSale);
        fields.addRow(5, label("Pack size"), packSize);
        fields.addRow(6, label("Purchase price (NPR)"), purchasePrice);
        fields.addRow(7, label("Sale price (NPR) *"), salePrice);
        fields.addRow(8, label("MRP (NPR)"), mrp);
        fields.addRow(9, label("Tax rate (%)"), taxRate);
        fields.addRow(10, label("Reorder threshold"), reorderThreshold);
        fields.addRow(11, new Label(), active);
        GridPane.setHgrow(name, Priority.ALWAYS);

        Button clear = new Button("Clear");
        clear.getStyleClass().add("secondary-button");
        clear.setOnAction(event -> clearForm());
        save.getStyleClass().add("primary-button");
        save.setDefaultButton(true);
        save.setOnAction(event -> saveProduct());
        HBox actions = new HBox(10, save, clear);
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox formBody = new VBox(14, formTitle, feedback, fields, actions);
        formBody.setPadding(new Insets(18));
        formBody.getStyleClass().add("form-panel");

        ScrollPane scroll = new ScrollPane(formBody);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("form-scroll");

        VBox holder = new VBox(scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        return holder;
    }

    private VBox configureTable() {
        table.setPlaceholder(new Label("No products yet. Add the first product using the form."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        table.getColumns().add(column("Name", product -> product.name(), 180));
        table.getColumns().add(column("Generic", product -> optional(product.genericName()), 130));
        table.getColumns().add(column("Manufacturer", product -> optional(product.manufacturer()), 140));
        table.getColumns().add(column("Category", product -> product.category().toString(), 90));
        table.getColumns().add(column("Unit", product -> product.unitOfSale().toString(), 75));
        table.getColumns().add(column("Sale NPR", product -> formatPaisa(product.salePricePaisa()), 90));
        table.getColumns().add(column("Tax", product -> formatTax(product.taxRateBasisPoints()), 65));
        table.getColumns().add(column("Status", product -> product.active() ? "Active" : "Inactive", 70));

        Button edit = new Button("Edit selected");
        edit.getStyleClass().add("secondary-button");
        edit.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        edit.setOnAction(event -> editSelected());

        Label hint = new Label("Select a row and choose Edit selected, or double-click a row.");
        hint.getStyleClass().add("table-hint");
        HBox tableTools = new HBox(12, hint, edit);
        tableTools.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(hint, Priority.ALWAYS);

        VBox tablePanel = new VBox(10, tableTools, table);
        tablePanel.setPadding(new Insets(4, 0, 0, 14));
        VBox.setVgrow(table, Priority.ALWAYS);

        table.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && table.getSelectionModel().getSelectedItem() != null) {
                editSelected();
            }
        });

        return tablePanel;
    }

    private TableColumn<Product, String> column(
            String title,
            java.util.function.Function<Product, String> value,
            double width
    ) {
        TableColumn<Product, String> column = new TableColumn<>(title);
        column.setCellValueFactory(data -> new ReadOnlyStringWrapper(value.apply(data.getValue())));
        column.setPrefWidth(width);
        return column;
    }

    private void saveProduct() {
        clearFeedbackStyle();
        try {
            ProductDraft draft = readDraft();
            if (editingProduct == null) {
                Product created = service.create(draft);
                refreshTable();
                clearForm();
                showSuccess("Added “" + created.name() + "”.");
            } else {
                Product updated = service.update(editingProduct.id(), draft);
                refreshTable();
                clearForm();
                showSuccess("Updated “" + updated.name() + "”.");
            }
        } catch (ProductValidationException exception) {
            showError(exception.fieldErrors().values().stream().collect(Collectors.joining("\n")));
        } catch (IllegalArgumentException exception) {
            showError(exception.getMessage());
        } catch (RuntimeException exception) {
            showError("The product could not be saved. " + exception.getMessage());
        }
    }

    private ProductDraft readDraft() {
        return new ProductDraft(
                name.getText(),
                genericName.getText(),
                manufacturer.getText(),
                category.getValue(),
                unitOfSale.getValue(),
                parseOptionalInteger(packSize.getText(), "Pack size"),
                parsePaisa(purchasePrice.getText(), "Purchase price", false),
                parsePaisa(salePrice.getText(), "Sale price", false),
                parseOptionalPaisa(mrp.getText(), "MRP"),
                parseBasisPoints(taxRate.getText()),
                parseInteger(reorderThreshold.getText(), "Reorder threshold"),
                active.isSelected()
        );
    }

    private void editSelected() {
        Product product = table.getSelectionModel().getSelectedItem();
        if (product == null) {
            return;
        }

        editingProduct = product;
        formTitle.setText("Edit product");
        save.setText("Update product");
        name.setText(product.name());
        genericName.setText(optional(product.genericName()));
        manufacturer.setText(optional(product.manufacturer()));
        category.setValue(product.category());
        unitOfSale.setValue(product.unitOfSale());
        packSize.setText(product.packSize() == null ? "" : product.packSize().toString());
        purchasePrice.setText(formatPaisa(product.purchasePricePaisa()));
        salePrice.setText(formatPaisa(product.salePricePaisa()));
        mrp.setText(product.mrpPaisa() == null ? "" : formatPaisa(product.mrpPaisa()));
        taxRate.setText(formatBasisPointsForInput(product.taxRateBasisPoints()));
        reorderThreshold.setText(Integer.toString(product.reorderThresholdBaseUnits()));
        active.setSelected(product.active());
        feedback.setText("Editing “" + product.name() + "”.");
        clearFeedbackStyle();
        name.requestFocus();
    }

    private void clearForm() {
        editingProduct = null;
        feedback.setText("");
        clearFeedbackStyle();
        formTitle.setText("Add product");
        save.setText("Add product");
        name.clear();
        genericName.clear();
        manufacturer.clear();
        category.setValue(null);
        unitOfSale.setValue(null);
        packSize.clear();
        purchasePrice.setText("0.00");
        salePrice.clear();
        mrp.clear();
        taxRate.setText("0");
        reorderThreshold.setText("0");
        active.setSelected(true);
        table.getSelectionModel().clearSelection();
        name.requestFocus();
    }

    private void refreshTable() {
        try {
            table.setItems(FXCollections.observableArrayList(service.findAll()));
        } catch (RuntimeException exception) {
            showError("Products could not be loaded. " + exception.getMessage());
        }
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

    private static String optional(String value) {
        return value == null ? "" : value;
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

    private static Integer parseOptionalInteger(String text, String label) {
        return text == null || text.isBlank() ? null : parseInteger(text, label);
    }

    private static long parsePaisa(String text, String label, boolean optional) {
        if (text == null || text.isBlank()) {
            if (optional) {
                return 0;
            }
            throw new IllegalArgumentException(label + " is required.");
        }
        try {
            return new BigDecimal(text.trim()).movePointRight(2).longValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new IllegalArgumentException(label + " must be a valid amount with at most 2 decimal places.");
        }
    }

    private static Long parseOptionalPaisa(String text, String label) {
        return text == null || text.isBlank() ? null : parsePaisa(text, label, true);
    }

    private static int parseBasisPoints(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Tax rate is required; use 0 for tax-exempt products.");
        }
        try {
            return new BigDecimal(text.trim()).movePointRight(2).intValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new IllegalArgumentException("Tax rate must be a percentage with at most 2 decimal places.");
        }
    }

    private static String formatPaisa(long paisa) {
        return BigDecimal.valueOf(paisa, 2).setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }

    private static String formatTax(int basisPoints) {
        return formatBasisPointsForInput(basisPoints) + "%";
    }

    private static String formatBasisPointsForInput(int basisPoints) {
        return BigDecimal.valueOf(basisPoints, 2).stripTrailingZeros().toPlainString();
    }
}
