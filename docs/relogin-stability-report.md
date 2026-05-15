# Re-login Stability Report

Дата: 2026-05-15

## Scope

- Проверка устойчивости сценария `logout -> login` после правок lifecycle.
- Явная проверка restart/process-death между auth-шагами.
- Финальный smoke по маршрутам `Subscriptions`, `Updates`, `Settings`.

## Автоматические прогоны

- `./gradlew :app:testDebugUnitTest --tests "com.devpulse.app.ui.auth.AuthErrorMessageMapperTest" --tests "com.devpulse.app.ui.main.MainLogoutLifecycleIntegrationTest"` -> PASS
- `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.devpulse.app.ui.DevPulseAppTest#reloginAfterLogout_keepsSubscriptionsUpdatesAndSettingsStable,com.devpulse.app.ui.DevPulseAppTest#reloginAfterLogout_withRestartBetweenAuthSteps_staysStable` -> PASS
- `./gradlew build` -> PASS

## Результат

- Статус: PASS
- Ошибок по re-login lifecycle не обнаружено.
- Edge-cases restart/process-death закреплены автотестами и checklist-процедурой.

## Ручной device smoke (process-death)

1. Авторизоваться и выполнить `logout`.
2. На `Auth` экране заполнить данные, затем убить процесс (`adb shell am kill com.devpulse.app`).
3. Перезапустить приложение, повторить login и пройти маршруты `Subscriptions -> Updates -> Subscriptions` и `Subscriptions -> Settings -> Back`.
4. Ожидание: отсутствует ложный `Bad Request`, маршруты стабильны.
