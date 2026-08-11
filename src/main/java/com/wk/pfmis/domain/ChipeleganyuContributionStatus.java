package com.wk.pfmis.domain;

import java.util.Locale;

public enum ChipeleganyuContributionStatus {
    UPCOMING,
    DUE,
    PAID,
    PARTIALLY_PAID,
    MISSED,
    OVERDUE,
    FAILED_AUTOMATIC_DEDUCTION,
    WAIVED,
    CANCELLED;

    public static ChipeleganyuContributionStatus fromDatabaseValue(String value) {
        if (value == null || value.isBlank()) {
            return UPCOMING;
        }
        return valueOf(value.trim().toUpperCase(Locale.ENGLISH).replace(' ', '_'));
    }

    public boolean blocksAutomaticDeduction() {
        return this == MISSED || this == WAIVED || this == CANCELLED || this == PAID;
    }

    public boolean hasPostedMoneyMovement() {
        return this == PAID || this == PARTIALLY_PAID;
    }

    public boolean canBecomeMissedWithoutReversal(long amountPaidMinor, Integer transactionId) {
        return amountPaidMinor == 0 && transactionId == null && !hasPostedMoneyMovement();
    }
}
