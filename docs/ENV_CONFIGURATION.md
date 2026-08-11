# PFMIS Environment Configuration

PFMIS loads machine-level configuration from operating-system environment variables first, then Java system properties, then the local `.env` file. On installed Windows builds, the local file is discovered at:

`%LOCALAPPDATA%\PFMIS\.env`

PFMIS creates this file with safe defaults on first startup if it does not already exist. Upgrades must preserve the existing file. Do not store account balances, account numbers, transaction data, budgets, loans, project data, user passwords, or other business records in `.env`.

## Application

| Variable | Purpose | Required | Default | Allowed values | Example | Security | Restart |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `PFMIS_APP_ENV` | Selects production or development environment discovery behavior. | Optional | `production` | `production`, `development`, `dev`, `test` | `PFMIS_APP_ENV=production` | Non-sensitive | Yes |

## Foreign Exchange

| Variable | Purpose | Required | Default | Allowed values | Example | Security | Restart |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `PFMIS_FX_ENABLED` | Master switch for automatic online rates. | Optional | `true` | boolean | `PFMIS_FX_ENABLED=true` | Non-sensitive | Yes |
| `PFMIS_FX_PROVIDER` | Primary online provider. | Optional | `FRANKFURTER` | `FRANKFURTER`, `EXCHANGE_RATE_API_OPEN` | `PFMIS_FX_PROVIDER=FRANKFURTER` | Non-sensitive | Yes |
| `PFMIS_FX_BASE_URL` | Primary provider base URL. | Optional | provider default | HTTPS URL | `PFMIS_FX_BASE_URL=https://api.frankfurter.dev/v2` | Non-sensitive | Yes |
| `PFMIS_FX_API_KEY` | Primary provider API key when required. | Optional | blank | provider key | `PFMIS_FX_API_KEY=` | Sensitive | Yes |
| `PFMIS_FX_MWK_PREFERRED_SOURCE` | Administrative label for future MWK-specific source preference. | Optional | `RBM` | text | `PFMIS_FX_MWK_PREFERRED_SOURCE=RBM` | Non-sensitive | Yes |
| `PFMIS_FX_REFRESH_MINUTES` | Automatic refresh interval. | Optional | `360` | `15` to `1440` | `PFMIS_FX_REFRESH_MINUTES=360` | Non-sensitive | Yes |
| `PFMIS_FX_REFRESH_ON_STARTUP` | Refresh rates in background after workspace startup. | Optional | `true` | boolean | `PFMIS_FX_REFRESH_ON_STARTUP=true` | Non-sensitive | Yes |

## Foreign Exchange Network

| Variable | Purpose | Required | Default | Allowed values | Example | Security | Restart |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `PFMIS_FX_CONNECT_TIMEOUT_SECONDS` | Connection timeout for provider calls. | Optional | `10` | `1` to `120` | `PFMIS_FX_CONNECT_TIMEOUT_SECONDS=10` | Non-sensitive | Yes |
| `PFMIS_FX_REQUEST_TIMEOUT_SECONDS` | Total request timeout for provider calls. | Optional | `20` | `1` to `180` | `PFMIS_FX_REQUEST_TIMEOUT_SECONDS=20` | Non-sensitive | Yes |
| `PFMIS_FX_MAX_RETRIES` | Controlled retries after transient failures. | Optional | `2` | `0` to `5` | `PFMIS_FX_MAX_RETRIES=2` | Non-sensitive | Yes |
| `PFMIS_FX_RETRY_DELAY_MILLISECONDS` | Delay between retries. | Optional | `1500` | `0` to `60000` | `PFMIS_FX_RETRY_DELAY_MILLISECONDS=1500` | Non-sensitive | Yes |
| `PFMIS_FX_MAX_CONCURRENT_REQUESTS` | Maximum concurrent provider requests. | Optional | `3` | `1` to `10` | `PFMIS_FX_MAX_CONCURRENT_REQUESTS=3` | Non-sensitive | Yes |
| `PFMIS_FX_USER_AGENT` | HTTP User-Agent sent to rate providers. | Optional | `PFMIS/1.0` | text without private data | `PFMIS_FX_USER_AGENT=PFMIS/1.0` | Non-sensitive | Yes |

## Foreign Exchange Cache

