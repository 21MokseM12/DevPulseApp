# Re-login Stability Checklist

Финальный stability pass после фиксов re-login.

## Проверки startup/state

- После `logout` стартовый маршрут возвращается на `Auth`.
- После успешного re-login стартовый маршрут снова `Subscriptions`.
- Сессия корректно сохраняется заново после повторного входа.
- После restart между `logout` и повторным `login` экран `Auth` остается консистентным и допускает повторный вход.

## Проверки смежных экранов

- `Subscriptions` открывается после re-login без fallback-ошибок.
- Переход `Subscriptions -> Updates -> Subscriptions` остается рабочим.
- Переход `Subscriptions -> Settings -> Back` остается рабочим.

## Автотесты

- Unit:
  - `MainLogoutLifecycleIntegrationTest.logoutThenRelogin_restoresSubscriptionsDestinationAndSession`
  - `MainLogoutLifecycleIntegrationTest.processDeath_afterLogout_keepsAuthDestinationOnNextStart`
  - `MainLogoutLifecycleIntegrationTest.appRestart_afterRelogin_restoresSubscriptionsDestinationFromPersistedSession`
- Instrumentation:
  - `DevPulseAppTest.reloginAfterLogout_keepsSubscriptionsUpdatesAndSettingsStable`
  - `DevPulseAppTest.reloginAfterLogout_withRestartBetweenAuthSteps_staysStable`

## Process-death проверка (ручной smoke)

1. Выполнить `register/login -> logout` на устройстве.
2. На экране `Auth` ввести валидные данные, не нажимая кнопку входа.
3. Убить процесс приложения (`adb shell am kill com.devpulse.app`) и заново открыть приложение.
4. Проверить, что приложение остается на `Auth`, затем выполнить `login` и убедиться в переходе на `Subscriptions`.
5. Пройти smoke переходы `Subscriptions -> Updates -> Subscriptions` и `Subscriptions -> Settings -> Back`.
