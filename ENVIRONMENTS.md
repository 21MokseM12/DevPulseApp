# Конфигурация окружений

В проекте `app` модуль использует `BuildConfig.BASE_URL` и `BuildConfig.ENVIRONMENT` для выбора API-окружения без правок исходного кода.

## Доступные окружения

- `debug` -> `http://10.0.2.2:8080/`
- `staging` -> `https://staging-api.devpulse.example/`
- `release` -> `https://api.devpulse.example/`

## Как собирать нужное окружение

- Debug: `./gradlew :app:assembleDebug`
- Staging: `./gradlew :app:assembleStaging`
- Release: `./gradlew :app:assembleRelease`

## Локальный backend

- Для Android Emulator локальный хост нужно указывать как `10.0.2.2`, поэтому для `debug` используется `http://10.0.2.2:8080/`.
- Для физического устройства используйте reverse-проброс порта:
  - `adb reverse tcp:8080 tcp:8080`
  - после этого `debug` сборка будет обращаться к локальному API через USB.
