package com.nepalpharmacy.sales.ui;

import com.nepalpharmacy.party.Customer;
import com.nepalpharmacy.party.CustomerDraft;
import com.nepalpharmacy.party.CustomerService;
import com.nepalpharmacy.party.CustomerValidationException;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.util.Objects;
import java.util.function.Consumer;

final class CustomerSelectionPane {

    private final CustomerService customers;
    private final Consumer<String> success;
    private final Consumer<String> error;
    private final VBox root = new VBox(10);
    private final ComboBox<Customer> customer = new ComboBox<>();
    private final TextField customerName = new TextField();
    private final TextField customerPhone = new TextField();
    private final TextField customerAddress = new TextField();

    CustomerSelectionPane(
            CustomerService customers,
            String prompt,
            Consumer<String> success,
            Consumer<String> error
    ) {
        this.customers = Objects.requireNonNull(customers, "customers");
        this.success = Objects.requireNonNull(success, "success");
        this.error = Objects.requireNonNull(error, "error");
        configure(prompt);
        refreshCustomers();
    }

    VBox view() {
        return root;
    }

    Customer selectedCustomer() {
        return customer.getValue();
    }

    void clearSelection() {
        customer.setValue(null);
    }

    void setShown(boolean shown) {
        root.setVisible(shown);
        root.setManaged(shown);
        if (!shown) {
            clearSelection();
        }
    }

    private void configure(String prompt) {
        customer.setPromptText(prompt);
        customer.setMaxWidth(Double.MAX_VALUE);
        customer.setConverter(new StringConverter<>() {
            @Override
            public String toString(Customer value) {
                return value == null ? "" : value.name()
                        + (value.phone() == null ? "" : " · " + value.phone());
            }

            @Override
            public Customer fromString(String value) {
                return null;
            }
        });

        GridPane customerFields = new GridPane();
        customerFields.setHgap(10);
        customerFields.setVgap(10);
        customerFields.addRow(0, label("Customer *"), customer);
        GridPane.setHgrow(customer, Priority.ALWAYS);
        root.getChildren().setAll(customerFields, createCustomerForm());
    }

    private TitledPane createCustomerForm() {
        customerName.setPromptText("Required");
        customerPhone.setPromptText("Optional");
        customerAddress.setPromptText("Optional");

        GridPane fields = new GridPane();
        fields.setHgap(10);
        fields.setVgap(10);
        fields.addRow(0, label("Name *"), customerName, label("Phone"), customerPhone);
        fields.addRow(1, label("Address"), customerAddress);
        GridPane.setHgrow(customerName, Priority.ALWAYS);
        GridPane.setHgrow(customerAddress, Priority.ALWAYS);

        Button add = new Button("Add and select customer");
        add.getStyleClass().add("secondary-button");
        add.setOnAction(event -> addCustomer());
        HBox actions = new HBox(add);
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox body = new VBox(12, fields, actions);
        body.setPadding(new Insets(12));
        TitledPane pane = new TitledPane("Need a new customer? Add one here", body);
        pane.setExpanded(false);
        return pane;
    }

    private void addCustomer() {
        try {
            Customer created = customers.create(new CustomerDraft(
                    customerName.getText(), customerPhone.getText(),
                    customerAddress.getText(), true));
            refreshCustomers();
            customer.setValue(created);
            customerName.clear();
            customerPhone.clear();
            customerAddress.clear();
            success.accept("Added and selected customer “" + created.name() + "”.");
        } catch (CustomerValidationException exception) {
            error.accept(String.join("\n", exception.fieldErrors().values()));
        } catch (RuntimeException exception) {
            error.accept("The customer could not be added. " + exception.getMessage());
        }
    }

    private void refreshCustomers() {
        try {
            Customer selected = customer.getValue();
            customer.setItems(FXCollections.observableArrayList(customers.findAllActive()));
            if (selected != null) {
                customer.getItems().stream()
                        .filter(item -> item.id().equals(selected.id()))
                        .findFirst()
                        .ifPresent(customer::setValue);
            }
        } catch (RuntimeException exception) {
            error.accept("Customers could not be loaded. " + exception.getMessage());
        }
    }

    private static Label label(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("field-label");
        return label;
    }
}
