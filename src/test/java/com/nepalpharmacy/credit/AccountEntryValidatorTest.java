package com.nepalpharmacy.credit;

import com.nepalpharmacy.sales.PaymentMethod;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccountEntryValidatorTest {
    private static final UUID PARTY = UUID.randomUUID();
    private static final LocalDate DATE = LocalDate.of(2026, 9, 14);

    @Test
    void acceptsOnePositiveOpeningWithoutPaymentMethod() {
        assertDoesNotThrow(() -> AccountEntryValidator.validateCustomer(
                draft(AccountEntryType.OPENING_BALANCE, 100, null), 0, false));
        assertDoesNotThrow(() -> AccountEntryValidator.validateSupplier(
                draft(AccountEntryType.OPENING_BALANCE, 100, null), 0, false));
    }

    @Test
    void rejectsDuplicateOpeningAndOpeningWithPaymentMethod() {
        AccountValidationException duplicate = assertThrows(AccountValidationException.class,
                () -> AccountEntryValidator.validateCustomer(
                        draft(AccountEntryType.OPENING_BALANCE, 100, null), 0, true));
        AccountValidationException method = assertThrows(AccountValidationException.class,
                () -> AccountEntryValidator.validateSupplier(
                        draft(AccountEntryType.OPENING_BALANCE, 100, PaymentMethod.CASH), 0, false));

        assertEquals("An opening balance already exists for this party.",
                duplicate.fieldErrors().get("openingBalance"));
        assertEquals("Opening balance must not have a payment method.",
                method.fieldErrors().get("paymentMethod"));
    }

    @Test
    void rejectsNonPositiveAmounts() {
        AccountValidationException zero = assertThrows(AccountValidationException.class,
                () -> AccountEntryValidator.validateCustomer(
                        draft(AccountEntryType.OPENING_BALANCE, 0, null), 0, false));
        AccountValidationException negative = assertThrows(AccountValidationException.class,
                () -> AccountEntryValidator.validateSupplier(
                        draft(AccountEntryType.OPENING_BALANCE, -1, null), 0, false));

        assertEquals("Amount must be greater than zero.", zero.fieldErrors().get("amount"));
        assertEquals("Amount must be greater than zero.", negative.fieldErrors().get("amount"));
    }

    @Test
    void acceptsCashOrQrPaymentUpToPositiveOutstanding() {
        assertDoesNotThrow(() -> AccountEntryValidator.validateCustomer(
                draft(AccountEntryType.PAYMENT_RECEIVED, 500, PaymentMethod.CASH), 500, false));
        assertDoesNotThrow(() -> AccountEntryValidator.validateSupplier(
                draft(AccountEntryType.PAYMENT_MADE, 250, PaymentMethod.QR), 500, false));
    }

    @Test
    void rejectsCreditPaymentNoOutstandingAndOverpayment() {
        AccountValidationException credit = assertThrows(AccountValidationException.class,
                () -> AccountEntryValidator.validateCustomer(
                        draft(AccountEntryType.PAYMENT_RECEIVED, 100, PaymentMethod.CREDIT), 500, false));
        AccountValidationException none = assertThrows(AccountValidationException.class,
                () -> AccountEntryValidator.validateSupplier(
                        draft(AccountEntryType.PAYMENT_MADE, 100, PaymentMethod.CASH), -20, false));
        AccountValidationException excess = assertThrows(AccountValidationException.class,
                () -> AccountEntryValidator.validateCustomer(
                        draft(AccountEntryType.PAYMENT_RECEIVED, 501, PaymentMethod.QR), 500, false));

        assertEquals("Payment received requires Cash or QR / digital.",
                credit.fieldErrors().get("paymentMethod"));
        assertEquals("There is no positive outstanding balance to settle.",
                none.fieldErrors().get("outstanding"));
        assertEquals("Payment cannot exceed the outstanding balance.",
                excess.fieldErrors().get("amount"));
    }

    @Test
    void enforcesReferenceAndNotesBounds() {
        AccountEntryDraft draft = new AccountEntryDraft(PARTY, DATE,
                AccountEntryType.OPENING_BALANCE, 100, null, "x".repeat(161),
                "n".repeat(501), null);

        AccountValidationException exception = assertThrows(AccountValidationException.class,
                () -> AccountEntryValidator.validateCustomer(draft, 0, false));

        assertEquals("Reference must be at most 160 characters.",
                exception.fieldErrors().get("reference"));
        assertEquals("Notes must be at most 500 characters.",
                exception.fieldErrors().get("notes"));
    }

    private static AccountEntryDraft draft(
            AccountEntryType type, long amount, PaymentMethod method) {
        return new AccountEntryDraft(PARTY, DATE, type, amount, method, null, null, null);
    }
}