| Variable | Purpose | Required | Default | Allowed values | Example | Security | Restart |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `PFMIS_FX_USE_CACHED_WHEN_OFFLINE` | Allows last valid stored rates when provider calls fail. | Optional | `true` | boolean | `PFMIS_FX_USE_CACHED_WHEN_OFFLINE=true` | Non-sensitive | Yes |
| `PFMIS_FX_CACHE_ENABLED` | Enables local storage of downloaded rates. | Optional | `true` | boolean | `PFMIS_FX_CACHE_ENABLED=true` | Non-sensitive | Yes |
| `PFMIS_FX_STALE_AFTER_HOURS` | Marks saved rates as stale after this age. | Optional | `24` | `1` to `744` | `PFMIS_FX_STALE_AFTER_HOURS=24` | Non-sensitive | Yes |
| `PFMIS_FX_VALIDATE_RESPONSES` | Rejects malformed, zero, negative, or unreasonable provider values. | Optional | `true` | boolean | `PFMIS_FX_VALIDATE_RESPONSES=true` | Non-sensitive | Yes |
| `PFMIS_FX_RATE_SCALE` | BigDecimal scale for FX rates and inverse calculations. | Optional | `10` | `4` to `18` | `PFMIS_FX_RATE_SCALE=10` | Non-sensitive | Yes |

## Historical Rates

| Variable | Purpose | Required | Default | Allowed values | Example | Security | Restart |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `PFMIS_FX_HISTORICAL_ENABLED` | Allows provider historical-rate requests. | Optional | `true` | boolean | `PFMIS_FX_HISTORICAL_ENABLED=true` | Non-sensitive | Yes |
| `PFMIS_FX_HISTORICAL_CACHE_FALLBACK` | Allows stored rates when an exact historical provider rate is unavailable. | Optional | `true` | boolean | `PFMIS_FX_HISTORICAL_CACHE_FALLBACK=true` | Non-sensitive | Yes |
| `PFMIS_FX_HISTORY_RETENTION_DAYS` | Future retention policy for stored FX history. `0` means retain indefinitely. | Optional | `0` | `0` to `3650` | `PFMIS_FX_HISTORY_RETENTION_DAYS=0` | Non-sensitive | Yes |

## Fallback Provider

| Variable | Purpose | Required | Default | Allowed values | Example | Security | Restart |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `PFMIS_FX_FALLBACK_PROVIDER` | Optional secondary provider used after primary failure. | Optional | blank | blank, `FRANKFURTER`, `EXCHANGE_RATE_API_OPEN` | `PFMIS_FX_FALLBACK_PROVIDER=` | Non-sensitive | Yes |
| `PFMIS_FX_FALLBACK_BASE_URL` | Fallback provider base URL. | Optional | blank/provider default | HTTPS URL | `PFMIS_FX_FALLBACK_BASE_URL=` | Non-sensitive | Yes |
| `PFMIS_FX_FALLBACK_API_KEY` | Fallback provider API key when required. | Optional | blank | provider key | `PFMIS_FX_FALLBACK_API_KEY=` | Sensitive | Yes |

## Local AI

| Variable | Purpose | Required | Default | Allowed values | Example | Security | Restart |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `PFMIS_LOCAL_AI_ENABLED` | Master switch for the bundled llama.cpp local AI runtime. | Optional | `true` | boolean | `PFMIS_LOCAL_AI_ENABLED=true` | Non-sensitive | Yes |
| `PFMIS_LOCAL_AI_HOST` | Loopback host used by both llama-server and the PFMIS HTTP client. | Optional | `127.0.0.1` | loopback host | `PFMIS_LOCAL_AI_HOST=127.0.0.1` | Non-sensitive | Yes |
| `PFMIS_LOCAL_AI_PORT` | Port used by both llama-server and the PFMIS HTTP client. | Optional | `8080` | `1` to `65535` | `PFMIS_LOCAL_AI_PORT=8080` | Non-sensitive | Yes |
| `PFMIS_LOCAL_AI_CONTEXT_SIZE` | llama.cpp context window passed with `-c`. | Optional | `2048` | `512` to `131072` | `PFMIS_LOCAL_AI_CONTEXT_SIZE=2048` | Non-sensitive | Yes |
| `PFMIS_LOCAL_AI_STARTUP_TIMEOUT_SECONDS` | Maximum time to wait for the health endpoint after starting llama-server. | Optional | `120` | `5` to `600` | `PFMIS_LOCAL_AI_STARTUP_TIMEOUT_SECONDS=120` | Non-sensitive | Yes |
| `PFMIS_LOCAL_AI_HEALTH_POLL_MILLISECONDS` | Delay between startup health checks. | Optional | `1000` | `100` to `10000` | `PFMIS_LOCAL_AI_HEALTH_POLL_MILLISECONDS=1000` | Non-sensitive | Yes |
| `PFMIS_LOCAL_AI_REQUEST_TIMEOUT_SECONDS` | HTTP timeout for Local AI health and inference checks. | Optional | `10` | `1` to `120` | `PFMIS_LOCAL_AI_REQUEST_TIMEOUT_SECONDS=10` | Non-sensitive | Yes |
| `PFMIS_LOCAL_AI_DIR` | Optional override for the packaged `local-ai` directory. Leave blank in production unless instructed by support. | Optional | blank | absolute path | `PFMIS_LOCAL_AI_DIR=` | Local path | Yes |

