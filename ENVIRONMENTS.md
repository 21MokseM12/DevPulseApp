# Конфигурация окружений

В проекте `app` модуль использует `BuildConfig.BASE_URL` и `BuildConfig.ENVIRONMENT` для выбора API-окружения без правок исходного кода.

## Контрактная API-граница Android

- Android-клиент работает только с bot HTTP API (`/api/v1/...`), включая `links` и `notifications`.
- Контракты `scrapper` (`HTTP/Async`) считаются backend-internal и не вызываются мобильным приложением напрямую.

## Доступные окружения

- `debug` -> `http://10.0.2.2:8080/`
- `staging` -> `https://staging-api.devpulse.example/`
- `release` -> `https://api.devpulse.example/`

## TLS pinning (staging/release)

- `staging` и `release` сборки требуют явной настройки certificate pin-ов через Gradle properties:
  - `devpulse.stagingCertPins=sha256/<pin1>,sha256/<pin2>`
  - `devpulse.releaseCertPins=sha256/<pin1>,sha256/<pin2>`
- Формат: список `sha256/...` через запятую, без пробелов.
- При отсутствии pin-ов в `staging/release`:
  - auth-запросы блокируются `AuthTransportSecurityGuard` до сетевого вызова;
  - сеть в production-конфигурации падает fail-fast при создании `OkHttpClient`.
- Это fail-closed политика: приложение не делает небезопасный transport в production-вариантах.

### Ротация pin-ов (runbook)

- Получите новый и резервный pin у backend/security команды (для текущего и следующего сертификата).
- Обновите `devpulse.stagingCertPins` и `devpulse.releaseCertPins` в защищенном CI/локальном секрете.
- Проверьте `:app:testDebugUnitTest` и сборки `assembleStaging`/`assembleRelease`.
- Выпустите релиз с пересечением старых/новых pin-ов минимум на один цикл ротации сертификата.
- После завершения ротации удалите устаревший pin в следующем релизе.

### Ограничение текущей реализации

- Реальные production/staging pin-значения в репозитории не хранятся (по требованиям безопасности), поэтому их нужно задавать внешне через секреты CI/локальной среды.
- Без этих данных production/staging intentionally блокируются по fail-closed политике.

## Как собирать нужное окружение

- Debug: `./gradlew :app:assembleDebug`
- Staging: `./gradlew :app:assembleStaging`
- Release: `./gradlew :app:assembleRelease`

## Локальный backend

- Для Android Emulator локальный хост нужно указывать как `10.0.2.2`, поэтому для `debug` используется `http://10.0.2.2:8080/`.
- В `debug` сборке включен cleartext HTTP-трафик, поэтому эмулятор может ходить на локальный `http://10.0.2.2:8080/` без TLS.
- Для физического устройства используйте reverse-проброс порта:
  - `adb reverse tcp:8080 tcp:8080`
  - после этого `debug` сборка будет обращаться к локальному API через USB.

## Debug-поток для тестовых push (FCM)

- Добавьте в проект `app/google-services.json` из Firebase Console (проект для debug-среды).
- Убедитесь, что устройство подключено к интернету, а приложение было запущено хотя бы один раз.
- Найдите token в `logcat` по тегам `PushInitializer` или `DevPulseFcmService`.
- Отправьте тестовое сообщение через Firebase Console (`Cloud Messaging`) на этот token.
- Проверка для E-1:
  - сообщение фиксируется в `logcat` (`Получен push...`);
  - канал `devpulse_updates` создан в системных настройках уведомлений приложения;
  - token сохраняется в локальном `DataStore` (`push_token`), доступен через `PushTokenStore`.

## Внутренний beta релиз

- Для внутреннего релиза используйте `staging` сборку: `./gradlew :app:assembleStaging`.
- Готовый APK: `app/build/outputs/apk/staging/app-staging.apk`.
- Release notes и smoke checklist: `BETA_INTERNAL_RELEASE.md`.
