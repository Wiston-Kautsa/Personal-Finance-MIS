package com.wk.pfmis.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FinancialTransactionEffectTest {
    private static final Money MWK_100 = Money.parseMajor("100.00", "MWK");

    @Test
    void ordinaryIncomeIncreasesAndExpenseDecreasesAccountBalance() {
        assertEquals(Money.parseMajor("100.00", "MWK"),
                FinancialTransactionEffect.accountBalanceEffect(MWK_100, "INCOME", "NORMAL"));
        assertEquals(Money.parseMajor("-100.00", "MWK"),
                FinancialTransactionEffect.accountBalanceEffect(MWK_100, "EXPENSE", "NORMAL"));
    }

    @Test
    void ownedAccountTransferHasZeroTotalBalanceEffect() {
        Money outgoing = FinancialTransactionEffect.accountBalanceEffect(MWK_100, "TRANSFER", "TRANSFER_OUT");
        Money incoming = FinancialTransactionEffect.accountBalanceEffect(MWK_100, "TRANSFER", "TRANSFER_IN");

        assertEquals(Money.zero("MWK"), outgoing.add(incoming));
    }

    @Test
    void borrowedLoanDisbursementIsCashIncreaseButNotOrdinaryIncome() {
        assertEquals(1, FinancialTransactionEffect.accountBalanceMultiplier("LOAN", "LOAN_PROCEEDS"));
        assertEquals(1, FinancialTransactionEffect.accountBalanceMultiplier("LOAN", "MONEY_BORROWED"));
        assertEquals(0, FinancialTransactionEffect.accountBalanceMultiplier("INCOME", "LOAN_PROCEEDS"));
    }

    @Test
    void borrowedLoanRepaymentComponentsReduceCashOnceEach() {
        assertEquals(-1, FinancialTransactionEffect.accountBalanceMultiplier("LOAN", "LOAN_PRINCIPAL_PAYMENT"));
        assertEquals(-1, FinancialTransactionEffect.accountBalanceMultiplier("EXPENSE", "LOAN_INTEREST_PAYMENT"));
        assertEquals(-1, FinancialTransactionEffect.accountBalanceMultiplier("EXPENSE", "LOAN_FEE"));
        assertEquals(-1, FinancialTransactionEffect.accountBalanceMultiplier("EXPENSE", "LOAN_PENALTY"));
    }

    @Test
    void lentLoanDisbursementAndRepaymentUseReceivableDirection() {
        assertEquals(Money.parseMajor("-100.00", "MWK"),
                FinancialTransactionEffect.accountBalanceEffect(MWK_100, "LOAN", "MONEY_LENT"));
        assertEquals(Money.parseMajor("100.00", "MWK"),
                FinancialTransactionEffect.accountBalanceEffect(MWK_100, "LOAN", "LENT_REPAID"));
    }

    @Test
    void budgetAndRevisionEventsDoNotMoveMoney() {
        assertEquals(Money.zero("MWK"),
                FinancialTransactionEffect.accountBalanceEffect(MWK_100, "BUDGET", "BUDGET_CREATED"));
        assertEquals(Money.zero("MWK"),
                FinancialTransactionEffect.accountBalanceEffect(MWK_100, "BUDGET", "BUDGET_REVISED"));
    }

    @Test
    void assetRecognitionAndOpeningBalanceRowsDoNotDuplicateCashMovement() {
        assertEquals(Money.zero("MWK"),
                FinancialTransactionEffect.accountBalanceEffect(MWK_100, "ASSET", "ASSET_RECOGNITION"));
        assertEquals(Money.zero("MWK"),
                FinancialTransactionEffect.accountBalanceEffect(MWK_100, "OPENING_BALANCE", "OPENING_BALANCE"));
    }
}
