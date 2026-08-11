package com.wk.pfmis.fx;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FxMathTest {
    @Test
    void sameCurrencyQuoteIsAlwaysOne() {
        ExchangeRateQuote quote = ExchangeRateQuote.sameCurrency("mwk");

        assertEquals("MWK", quote.fromCurrency());
        assertEquals("MWK", quote.toCurrency());
        assertEquals(0, BigDecimal.ONE.compareTo(quote.rate()));
    }

    @Test
    void convertsWithBigDecimalPrecision() {
        BigDecimal converted = FxMath.convert(new BigDecimal("100.00"), new BigDecimal("1750.255"));

        assertEquals(new BigDecimal("175025.50"), converted);
    }

    @Test
    void invertsRatesAtConfiguredScale() {
        BigDecimal inverse = FxMath.inverse(new BigDecimal("1750"), 10);

        assertEquals(new BigDecimal("0.0005714286"), inverse);
    }

    @Test
    void rejectsZeroAndNegativeRates() {
        assertThrows(IllegalArgumentException.class, () -> FxMath.convert(BigDecimal.TEN, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> FxMath.inverse(new BigDecimal("-1"), 10));
        assertThrows(IllegalArgumentException.class, () -> new ExchangeRateQuote(
                "USD",
                "MWK",
                BigDecimal.ZERO,
                null,
                null,
                "Test",
                ExchangeRateSource.ONLINE,
                "ONLINE",
                false,
                false,
                ""
        ));
    }
}
