package com.nepalpharmacy.credit;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class AccountBalancePresentation {
    private AccountBalancePresentation() {
    }

    public static String customer(long balancePaisa) {
        if (balancePaisa > 0) return "Customer owes NPR " + magnitude(balancePaisa);
        if (balancePaisa < 0) return "Customer credit NPR " + magnitude(balancePaisa);
        return "Settled";
    }

    public static String supplier(long balancePaisa) {
        if (balancePaisa > 0) return "Payable to supplier NPR " + magnitude(balancePaisa);
        if (balancePaisa < 0) return "Supplier credit NPR " + magnitude(balancePaisa);
        return "Settled";
    }

    private static String magnitude(long paisa) {
        return BigDecimal.valueOf(paisa, 2)
                .abs().setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }
}
