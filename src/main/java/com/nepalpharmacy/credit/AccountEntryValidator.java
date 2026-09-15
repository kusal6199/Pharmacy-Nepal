package com.nepalpharmacy.credit;

import com.nepalpharmacy.sales.PaymentMethod;

import java.util.LinkedHashMap;
import java.util.Map;

public final class AccountEntryValidator {
    private AccountEntryValidator() {
    }

    public static void validateCustomer(
            AccountEntryDraft draft, long currentBalancePaisa, boolean hasOpeningBalance) {
        Map<String, String> errors = basicErrors(draft);
        if (draft != null) {
            if (draft.entryType() == AccountEntryType.OPENING_BALANCE) {
                validateOpening(draft, hasOpeningBalance, errors);
            } else if (draft.entryType() == AccountEntryType.PAYMENT_RECEIVED) {
                validatePayment(draft, currentBalancePaisa, "Payment received", errors);
            } else if (draft.entryType() == AccountEntryType.CREDIT_PAYOUT) {
                validateCreditSettlement(
                        draft, currentBalancePaisa, "Payout",
                        "There is no customer credit available to pay out.",
                        "Payout cannot exceed the customer's available credit of NPR ", errors);
            } else {
                errors.put("entryType", "Customer entry must be an opening balance, "
                        + "payment received, or customer credit payout.");
            }
        }
        throwIfAny(errors);
    }

    public static void validateSupplier(
            AccountEntryDraft draft, long currentBalancePaisa, boolean hasOpeningBalance) {
        Map<String, String> errors = basicErrors(draft);
        if (draft != null) {
            if (draft.entryType() == AccountEntryType.OPENING_BALANCE) {
                validateOpening(draft, hasOpeningBalance, errors);
            } else if (draft.entryType() == AccountEntryType.PAYMENT_MADE) {
                validatePayment(draft, currentBalancePaisa, "Payment made", errors);
            } else if (draft.entryType() == AccountEntryType.CREDIT_REFUND_RECEIVED) {
                validateCreditSettlement(
                        draft, currentBalancePaisa, "Supplier credit refund",
                        "There is no supplier credit available to receive.",
                        "Refund cannot exceed the available supplier credit of NPR ", errors);
            } else {
                errors.put("entryType", "Supplier entry must be an opening balance, "
                        + "payment made, or supplier credit refund received.");
            }
        }
        throwIfAny(errors);
    }

    private static Map<String, String> basicErrors(AccountEntryDraft draft) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (draft == null) {
            errors.put("entry", "Account entry details are required.");
            return errors;
        }
        if (draft.partyId() == null) errors.put("party", "Party is required.");
        if (draft.entryDate() == null) errors.put("entryDate", "Entry date is required.");
        if (draft.entryType() == null) errors.put("entryType", "Entry type is required.");
        if (draft.amountPaisa() <= 0) errors.put("amount", "Amount must be greater than zero.");
        if (draft.referenceText() != null && draft.referenceText().length() > 160) {
            errors.put("reference", "Reference must be at most 160 characters.");
        }
        if (draft.notes() != null && draft.notes().length() > 500) {
            errors.put("notes", "Notes must be at most 500 characters.");
        }
        return errors;
    }

    private static void validateOpening(
            AccountEntryDraft draft, boolean hasOpening, Map<String, String> errors) {
        if (draft.paymentMethod() != null) {
            errors.put("paymentMethod", "Opening balance must not have a payment method.");
        }
        if (hasOpening) {
            errors.put("openingBalance", "An opening balance already exists for this party.");
        }
    }

    private static void validatePayment(
            AccountEntryDraft draft, long outstanding, String label, Map<String, String> errors) {
        if (draft.paymentMethod() != PaymentMethod.CASH
                && draft.paymentMethod() != PaymentMethod.QR) {
            errors.put("paymentMethod", label + " requires Cash or QR / digital.");
        }
        if (outstanding <= 0) {
            errors.put("outstanding", "There is no positive outstanding balance to settle.");
        } else if (draft.amountPaisa() > outstanding) {
            errors.put("amount", "Payment cannot exceed the outstanding balance.");
        }
    }

    private static void validateCreditSettlement(
            AccountEntryDraft draft,
            long currentBalance,
            String methodLabel,
            String noCreditMessage,
            String excessMessagePrefix,
            Map<String, String> errors
    ) {
        if (draft.paymentMethod() != PaymentMethod.CASH
                && draft.paymentMethod() != PaymentMethod.QR) {
            errors.put("paymentMethod", methodLabel + " requires Cash or QR / digital.");
        }
        if (currentBalance >= 0) {
            errors.put("credit", noCreditMessage);
        } else if (draft.amountPaisa() > 0 && currentBalance > -draft.amountPaisa()) {
            errors.put("amount", excessMessagePrefix
                    + AccountBalancePresentation.magnitude(currentBalance) + ".");
        }
    }

    private static void throwIfAny(Map<String, String> errors) {
        if (!errors.isEmpty()) throw new AccountValidationException(errors);
    }
}
