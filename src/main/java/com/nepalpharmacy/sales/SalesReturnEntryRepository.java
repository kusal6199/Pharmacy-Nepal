package com.nepalpharmacy.sales;

import java.time.Instant;
import java.util.Optional;

public interface SalesReturnEntryRepository {

    Optional<SalesReturnSource> findSourceByInvoiceNumber(long invoiceNumber);

    SalesReturn save(SalesReturnDraft draft, Instant createdAt);
}
