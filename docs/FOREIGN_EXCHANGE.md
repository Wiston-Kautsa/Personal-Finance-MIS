# Automatic Foreign Exchange

PFMIS uses a centralized exchange-rate service for currency conversion. Controllers do not call internet APIs directly. They request rates or conversions from `ExchangeRateService`, which handles provider calls, validation, caching, manual overrides, stale-rate status, and BigDecimal arithmetic.

## Provider

The default provider is Frankfurter using `https://api.frankfurter.dev/v2`. It does not require an API key for the current implementation. The provider layer is abstracted behind `ExchangeRateProvider`, so another provider can be added or configured without rewriting financial controllers.

PFMIS sends only the currency pair and optional historical date to the provider. It does not send balances, account names, account numbers, transactions, budgets, loans, projects, savings groups, or user identity.

## Rate Selection

PFMIS uses one documented selection policy:

1. Locked transaction rate, when the transaction already has one.
2. Active manual override for the requested pair and date.
3. Stored historical rate for the requested date.
4. Online current or historical provider rate when automatic rates are enabled.
5. Most recent valid cached rate.
6. Manual entry prompt when no valid rate exists.

Same-currency conversion always uses `1` and never calls a provider.

## Offline Behavior

PFMIS starts normally when internet access is unavailable. Cached rates are loaded from the database immediately, and startup provider refresh runs in the background when enabled. If a provider request fails, PFMIS uses the most recent valid stored rate and marks it as cached or stale in the UI. If no rate exists for a required pair, the user is told that the exchange rate is unavailable and can retry or enter a manual rate.

## Manual Overrides

Manual rates are saved separately as `MANUAL` exchange-rate records. A manual record stores the currency pair, rate, effective date, optional expiry date, source, provider label, notes, and timestamps. Manual rates are clearly labeled in the Currencies & Exchange Rates page and in transaction conversion summaries.

## Transaction Locking

For cross-currency transfers, PFMIS shows the selected rate before confirmation and then writes the rate into the posted transaction rows:

- original amount
- original currency
- exchange rate
- converted amount
- converted currency
- exchange-rate source
- exchange-rate timestamp
- provider
- rate type
- effective rate date

Refreshing rates later does not change those posted conversion values.

## UI

The Currencies & Exchange Rates screen shows:

- automatic-rate status
- provider name
- base currency
- last successful update
- active rate count
- source and status for each pair
- refresh actions
- manual-rate entry
- details and history dialogs

Cross-currency transfers show a conversion card with the rate direction, source, updated timestamp, amount sent, and estimated amount received before confirmation.

## Reporting

Financial reports should prefer locked transaction FX metadata where available, then historical rates for the transaction date, and should label converted amounts with the reporting currency and rate basis. Existing report paths that still rely on legacy `rate_to_base` values receive updated values when online rates are cached, but deeper locked-rate report display remains a known follow-up item.

## Precision

PFMIS uses `BigDecimal` for exchange rates and conversions. `FxMath` centralizes monetary scale, FX scale, and rounding policy so controllers do not scatter arbitrary rounding rules.
