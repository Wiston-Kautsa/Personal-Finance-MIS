package com.wk.pfmis.fx;

import java.math.BigDecimal;

public record ConversionResult(
        BigDecimal originalAmount,
        String originalCurrency,
        BigDecimal convertedAmount,
        String convertedCurrency,
        ExchangeRateQuote quote
) {
}
