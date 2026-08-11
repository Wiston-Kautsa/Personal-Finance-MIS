package com.wk.pfmis.fx;

import java.time.LocalDate;
import java.util.Map;

public interface ExchangeRateProvider {
    Map<String, ExchangeRateQuote> fetchLatestRates(String baseCurrency);

    ExchangeRateQuote fetchRate(String baseCurrency, String targetCurrency);

    ExchangeRateQuote fetchHistoricalRate(String baseCurrency, String targetCurrency, LocalDate date);

    String providerName();

    boolean requiresApiKey();
}