## Mail

| Variable | Purpose | Required | Default | Allowed values | Example | Security | Restart |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `PFMIS_MAIL_ENABLED` | Master switch for email features. | Optional | `false` | boolean | `PFMIS_MAIL_ENABLED=false` | Non-sensitive | Yes |
| `PFMIS_MAIL_CONNECT_TIMEOUT_SECONDS` | Mail connection timeout. | Optional | `15` | `1` to `300` | `PFMIS_MAIL_CONNECT_TIMEOUT_SECONDS=15` | Non-sensitive | Yes |
| `PFMIS_MAIL_READ_TIMEOUT_SECONDS` | Mail read timeout. | Optional | `30` | `1` to `600` | `PFMIS_MAIL_READ_TIMEOUT_SECONDS=30` | Non-sensitive | Yes |
| `PFMIS_MAIL_WRITE_TIMEOUT_SECONDS` | Mail write timeout. | Optional | `30` | `1` to `600` | `PFMIS_MAIL_WRITE_TIMEOUT_SECONDS=30` | Non-sensitive | Yes |
| `PFMIS_MAIL_FROM` | Default sender address. | Optional | blank | email address | `PFMIS_MAIL_FROM=` | Personal data | Yes |
| `PFMIS_MAIL_REPLY_TO` | Reply-to address. | Optional | blank | email address | `PFMIS_MAIL_REPLY_TO=` | Personal data | Yes |
| `PFMIS_MAIL_USERNAME` | SMTP/IMAP username. | Optional | blank | text | `PFMIS_MAIL_USERNAME=` | Sensitive | Yes |
| `PFMIS_MAIL_PASSWORD` | SMTP/IMAP password or app password. | Optional | blank | secret | `PFMIS_MAIL_PASSWORD=` | Sensitive | Yes |
| `PFMIS_SMTP_HOST` | SMTP server host. | Optional | blank | host name | `PFMIS_SMTP_HOST=` | Non-sensitive | Yes |
| `PFMIS_SMTP_PORT` | SMTP server port. | Optional | `587` | integer | `PFMIS_SMTP_PORT=587` | Non-sensitive | Yes |
| `PFMIS_SMTP_STARTTLS` | Enables STARTTLS. | Optional | `true` | boolean | `PFMIS_SMTP_STARTTLS=true` | Non-sensitive | Yes |
| `PFMIS_SMTP_SSL` | Enables SMTP SSL socket mode. | Optional | `false` | boolean | `PFMIS_SMTP_SSL=false` | Non-sensitive | Yes |
| `PFMIS_IMAP_HOST` | IMAP server host. | Optional | blank | host name | `PFMIS_IMAP_HOST=` | Non-sensitive | Yes |
| `PFMIS_IMAP_PORT` | IMAP server port. | Optional | `993` | integer | `PFMIS_IMAP_PORT=993` | Non-sensitive | Yes |
| `PFMIS_IMAP_SSL` | Enables IMAP SSL. | Optional | `true` | boolean | `PFMIS_IMAP_SSL=true` | Non-sensitive | Yes |

## Logging

| Variable | Purpose | Required | Default | Allowed values | Example | Security | Restart |
| --- | --- | --- | --- | --- | --- | --- |
| `PFMIS_LOG_LEVEL` | Diagnostic logging level. | Optional | `INFO` | `ERROR`, `WARN`, `INFO`, `DEBUG`, `TRACE` | `PFMIS_LOG_LEVEL=INFO` | Non-sensitive | Yes |
| `PFMIS_LOG_RETENTION_DAYS` | Deletes old technical `.log` files from the PFMIS log directory. | Optional | `30` | `1` to `3650` | `PFMIS_LOG_RETENTION_DAYS=30` | Non-sensitive | Yes |

Secrets must never be written to logs, screenshots, issue reports, or committed files.
