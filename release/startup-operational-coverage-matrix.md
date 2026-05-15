# Startup Operational Coverage Matrix

Дата обновления: 2026-05-15  
Область: startup routing + session recovery для Android MVP

## Оси покрытия

- **Environment:** `debug`, `staging`, `release`.
- **Startup mode:** `cold start`, `warm start`.
- **Статус:** `PASS`, `PENDING_MANUAL`.

## Матрица подтвержденного покрытия

| Environment | Startup mode | Подтвержденный сценарий | Статус | Подтверждение | Наблюдения |
| --- | --- | --- | --- | --- | --- |
| `debug` | `cold start` | Первый запуск без сессии маршрутизирует в `Auth`; запуск с сохраненной сессией маршрутизирует в `Subscriptions` | PASS | `MainViewModelTest.init_withoutSession_routesToAuth`, `MainLogoutLifecycleIntegrationTest.coldStart_withSessionInStore_routesToSubscriptions_evenIfBootstrapHasNoSession` | Startup destination определяется состоянием `SessionStore`, а не только bootstrap-флагом `hasCachedSession` |
| `debug` | `warm start` | Быстрый relaunch/ротация после успешной авторизации сохраняют маршрут в `Subscriptions` | PASS | `MainViewModelTest.quickRelaunch_afterAuthSuccess_keepsMainDestination`, `MainViewModelTest.rotationDuringAuthSuccess_keepsMainNavigationAfterRecreation` | При пересоздании `MainViewModel` состояние сессии консистентно и не откатывается в `Auth` |
| `staging` | `cold start` | Установка `staging` APK и первый запуск на Android 13+ | PENDING_MANUAL | `release/android-mvp-acceptance-checklist.md` (AC-01, AC-02), `release/android-mvp-acceptance-report.md` | Требуется физическое устройство и реальный staging-контур |
| `staging` | `warm start` | Повторный запуск с активной сессией после успешного входа | PENDING_MANUAL | `release/android-mvp-acceptance-checklist.md` (AC-02), `release/android-mvp-acceptance-report.md` | Закрывается в том же ручном прогоне, что и AC-01..AC-07 |
| `release` | `cold start` | Первый запуск release-сборки в production-контуре | PENDING_MANUAL | `ENVIRONMENTS.md` (release env + TLS pinning policy), `release/android-mvp-go-no-go.md` | До релиза не проводится в автоматическом окружении; зависит от production provisioning |
| `release` | `warm start` | Перезапуск release-сборки с сохраненной сессией | PENDING_MANUAL | `ENVIRONMENTS.md`, `release/android-mvp-go-no-go.md` | Проверяется в финальном pre-release/manual smoke цикле |

## Критерий закрытия матрицы

- Матрица считается полностью закрытой, когда строки `staging` и `release` переходят в `PASS` с приложением фактических device evidence (видео/скриншоты/логи) в release-отчет.
