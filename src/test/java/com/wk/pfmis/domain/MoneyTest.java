package com.wk.pfmis.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoneyTest {

    @Test
    void parsesMajorUnitsIntoIntegerMinorUnits() {
        Money money = Money.parseMajor("5,000.25", "MWK");

        assertEquals(500_025, money.amountMinor());
        assertEquals(new BigDecimal("5000.25"), money.toMajor());
    }

    @Test
    void mwkUsesTwoDecimalPlaces() {
        assertEquals(2, Money.scaleFor("MWK"));
        assertEquals("MWK 10.00", Money.ofMinor(1_000, "mwk").toString());
    }

    @Test
    void parsingUsesExplicitRounding() {
        assertEquals(100, Money.parseMajor("1.004", "MWK", RoundingMode.HALF_UP).amountMinor());
        assertEquals(101, Money.parseMajor("1.005", "MWK", RoundingMode.HALF_UP).amountMinor());
    }

    @Test
    void arithmeticKeepsCurrencyAndMinorUnits() {
        Money first = Money.parseMajor("100.00", "MWK");
        Money second = Money.parseMajor("25.50", "MWK");

        assertEquals(Money.ofMinor(12_550, "MWK"), first.add(second));
        assertEquals(Money.ofMinor(7_450, "MWK"), first.subtract(second));
        assertEquals(Money.ofMinor(11_000, "MWK"), first.multiply(new BigDecimal("1.10")));
    }

    @Test
    void rejectsCrossCurrencyArithmetic() {
        Money mwk = Money.parseMajor("100.00", "MWK");
        Money usd = Money.parseMajor("100.00", "USD");

        assertThrows(IllegalArgumentException.class, () -> mwk.add(usd));
        assertThrows(IllegalArgumentException.class, () -> mwk.subtract(usd));
        assertThrows(IllegalArgumentException.class, () -> mwk.compareTo(usd));
    }
}
