# Startup Lifecycle Report

Дата отчета: 2026-05-15  
Область: lifecycle поведения старта приложения и переходов `Auth`/`Subscriptions`

## Цель отчета

Отдельно зафиксировать сценарии startup lifecycle, результаты и доказательства без вложения в диагностические markdown.

## Сценарии и результаты

### Сценарий 1. Cold start без сессии

- **Предусловия:** в `SessionStore` нет сохраненной сессии.
- **Шаги:** запуск приложения и инициализация `MainViewModel`.
- **Ожидаемо:** `startupDestination = Auth`.
- **Результат:** PASS.
- **Доказательство:** `MainViewModelTest.init_withoutSession_routesToAuth`.

### Сценарий 2. Cold start с сохраненной сессией

- **Предусловия:** в `SessionStore` есть сохраненная сессия.
- **Шаги:** запуск приложения; bootstrap возвращает `hasCachedSession = false`, но стор содержит актуальную сессию.
- **Ожидаемо:** маршрут остается в `Subscriptions`.
- **Результат:** PASS.
- **Доказательство:** `MainLogoutLifecycleIntegrationTest.coldStart_withSessionInStore_routesToSubscriptions_evenIfBootstrapHasNoSession`.

### Сценарий 3. Warm start после auth success (quick relaunch)

- **Предусловия:** пользователь только что прошел login/register, процесс приложения быстро перезапущен.
- **Шаги:** создать новый `MainViewModel` на том же `SessionStore`.
- **Ожидаемо:** `hasCachedSession = true`, старт в `Subscriptions`.
- **Результат:** PASS.
- **Доказательство:** `MainViewModelTest.quickRelaunch_afterAuthSuccess_keepsMainDestination`.

### Сценарий 4. Warm start после пересоздания (rotation)

- **Предусловия:** во время auth success происходит пересоздание `MainViewModel`.
- **Шаги:** повторная инициализация `MainViewModel`, затем завершение сохранения сессии.
- **Ожидаемо:** финальная маршрутизация в `Subscriptions` без отката в `Auth`.
- **Результат:** PASS.
- **Доказательство:** `MainViewModelTest.rotationDuringAuthSuccess_keepsMainNavigationAfterRecreation`.

### Сценарий 5. Logout lifecycle cleanup

- **Предусловия:** пользователь находится в main flow с активной сессией.
- **Шаги:** выполнить logout и lifecycle cleanup.
- **Ожидаемо:** очистка `session`, `updates`, `push`, `permission` и маршрут в `Auth`.
- **Результат:** PASS.
- **Доказательство:** `MainLogoutLifecycleIntegrationTest.logout_fromMainFlow_usesLifecycleCleanupAndRoutesToAuth`.

## Итог по startup lifecycle

- Startup lifecycle для автоматизируемых сценариев (`debug` env) подтвержден тестами и стабилен для cold/warm start путей.
- Для `staging`/`release` остаются ручные startup-проверки на физическом устройстве, их статус отражен в `release/startup-operational-coverage-matrix.md`.
