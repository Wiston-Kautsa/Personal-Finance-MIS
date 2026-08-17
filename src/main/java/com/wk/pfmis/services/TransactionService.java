package com.wk.pfmis.services;

import com.wk.pfmis.domain.Money;
import com.wk.pfmis.repositories.TransactionRepository;

import java.time.LocalDate;
import java.util.Objects;

public final class TransactionService {
    private final TransactionRepository repository;

    public TransactionService() {
        this(new TransactionRepository());
    }

    public TransactionService(TransactionRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public void record(TransactionCommand command) {
        repository.record(writeRequest(command));
    }

    public void update(int transactionId, TransactionCommand command) {
        if (transactionId <= 0) {
            throw new IllegalArgumentException("Select a valid transaction.");
        }
        repository.update(transactionId, writeRequest(command));
    }

    private TransactionRepository.TransactionWriteRequest writeRequest(TransactionCommand command) {
        TransactionCommand clean = validate(command);
        return new TransactionRepository.TransactionWriteRequest(
                clean.accountId(),
                clean.categoryId(),
                clean.projectId(),
                clean.projectActivityId(),
                clean.personId(),
                clean.transactionType(),
                clean.purpose(),
                clean.status(),
                clean.amount().toMajor().doubleValue(),
                clean.date(),
                clean.description(),
                clean.paymentMethod(),
                clean.referenceNumber()
        );
    }

    private TransactionCommand validate(TransactionCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Transaction details are required.");
        }
        if (command.accountId() <= 0) {
            throw new IllegalArgumentException("Select a valid account.");
        }
        Money amount = Objects.requireNonNull(command.amount(), "amount");
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("Amount must be greater than zero.");
        }
        if (command.date() == null) {
            throw new IllegalArgumentException("Transaction date is required.");
        }
        String type = requireText(command.transactionType(), "Transaction type");
        String purpose = requireText(command.purpose(), "Transaction purpose");
        String status = requireText(command.status(), "Transaction status");
        return new TransactionCommand(
                command.accountId(),
                command.categoryId(),
                command.projectId(),
                command.projectActivityId(),
                command.personId(),
                type,
                purpose,
                status,
                amount,
                command.date(),
                safeText(command.description()),
                safeText(command.paymentMethod()),
                safeText(command.referenceNumber())
        );
    }

    private String requireText(String value, String label) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return value.trim();
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    public record TransactionCommand(
            int accountId,
            Integer categoryId,
            Integer projectId,
            Integer projectActivityId,
            Integer personId,
            String transactionType,
            String purpose,
            String status,
            Money amount,
            LocalDate date,
            String description,
            String paymentMethod,
            String referenceNumber
    ) {
    }
}
