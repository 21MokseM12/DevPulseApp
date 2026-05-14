# Network timeout/retry policy

## Scope
- Android client uses this policy for all HTTP calls to Bot API.
- Policy is implemented centrally in `NetworkModule` + `RetryPolicyInterceptor`.
- UI/ViewModel layers must not duplicate retry logic.

## Timeout defaults
- `connect timeout`: 10 seconds.
- `read timeout`: 20 seconds.
- `write timeout`: 20 seconds.
- `call timeout`: 30 seconds.

## Retry matrix
- Auto-retry is enabled only for idempotent reads (`GET`).
- Auto-retry is disabled for all mutating calls (`POST`, `DELETE`, `PUT`, `PATCH`) including auth and subscription mutations.
- Retriable HTTP statuses for `GET`: `408`, `429`, `500`, `502`, `503`, `504`.
- Retriable network failures for `GET`: `IOException`, except cancellation-like `InterruptedIOException`.

## Backoff policy
- Maximum retries: 2 (up to 3 total attempts including first call).
- Exponential backoff: 250 ms, then 500 ms.
- Backoff upper bound: 1000 ms.

## User-facing error alignment
- Timeout failures map to: `Превышено время ожидания сети. Повторите попытку.`
- No internet/network failures map to: `Ошибка сети. Проверьте подключение к интернету.`
- Temporary backend unavailability (`502/503/504`) maps to: `Сервер временно недоступен. Попробуйте позже.`
