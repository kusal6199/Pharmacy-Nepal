package com.nepalpharmacy.credit.ui;

import com.nepalpharmacy.credit.AccountBalanceFilter;
import com.nepalpharmacy.credit.AccountBalancePresentation;
import com.nepalpharmacy.credit.AccountEntryDraft;
import com.nepalpharmacy.credit.AccountEntryType;
import com.nepalpharmacy.credit.AccountLedgerEntry;
import com.nepalpharmacy.credit.AccountValidationException;
import com.nepalpharmacy.credit.BoundedAccountResult;
import com.nepalpharmacy.credit.CustomerAccountDetail;
import com.nepalpharmacy.credit.CustomerAccountService;
import com.nepalpharmacy.credit.CustomerAccountSummary;
import com.nepalpharmacy.credit.SupplierAccountDetail;
import com.nepalpharmacy.credit.SupplierAccountService;
import com.nepalpharmacy.credit.SupplierAccountSummary;
import com.nepalpharmacy.sales.PaymentMethod;
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
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
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
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class CreditAccountsScreen {
    private final CustomerAccountService customers;
    private final SupplierAccountService suppliers;
    private final VBox root = new VBox();

    private final TextField customerSearch = new TextField();
    private final ComboBox<AccountBalanceFilter> customerFilter = filterBox();
    private final ObservableList<CustomerAccountSummary> customerRows = FXCollections.observableArrayList();
    private final TableView<CustomerAccountSummary> customerTable = new TableView<>(customerRows);
    private final Label customerListFeedback = new Label();
    private final Label customerDetailSummary = new Label("Select a customer account.");
    private final ObservableList<AccountLedgerEntry> customerLedger = FXCollections.observableArrayList();
    private final TableView<AccountLedgerEntry> customerLedgerTable = ledgerTable(customerLedger, true);
    private final EntryForm customerEntry = new EntryForm(AccountEntryType.PAYMENT_RECEIVED);
    private UUID selectedCustomerId;

    private final TextField supplierSearch = new TextField();
    private final ComboBox<AccountBalanceFilter> supplierFilter = filterBox();
    private final ObservableList<SupplierAccountSummary> supplierRows = FXCollections.observableArrayList();
    private final TableView<SupplierAccountSummary> supplierTable = new TableView<>(supplierRows);
    private final Label supplierListFeedback = new Label();
    private final Label supplierDetailSummary = new Label("Select a supplier account.");
    private final ObservableList<AccountLedgerEntry> supplierLedger = FXCollections.observableArrayList();
    private final TableView<AccountLedgerEntry> supplierLedgerTable = ledgerTable(supplierLedger, false);
    private final EntryForm supplierEntry = new EntryForm(AccountEntryType.PAYMENT_MADE);
    private UUID selectedSupplierId;

    public CreditAccountsScreen(
            CustomerAccountService customers, SupplierAccountService suppliers) {
        this.customers = customers;
        this.suppliers = suppliers;
        configureView();
        refreshCustomers();
        refreshSuppliers();
    }

    public Parent view() {
        return root;
    }

    private void configureView() {
        Label title = new Label("Udharo / Credit accounts");
        title.getStyleClass().add("section-title");
        Label subtitle = new Label(
                "Review derived customer receivables and supplier payables, then record settlements.");
        subtitle.getStyleClass().add("subtitle");
        VBox heading = new VBox(4, title, subtitle);
        heading.setPadding(new Insets(24, 24, 12, 24));

        Tab customerTab = new Tab("Customer receivables", customerContent());
        Tab supplierTab = new Tab("Supplier payables", supplierContent());
        customerTab.setClosable(false);
        supplierTab.setClosable(false);
        TabPane tabs = new TabPane(customerTab, supplierTab);
        VBox.setVgrow(tabs, Priority.ALWAYS);
        root.getChildren().addAll(heading, tabs);
    }

    private Parent customerContent() {
        configureCustomerTable();
        customerSearch.setPromptText("Customer name contains");
        customerSearch.setOnAction(event -> refreshCustomers());
        Button refresh = button("Refresh", this::refreshCustomers);
        HBox filters = new HBox(10, label("Name"), customerSearch,
                label("Balance"), customerFilter, refresh);
        filters.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(customerSearch, Priority.ALWAYS);
        customerFilter.setOnAction(event -> refreshCustomers());
        customerListFeedback.getStyleClass().add("table-hint");

        customerEntry.save.setOnAction(event -> saveCustomerEntry());
        VBox content = new VBox(16,
                panel("Find customer account", new VBox(8, filters, customerListFeedback)),
                panel("Customer accounts", customerTable),
                panel("Customer ledger", new VBox(8, customerDetailSummary, customerLedgerTable)),
                panel("Record opening balance or payment received", customerEntry.view));
        return scroll(content);
    }

    private Parent supplierContent() {
        configureSupplierTable();
        supplierSearch.setPromptText("Supplier name contains");
        supplierSearch.setOnAction(event -> refreshSuppliers());
        Button refresh = button("Refresh", this::refreshSuppliers);
        HBox filters = new HBox(10, label("Name"), supplierSearch,
                label("Balance"), supplierFilter, refresh);
        filters.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(supplierSearch, Priority.ALWAYS);
        supplierFilter.setOnAction(event -> refreshSuppliers());
        supplierListFeedback.getStyleClass().add("table-hint");

        supplierEntry.save.setOnAction(event -> saveSupplierEntry());
        VBox content = new VBox(16,
                panel("Find supplier account", new VBox(8, filters, supplierListFeedback)),
                panel("Supplier accounts", supplierTable),
                panel("Supplier ledger", new VBox(8, supplierDetailSummary, supplierLedgerTable)),
                panel("Record opening balance or payment made", supplierEntry.view));
        return scroll(content);
    }

    private void configureCustomerTable() {
        customerTable.setPlaceholder(new Label("No customer accounts match this filter."));
        customerTable.setPrefHeight(210);
        customerTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        customerTable.getColumns().add(column("Customer", CustomerAccountSummary::customerName, 190));
        customerTable.getColumns().add(column("Phone", row -> optional(row.phone()), 110));
        customerTable.getColumns().add(column("Status", row -> row.active() ? "Active" : "Inactive", 75));
        customerTable.getColumns().add(column("Balance", row -> customerBalance(row.balancePaisa()), 170));
        customerTable.getColumns().add(column("Last activity",
                row -> row.lastActivityDate() == null ? "—" : row.lastActivityDate().toString(), 100));
        customerTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, previous, selected) -> loadCustomer(selected));
    }

    private void configureSupplierTable() {
        supplierTable.setPlaceholder(new Label("No supplier accounts match this filter."));
        supplierTable.setPrefHeight(210);
        supplierTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        supplierTable.getColumns().add(column("Supplier", SupplierAccountSummary::supplierName, 190));
        supplierTable.getColumns().add(column("Phone", row -> optional(row.phone()), 110));
        supplierTable.getColumns().add(column("Status", row -> row.active() ? "Active" : "Inactive", 75));
        supplierTable.getColumns().add(column("Balance", row -> supplierBalance(row.balancePaisa()), 190));
        supplierTable.getColumns().add(column("Last activity",
                row -> row.lastActivityDate() == null ? "—" : row.lastActivityDate().toString(), 100));
        supplierTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, previous, selected) -> loadSupplier(selected));
    }

    private void refreshCustomers() {
        try {
            selectedCustomerId = null;
            customerLedger.clear();
            customerDetailSummary.setText("Select a customer account.");
            BoundedAccountResult<CustomerAccountSummary> result = customers.search(
                    customerSearch.getText(), customerFilter.getValue());
            customerRows.setAll(result.accounts());
            customerListFeedback.setText(result.truncated()
                    ? "Showing the first 150 accounts. Narrow the name search to see more."
                    : result.accounts().size() + " account(s).");
        } catch (RuntimeException exception) {
            customerListFeedback.setText(message(exception));
        }
    }

    private void refreshSuppliers() {
        try {
            selectedSupplierId = null;
            supplierLedger.clear();
            supplierDetailSummary.setText("Select a supplier account.");
            BoundedAccountResult<SupplierAccountSummary> result = suppliers.search(
                    supplierSearch.getText(), supplierFilter.getValue());
            supplierRows.setAll(result.accounts());
            supplierListFeedback.setText(result.truncated()
                    ? "Showing the first 150 accounts. Narrow the name search to see more."
                    : result.accounts().size() + " account(s).");
        } catch (RuntimeException exception) {
            supplierListFeedback.setText(message(exception));
        }
    }

    private void loadCustomer(CustomerAccountSummary selected) {
        selectedCustomerId = selected == null ? null : selected.customerId();
        customerLedger.clear();
        if (selected == null) {
            customerDetailSummary.setText("Select a customer account.");
            return;
        }
        try {
            CustomerAccountDetail detail = customers.findDetail(selected.customerId()).orElse(null);
            if (detail == null) {
                customerDetailSummary.setText("That customer account no longer exists.");
                return;
            }
            customerLedger.setAll(detail.entries());
            customerDetailSummary.setText(detail.summary().customerName() + " • "
                    + customerBalance(detail.summary().balancePaisa())
                    + (detail.summary().active() ? "" : " • inactive"));
        } catch (RuntimeException exception) {
            customerDetailSummary.setText(message(exception));
        }
    }

    private void loadSupplier(SupplierAccountSummary selected) {
        selectedSupplierId = selected == null ? null : selected.supplierId();
        supplierLedger.clear();
        if (selected == null) {
            supplierDetailSummary.setText("Select a supplier account.");
            return;
        }
        try {
            SupplierAccountDetail detail = suppliers.findDetail(selected.supplierId()).orElse(null);
            if (detail == null) {
                supplierDetailSummary.setText("That supplier account no longer exists.");
                return;
            }
            supplierLedger.setAll(detail.entries());
            supplierDetailSummary.setText(detail.summary().supplierName() + " • "
                    + supplierBalance(detail.summary().balancePaisa())
                    + (detail.summary().active() ? "" : " • inactive"));
        } catch (RuntimeException exception) {
            supplierDetailSummary.setText(message(exception));
        }
    }

    private void saveCustomerEntry() {
        if (selectedCustomerId == null) {
            customerEntry.feedback.setText("Select a customer account first.");
            return;
        }
        try {
            customers.record(customerEntry.draft(selectedCustomerId));
            customerEntry.clear();
            customerEntry.feedback.setText("Customer account entry recorded.");
            UUID id = selectedCustomerId;
            refreshCustomers();
            customerRows.stream().filter(row -> row.customerId().equals(id)).findFirst()
                    .ifPresent(customerTable.getSelectionModel()::select);
        } catch (RuntimeException exception) {
            customerEntry.feedback.setText(message(exception));
        }
    }

    private void saveSupplierEntry() {
        if (selectedSupplierId == null) {
            supplierEntry.feedback.setText("Select a supplier account first.");
            return;
        }
        try {
            suppliers.record(supplierEntry.draft(selectedSupplierId));
            supplierEntry.clear();
            supplierEntry.feedback.setText("Supplier account entry recorded.");
            UUID id = selectedSupplierId;
            refreshSuppliers();
            supplierRows.stream().filter(row -> row.supplierId().equals(id)).findFirst()
                    .ifPresent(supplierTable.getSelectionModel()::select);
        } catch (RuntimeException exception) {
            supplierEntry.feedback.setText(message(exception));
        }
    }

    private static ComboBox<AccountBalanceFilter> filterBox() {
        ComboBox<AccountBalanceFilter> box = new ComboBox<>(
                FXCollections.observableArrayList(AccountBalanceFilter.values()));
        box.setValue(AccountBalanceFilter.POSITIVE);
        return box;
    }

    private static TableView<AccountLedgerEntry> ledgerTable(
            ObservableList<AccountLedgerEntry> rows, boolean customer) {
        TableView<AccountLedgerEntry> table = new TableView<>(rows);
        table.setPlaceholder(new Label("No account activity yet."));
        table.setPrefHeight(245);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getColumns().add(column("Date", row -> row.businessDate().toString(), 90));
        table.getColumns().add(column("Activity", AccountLedgerEntry::activity, 130));
        table.getColumns().add(column("Reference", row -> optional(row.reference()), 150));
        table.getColumns().add(column(customer ? "Charge" : "Increase",
                row -> amountOrDash(row.increasePaisa()), 90));
        table.getColumns().add(column(customer ? "Credit / paid" : "Paid / credit",
                row -> amountOrDash(row.decreasePaisa()), 90));
        table.getColumns().add(column("Running balance",
                row -> customer ? customerBalance(row.runningBalancePaisa())
                        : supplierBalance(row.runningBalancePaisa()), 180));
        return table;
    }

    private static ScrollPane scroll(VBox content) {
        content.setPadding(new Insets(12, 24, 28, 24));
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return scroll;
    }

    private static VBox panel(String title, javafx.scene.Node content) {
        Label heading = new Label(title);
        heading.getStyleClass().add("panel-title");
        VBox panel = new VBox(10, heading, content);
        panel.getStyleClass().add("form-card");
        panel.setPadding(new Insets(16));
        return panel;
    }

    private static Button button(String text, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("secondary-button");
        button.setOnAction(event -> action.run());
        return button;
    }

    private static Label label(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("field-label");
        return label;
    }

    private static <T> TableColumn<T, String> column(
            String title, Function<T, String> value, double width) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        column.setPrefWidth(width);
        return column;
    }

    private static String customerBalance(long paisa) {
        return AccountBalancePresentation.customer(paisa);
    }

    private static String supplierBalance(long paisa) {
        return AccountBalancePresentation.supplier(paisa);
    }

    private static String amountOrDash(long paisa) {
        return paisa == 0 ? "—" : "NPR " + formatPaisa(paisa);
    }

    private static String formatPaisa(long paisa) {
        return BigDecimal.valueOf(paisa, 2).setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }

    private static String optional(String value) {
        return value == null ? "—" : value;
    }

    private static String message(RuntimeException exception) {
        if (exception instanceof AccountValidationException validation) {
            return validation.fieldErrors().values().stream().collect(Collectors.joining(" "));
        }
        return exception.getMessage() == null ? "Account operation failed." : exception.getMessage();
    }

    private static final class EntryForm {
        private final AccountEntryType paymentType;
        private final ComboBox<AccountEntryType> type = new ComboBox<>();
        private final DatePicker date = new DatePicker(LocalDate.now());
        private final TextField amount = new TextField();
        private final ComboBox<PaymentMethod> method = new ComboBox<>();
        private final TextField reference = new TextField();
        private final TextArea notes = new TextArea();
        private final Button save = new Button("Record account entry");
        private final Label feedback = new Label();
        private final VBox view;

        private EntryForm(AccountEntryType paymentType) {
            this.paymentType = paymentType;
            type.setItems(FXCollections.observableArrayList(
                    AccountEntryType.OPENING_BALANCE, paymentType));
            type.setValue(AccountEntryType.OPENING_BALANCE);
            method.setItems(FXCollections.observableArrayList(PaymentMethod.CASH, PaymentMethod.QR));
            method.setPromptText("Required for payment");
            method.setDisable(true);
            type.setOnAction(event -> {
                boolean opening = type.getValue() == AccountEntryType.OPENING_BALANCE;
                method.setDisable(opening);
                if (opening) method.setValue(null);
            });
            amount.setPromptText("NPR, up to 2 decimal places");
            reference.setPromptText("Optional receipt/reference");
            notes.setPromptText("Optional, up to 500 characters");
            notes.setPrefRowCount(2);
            save.getStyleClass().add("primary-button");
            feedback.getStyleClass().add("feedback");
            feedback.setWrapText(true);

            GridPane fields = new GridPane();
            fields.setHgap(10);
            fields.setVgap(10);
            fields.addRow(0, label("Entry type *"), type, label("Date *"), date);
            fields.addRow(1, label("Amount (NPR) *"), amount,
                    label("Payment method"), method);
            fields.addRow(2, label("Reference"), reference);
            fields.addRow(3, label("Notes"), notes);
            GridPane.setHgrow(type, Priority.ALWAYS);
            GridPane.setHgrow(method, Priority.ALWAYS);
            GridPane.setHgrow(reference, Priority.ALWAYS);
            HBox actions = new HBox(save);
            actions.setAlignment(Pos.CENTER_RIGHT);
            view = new VBox(10, fields, actions, feedback);
        }

        private AccountEntryDraft draft(UUID partyId) {
            return new AccountEntryDraft(partyId, date.getValue(), type.getValue(),
                    parsePaisa(amount.getText()), method.getValue(), reference.getText(),
                    notes.getText(), null);
        }

        private void clear() {
            amount.clear();
            reference.clear();
            notes.clear();
            method.setValue(null);
            type.setValue(paymentType);
        }

        private static long parsePaisa(String text) {
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException("Amount is required.");
            }
            try {
                return new BigDecimal(text.trim()).movePointRight(2).longValueExact();
            } catch (ArithmeticException | NumberFormatException exception) {
                throw new IllegalArgumentException(
                        "Amount must be a valid NPR amount with at most 2 decimal places.");
            }
        }
    }
}
