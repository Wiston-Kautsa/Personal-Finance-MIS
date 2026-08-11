package com.wk.pfmis.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChipeleganyuContributionStatusTest {
    @Test
    void missedWaivedCancelledAndPaidBlockAutomaticDeduction() {
        assertTrue(ChipeleganyuContributionStatus.MISSED.blocksAutomaticDeduction());
        assertTrue(ChipeleganyuContributionStatus.WAIVED.blocksAutomaticDeduction());
        assertTrue(ChipeleganyuContributionStatus.CANCELLED.blocksAutomaticDeduction());
        assertTrue(ChipeleganyuContributionStatus.PAID.blocksAutomaticDeduction());
        assertFalse(ChipeleganyuContributionStatus.DUE.blocksAutomaticDeduction());
        assertFalse(ChipeleganyuContributionStatus.FAILED_AUTOMATIC_DEDUCTION.blocksAutomaticDeduction());
    }

    @Test
    void paidInstallmentCannotBecomeMissedWithoutReversal() {
        assertFalse(ChipeleganyuContributionStatus.PAID.canBecomeMissedWithoutReversal(50_000_00, 12));
        assertFalse(ChipeleganyuContributionStatus.PARTIALLY_PAID.canBecomeMissedWithoutReversal(30_000_00, 13));
        assertTrue(ChipeleganyuContributionStatus.FAILED_AUTOMATIC_DEDUCTION.canBecomeMissedWithoutReversal(0, null));
        assertTrue(ChipeleganyuContributionStatus.UPCOMING.canBecomeMissedWithoutReversal(0, null));
    }
}
