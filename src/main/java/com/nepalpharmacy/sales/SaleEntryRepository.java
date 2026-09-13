package com.nepalpharmacy.sales;

import java.time.Instant;

public interface SaleEntryRepository {

    SaleReceipt save(SaleDraft draft, Instant createdAt);
}
