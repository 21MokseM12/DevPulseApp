# Re-login Stability Checklist

Финальный stability pass после фиксов re-login.

## Проверки startup/state

- После `logout` стартовый маршрут возвращается на `Auth`.
- После успешного re-login стартовый маршрут снова `Subscriptions`.
- Сессия корректно сохраняется заново после повторного входа.

## Проверки смежных экранов

- `Subscriptions` открывается после re-login без fallback-ошибок.
- Переход `Subscriptions -> Updates -> Subscriptions` остается рабочим.
- Переход `Subscriptions -> Settings -> Back` остается рабочим.

## Автотесты

- Unit: `MainLogoutLifecycleIntegrationTest.logoutThenRelogin_restoresSubscriptionsDestinationAndSession`.
- Integration: `DevPulseAppTest.reloginAfterLogout_keepsSubscriptionsUpdatesAndSettingsStable`.
