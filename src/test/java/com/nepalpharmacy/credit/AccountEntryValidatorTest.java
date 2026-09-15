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
    void acceptsPartialAndFullCustomerCreditPayoutByCashOrQr() {
        assertDoesNotThrow(() -> AccountEntryValidator.validateCustomer(
                draft(AccountEntryType.CREDIT_PAYOUT, 100, PaymentMethod.CASH), -400, false));
        assertDoesNotThrow(() -> AccountEntryValidator.validateCustomer(
                draft(AccountEntryType.CREDIT_PAYOUT, 400, PaymentMethod.QR), -400, false));
    }

    @Test
    void acceptsPartialAndFullSupplierCreditRefundByCashOrQr() {
        assertDoesNotThrow(() -> AccountEntryValidator.validateSupplier(
                draft(AccountEntryType.CREDIT_REFUND_RECEIVED, 200, PaymentMethod.QR),
                -500, false));
        assertDoesNotThrow(() -> AccountEntryValidator.validateSupplier(
                draft(AccountEntryType.CREDIT_REFUND_RECEIVED, 500, PaymentMethod.CASH),
                -500, false));
    }

    @Test
    void rejectsCustomerPayoutAboveCreditAndWithoutNegativeBalance() {
        AccountValidationException excess = assertThrows(AccountValidationException.class,
                () -> AccountEntryValidator.validateCustomer(
                        draft(AccountEntryType.CREDIT_PAYOUT, 401, PaymentMethod.CASH),
                        -400, false));
        AccountValidationException settled = assertThrows(AccountValidationException.class,
                () -> AccountEntryValidator.validateCustomer(
                        draft(AccountEntryType.CREDIT_PAYOUT, 1, PaymentMethod.CASH),
                        0, false));
        AccountValidationException owes = assertThrows(AccountValidationException.class,
                () -> AccountEntryValidator.validateCustomer(
                        draft(AccountEntryType.CREDIT_PAYOUT, 1, PaymentMethod.CASH),
                        400, false));

        assertEquals("Payout cannot exceed the customer's available credit of NPR 4.00.",
                excess.fieldErrors().get("amount"));
        assertEquals("There is no customer credit available to pay out.",
                settled.fieldErrors().get("credit"));
        assertEquals("There is no customer credit available to pay out.",
                owes.fieldErrors().get("credit"));
    }

    @Test
    void rejectsSupplierRefundAboveCreditAndWithoutNegativeBalance() {
        AccountValidationException excess = assertThrows(AccountValidationException.class,
                () -> AccountEntryValidator.validateSupplier(
                        draft(AccountEntryType.CREDIT_REFUND_RECEIVED, 501, PaymentMethod.QR),
                        -500, false));
        AccountValidationException settled = assertThrows(AccountValidationException.class,
                () -> AccountEntryValidator.validateSupplier(
                        draft(AccountEntryType.CREDIT_REFUND_RECEIVED, 1, PaymentMethod.CASH),
                        0, false));
        AccountValidationException payable = assertThrows(AccountValidationException.class,
                () -> AccountEntryValidator.validateSupplier(
                        draft(AccountEntryType.CREDIT_REFUND_RECEIVED, 1, PaymentMethod.CASH),
                        500, false));

        assertEquals("Refund cannot exceed the available supplier credit of NPR 5.00.",
                excess.fieldErrors().get("amount"));
        assertEquals("There is no supplier credit available to receive.",
                settled.fieldErrors().get("credit"));
        assertEquals("There is no supplier credit available to receive.",
                payable.fieldErrors().get("credit"));
    }

    @Test
    void creditSettlementsRequireCashOrQr() {
        AccountValidationException missingCustomerMethod = assertThrows(
                AccountValidationException.class,
                () -> AccountEntryValidator.validateCustomer(
                        draft(AccountEntryType.CREDIT_PAYOUT, 100, null), -400, false));
        AccountValidationException creditCustomerMethod = assertThrows(
                AccountValidationException.class,
                () -> AccountEntryValidator.validateCustomer(
                        draft(AccountEntryType.CREDIT_PAYOUT, 100, PaymentMethod.CREDIT),
                        -400, false));
        AccountValidationException missingSupplierMethod = assertThrows(
                AccountValidationException.class,
                () -> AccountEntryValidator.validateSupplier(
                        draft(AccountEntryType.CREDIT_REFUND_RECEIVED, 100, null),
                        -400, false));
        AccountValidationException creditSupplierMethod = assertThrows(
                AccountValidationException.class,
                () -> AccountEntryValidator.validateSupplier(
                        draft(AccountEntryType.CREDIT_REFUND_RECEIVED, 100,
                                PaymentMethod.CREDIT), -400, false));

        assertEquals("Payout requires Cash or QR / digital.",
                missingCustomerMethod.fieldErrors().get("paymentMethod"));
        assertEquals("Payout requires Cash or QR / digital.",
                creditCustomerMethod.fieldErrors().get("paymentMethod"));
        assertEquals("Supplier credit refund requires Cash or QR / digital.",
                missingSupplierMethod.fieldErrors().get("paymentMethod"));
        assertEquals("Supplier credit refund requires Cash or QR / digital.",
                creditSupplierMethod.fieldErrors().get("paymentMethod"));
    }

    @Test
    void rejectsNonPositiveCreditSettlementAmounts() {
        AccountValidationException zero = assertThrows(AccountValidationException.class,
                () -> AccountEntryValidator.validateCustomer(
                        draft(AccountEntryType.CREDIT_PAYOUT, 0, PaymentMethod.CASH),
                        -400, false));
        AccountValidationException negative = assertThrows(AccountValidationException.class,
                () -> AccountEntryValidator.validateSupplier(
                        draft(AccountEntryType.CREDIT_REFUND_RECEIVED, -1, PaymentMethod.QR),
                        -400, false));

        assertEquals("Amount must be greater than zero.", zero.fieldErrors().get("amount"));
        assertEquals("Amount must be greater than zero.", negative.fieldErrors().get("amount"));
    }

    @Test
    void rejectsEntryTypesBelongingToTheOtherPartyLedger() {
        assertCustomerTypeRejected(AccountEntryType.PAYMENT_MADE);
        assertCustomerTypeRejected(AccountEntryType.CREDIT_REFUND_RECEIVED);
        assertSupplierTypeRejected(AccountEntryType.PAYMENT_RECEIVED);
        assertSupplierTypeRejected(AccountEntryType.CREDIT_PAYOUT);
    }

    @Test
    void handlesLongMinValueCreditWithoutMagnitudeOverflow() {
        assertDoesNotThrow(() -> AccountEntryValidator.validateCustomer(
                draft(AccountEntryType.CREDIT_PAYOUT, Long.MAX_VALUE, PaymentMethod.CASH),
                Long.MIN_VALUE, false));
        assertDoesNotThrow(() -> AccountEntryValidator.validateSupplier(
                draft(AccountEntryType.CREDIT_REFUND_RECEIVED, Long.MAX_VALUE,
                        PaymentMethod.QR), Long.MIN_VALUE, false));
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

    private static void assertCustomerTypeRejected(AccountEntryType type) {
        AccountValidationException exception = assertThrows(AccountValidationException.class,
                () -> AccountEntryValidator.validateCustomer(
                        draft(type, 100, PaymentMethod.CASH), -400, false));
        assertEquals("Customer entry must be an opening balance, payment received, "
                        + "or customer credit payout.",
                exception.fieldErrors().get("entryType"));
    }

    private static void assertSupplierTypeRejected(AccountEntryType type) {
        AccountValidationException exception = assertThrows(AccountValidationException.class,
                () -> AccountEntryValidator.validateSupplier(
                        draft(type, 100, PaymentMethod.CASH), -400, false));
        assertEquals("Supplier entry must be an opening balance, payment made, "
                        + "or supplier credit refund received.",
                exception.fieldErrors().get("entryType"));
    }
}
