package com.wk.pfmis.repositories;

import com.wk.pfmis.db.DatabaseHandler;

import java.time.LocalDate;
import java.util.Objects;

public class TransactionRepository {
    private final DatabaseHandler database;

    public TransactionRepository() {
        this(DatabaseHandler.getInstance());
    }

    public TransactionRepository(DatabaseHandler database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    public void record(TransactionWriteRequest request) {
        database.recordTransaction(
                request.accountId(),
                request.categoryId(),
                request.projectId(),
                request.projectActivityId(),
                request.personId(),
                request.transactionType(),
                request.purpose(),
                request.status(),
                request.legacyAmount(),
                request.date(),
                request.description(),
                request.paymentMethod(),
                request.referenceNumber()
        );
    }

    public void update(int transactionId, TransactionWriteRequest request) {
        database.updateTransaction(
                transactionId,
                request.accountId(),
                request.categoryId(),
                request.projectId(),
                request.projectActivityId(),
                request.personId(),
                request.transactionType(),
                request.purpose(),
                request.status(),
                request.legacyAmount(),
                request.date(),
                request.description(),
                request.paymentMethod(),
                request.referenceNumber()
        );
    }

    public record TransactionWriteRequest(
            int accountId,
            Integer categoryId,
            Integer projectId,
            Integer projectActivityId,
            Integer personId,
            String transactionType,
            String purpose,
            String status,
            double legacyAmount,
            LocalDate date,
            String description,
            String paymentMethod,
            String referenceNumber
    ) {
    }
}
