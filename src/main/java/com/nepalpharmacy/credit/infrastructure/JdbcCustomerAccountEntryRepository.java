package com.nepalpharmacy.credit.infrastructure;

import com.nepalpharmacy.credit.AccountEntry;
import com.nepalpharmacy.credit.CustomerAccountEntryRepository;
import com.nepalpharmacy.shared.infrastructure.DataAccessException;
import com.nepalpharmacy.shared.infrastructure.JdbcTransactionContext;
import com.nepalpharmacy.shared.persistence.TransactionContext;

import java.sql.SQLException;
import java.sql.Types;
import java.util.UUID;

public final class JdbcCustomerAccountEntryRepository implements CustomerAccountEntryRepository {
    @Override
    public boolean hasOpeningBalance(TransactionContext transaction, UUID customerId) {
        try (var statement = JdbcTransactionContext.connection(transaction).prepareStatement("""
                SELECT 1 FROM customer_account_entry
                WHERE customer_id = ? AND entry_type = 'OPENING_BALANCE'
                LIMIT 1
                """)) {
            statement.setString(1, customerId.toString());
            try (var rows = statement.executeQuery()) { return rows.next(); }
        } catch (SQLException exception) {
            throw new DataAccessException("Could not check customer opening balance.", exception);
        }
    }

    @Override
    public void insert(TransactionContext transaction, AccountEntry entry) {
        try (var statement = JdbcTransactionContext.connection(transaction).prepareStatement("""
                INSERT INTO customer_account_entry (
                    id, customer_id, entry_date, entry_type, amount_paisa, payment_method,
                    reference_text, notes, created_at, created_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            bind(statement, entry);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DataAccessException("Could not record customer account entry.", exception);
        }
    }

    static void bind(java.sql.PreparedStatement statement, AccountEntry entry) throws SQLException {
        statement.setString(1, entry.id().toString());
        statement.setString(2, entry.partyId().toString());
        statement.setString(3, entry.entryDate().toString());
        statement.setString(4, entry.entryType().name());
        statement.setLong(5, entry.amountPaisa());
        nullable(statement, 6, entry.paymentMethod() == null ? null : entry.paymentMethod().name());
        nullable(statement, 7, entry.referenceText());
        nullable(statement, 8, entry.notes());
        statement.setString(9, entry.createdAt().toString());
        nullable(statement, 10, entry.createdBy() == null ? null : entry.createdBy().toString());
    }

    static void nullable(java.sql.PreparedStatement statement, int index, String value)
            throws SQLException {
        if (value == null) statement.setNull(index, Types.VARCHAR);
        else statement.setString(index, value);
    }
}
