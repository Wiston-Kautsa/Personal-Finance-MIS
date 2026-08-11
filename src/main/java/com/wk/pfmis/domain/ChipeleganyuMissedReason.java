package com.wk.pfmis.domain;

import java.util.Locale;

public enum ChipeleganyuMissedReason {
    INSUFFICIENT_FUNDS,
    PAYMENT_NOT_MADE,
    GROUP_ALLOWED_SKIP,
    USER_ABSENT,
    PAYMENT_DEFERRED,
    OTHER;

    public static ChipeleganyuMissedReason fromDatabaseValue(String value) {
        if (value == null || value.isBlank()) {
            return OTHER;
        }
        return valueOf(value.trim().toUpperCase(Locale.ENGLISH).replace(' ', '_'));
    }
}
