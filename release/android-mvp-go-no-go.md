# Android MVP Go/No-Go Decision (R-0)

Дата: 2026-05-14
Решение: `NO-GO`
Статус решения: предварительное, до закрытия manual gates

## Основания решения

1. Формальный acceptance checklist создан и зафиксирован:
   - `release/android-mvp-acceptance-checklist.md`
2. Acceptance report создан:
   - `release/android-mvp-acceptance-report.md`
3. Автоматизируемый build gate выполнен:
   - `./gradlew build` -> PASS
4. Автоматизирована формальная проверка release decision:
   - `ReleaseAcceptanceDeciderTest` (unit) -> PASS
   - `ReleaseAcceptanceDocsIntegrationTest` (integration) -> PASS
5. Обязательные ручные проверки на физическом устройстве еще не закрыты:
   - Android 13+ smoke;
   - push e2e + deep-link;
   - (опционально) Android <13 smoke.

## Условия для переключения на GO

- Все обязательные пункты AC-01..AC-07 из checklist отмечены `PASS`.
- Blocker/major дефекты отсутствуют либо имеют согласованное релизное решение.
- В acceptance report добавлены фактические результаты ручного прогона.

## Ответственный за финализацию

- QA/Android владелец релизного прогона на устройстве обновляет:
  - `release/android-mvp-acceptance-report.md` (фактические результаты);
  - этот файл (решение `GO` и дата утверждения).

