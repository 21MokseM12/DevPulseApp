# Android MVP Release Acceptance Checklist (R-0)

Дата обновления: 2026-05-14
Владелец: Android команда DevPulse
Область: только Android MVP

## Статусы

- `PASS` - проверка пройдена.
- `FAIL` - проверка не пройдена.
- `PENDING_MANUAL` - требуется ручной прогон на физическом устройстве.
- `N/A` - неприменимо для конкретного прогона.

## Coverage baseline

- Требования Android-части MVP по `VKR.pdf`: auth, subscriptions CRUD, updates feed, push/deep-link, ошибки.
- Контрактная сверка API: `bot/contracts/openapi.yaml`.

## Формальный checklist

| ID | Сценарий | Ожидаемый результат | Статус | Доказательство/ссылка |
| --- | --- | --- | --- | --- |
| AC-01 | Установка `staging` APK на Android 13+ | Приложение устанавливается и запускается без crash | PENDING_MANUAL | См. раздел "Порядок ручного прогона" |
| AC-02 | Auth success + session restore | Успешный вход, сессия восстанавливается после cold start | PENDING_MANUAL | См. раздел "Порядок ручного прогона" |
| AC-03 | Auth validation/network errors | Понятные user-friendly ошибки без поломки flow | PENDING_MANUAL | См. раздел "Порядок ручного прогона" |
| AC-04 | Subscriptions CRUD | Добавление/удаление/чтение подписок работает стабильно | PENDING_MANUAL | См. раздел "Порядок ручного прогона" |
| AC-05 | Notifications feed + unread-count + mark-read | Лента загружается, unread-count и mark-read работают | PENDING_MANUAL | См. раздел "Порядок ручного прогона" |
| AC-06 | Push delivery + deep-link | Push отображается, тап ведет на экран Updates | PENDING_MANUAL | См. раздел "Порядок ручного прогона" |
| AC-07 | Runtime permission (Android 13+) | `POST_NOTIFICATIONS` запрашивается корректно | PENDING_MANUAL | См. раздел "Порядок ручного прогона" |
| AC-08 | Smoke на Android <13 (если доступно) | Базовые сценарии без runtime запроса уведомлений | PENDING_MANUAL | Опциональный gate, но рекомендован |
| AC-09 | Regression build gate | Команда `./gradlew build` завершается успешно | PASS | Лог локального прогона в acceptance report |
| AC-10 | Release decision policy regression | Unit + integration тесты release decision проходят | PASS | `ReleaseAcceptanceDeciderTest`, `ReleaseAcceptanceDocsIntegrationTest` |

## Порядок ручного прогона

1. Собрать и установить `staging` APK:
   - `./gradlew :app:assembleStaging`
   - APK: `app/build/outputs/apk/staging/app-staging.apk`
2. На Android 13+ выполнить AC-01..AC-07, зафиксировать факты (скриншоты/видео/логи).
3. При наличии второго устройства (<13) выполнить AC-08.
4. Для push-теста отправить тестовый payload через backend/staging контур.
5. Все найденные дефекты классифицировать по severity:
   - `blocker` - блокирует релиз.
   - `major` - критично ухудшает MVP сценарий, релиз обычно блокируется.
   - `minor` - допустимо с documented workaround.

## Правило принятия решения

- `GO`: все blocker/major закрыты, обязательные AC-01..AC-07 в `PASS`.
- `NO-GO`: есть хотя бы один blocker/major или не закрыты обязательные manual gates.

