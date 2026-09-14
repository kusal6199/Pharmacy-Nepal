package com.nepalpharmacy.credit.infrastructure;

import com.nepalpharmacy.credit.AccountEntry;
import com.nepalpharmacy.credit.SupplierAccountEntryRepository;
import com.nepalpharmacy.shared.infrastructure.DataAccessException;
import com.nepalpharmacy.shared.infrastructure.JdbcTransactionContext;
import com.nepalpharmacy.shared.persistence.TransactionContext;

import java.sql.SQLException;
import java.util.UUID;

public final class JdbcSupplierAccountEntryRepository implements SupplierAccountEntryRepository {
    @Override
    public boolean hasOpeningBalance(TransactionContext transaction, UUID supplierId) {
        try (var statement = JdbcTransactionContext.connection(transaction).prepareStatement("""
                SELECT 1 FROM supplier_account_entry
                WHERE supplier_id = ? AND entry_type = 'OPENING_BALANCE'
                LIMIT 1
                """)) {
            statement.setString(1, supplierId.toString());
            try (var rows = statement.executeQuery()) { return rows.next(); }
        } catch (SQLException exception) {
            throw new DataAccessException("Could not check supplier opening balance.", exception);
        }
    }

    @Override
    public void insert(TransactionContext transaction, AccountEntry entry) {
        try (var statement = JdbcTransactionContext.connection(transaction).prepareStatement("""
                INSERT INTO supplier_account_entry (
                    id, supplier_id, entry_date, entry_type, amount_paisa, payment_method,
                    reference_text, notes, created_at, created_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            JdbcCustomerAccountEntryRepository.bind(statement, entry);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DataAccessException("Could not record supplier account entry.", exception);
        }
    }
}
