package com.wk.pfmis.domain;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class FinancialTransactionEffect {
    private static final Set<String> CASH_INCREASE_TYPES = Set.of("INCOME", "ASSET_SALE");
    private static final Set<String> CASH_DECREASE_TYPES = Set.of("EXPENSE");
    private static final Set<String> LOAN_CASH_INCREASE_PURPOSES = Set.of(
            "MONEY_BORROWED",
            "LENT_REPAID",
            "LOAN_PROCEEDS",
            "COMMUNITY_LOAN_RECEIVABLE_INCREASE",
            "COMMUNITY_LOAN_LIABILITY_INCREASE"
    );
    private static final Set<String> LOAN_CASH_DECREASE_PURPOSES = Set.of(
            "MONEY_LENT",
            "BORROWED_REPAID",
            "LOAN_PRINCIPAL_PAYMENT",
            "LOAN_SETTLEMENT",
            "COMMUNITY_LOAN_RECEIVABLE_DECREASE",
            "COMMUNITY_LOAN_LIABILITY_DECREASE"
    );

    private FinancialTransactionEffect() {
    }

    public static Money accountBalanceEffect(Money amount, String transactionType, String transactionPurpose) {
        int multiplier = accountBalanceMultiplier(transactionType, transactionPurpose);
        return Money.ofMinor(Math.multiplyExact(amount.amountMinor(), multiplier), amount.currencyCode());
    }

    public static int accountBalanceMultiplier(String transactionType, String transactionPurpose) {
        String type = normalize(transactionType);
        String purpose = normalize(transactionPurpose);
        if (CASH_INCREASE_TYPES.contains(type)) {
            return 1;
        }
        if (CASH_DECREASE_TYPES.contains(type)) {
            return -1;
        }
        if ("TRANSFER".equals(type) && "TRANSFER_IN".equals(purpose)) {
            return 1;
        }
        if ("TRANSFER".equals(type) && "TRANSFER_OUT".equals(purpose)) {
            return -1;
        }
        if ("LOAN".equals(type) && LOAN_CASH_INCREASE_PURPOSES.contains(purpose)) {
            return 1;
        }
        if ("LOAN".equals(type) && LOAN_CASH_DECREASE_PURPOSES.contains(purpose)) {
            return -1;
        }
        if ("ADJUSTMENT".equals(type) && "BALANCE_INCREASE".equals(purpose)) {
            return 1;
        }
        if ("ADJUSTMENT".equals(type) && "BALANCE_DECREASE".equals(purpose)) {
            return -1;
        }
        return 0;
    }

    public static String accountBalanceCaseSql(String transactionTypeExpression,
                                               String transactionPurposeExpression,
                                               String amountExpression) {
        String type = "upper(COALESCE(" + transactionTypeExpression + ", ''))";
        String purpose = "upper(COALESCE(" + transactionPurposeExpression + ", ''))";
        return """
                CASE
                    WHEN %s IN (%s) THEN %s
                    WHEN %s IN (%s) THEN -%s
                    WHEN %s = 'TRANSFER' AND %s = 'TRANSFER_IN' THEN %s
                    WHEN %s = 'TRANSFER' AND %s = 'TRANSFER_OUT' THEN -%s
                    WHEN %s = 'LOAN' AND %s IN (%s) THEN %s
                    WHEN %s = 'LOAN' AND %s IN (%s) THEN -%s
                    WHEN %s = 'ADJUSTMENT' AND %s = 'BALANCE_INCREASE' THEN %s
                    WHEN %s = 'ADJUSTMENT' AND %s = 'BALANCE_DECREASE' THEN -%s
                    ELSE 0
                END
                """.formatted(
                type, sqlList(CASH_INCREASE_TYPES), amountExpression,
                type, sqlList(CASH_DECREASE_TYPES), amountExpression,
                type, purpose, amountExpression,
                type, purpose, amountExpression,
                type, purpose, sqlList(LOAN_CASH_INCREASE_PURPOSES), amountExpression,
                type, purpose, sqlList(LOAN_CASH_DECREASE_PURPOSES), amountExpression,
                type, purpose, amountExpression,
                type, purpose, amountExpression
        );
    }

    private static String sqlList(Set<String> values) {
        return values.stream()
                .sorted()
                .map(value -> "'" + value + "'")
                .collect(Collectors.joining(", "));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ENGLISH);
    }
}
