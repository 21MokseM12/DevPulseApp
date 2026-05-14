# Android MVP Acceptance Report (R-0)

Дата отчета: 2026-05-14
Ветка: `main`
Релиз-кандидат: Android MVP (`staging`)
Версия приложения: `1.15.0` (текущая в `app/build.gradle.kts`)

## Объем приемки

- Формализованный checklist: `release/android-mvp-acceptance-checklist.md`
- Обязательные MVP сценарии:
  - auth (success/error/session restore);
  - subscriptions CRUD;
  - notifications feed + unread-count + mark-read;
  - push + deep-link;
  - обработка ошибок без crash/ANR.

## Что проверено в доступном окружении (автоматизируемая часть)

| Gate | Команда | Результат |
| --- | --- | --- |
| Build gate | `./gradlew build` | PASS (см. локальный прогон в рамках задачи R-0) |
| Release decision policy | `./gradlew :app:testDebugUnitTest --tests "*ReleaseAcceptance*"` | PASS |

## Manual gates (physical device required)

Ниже перечислены проверки, которые нельзя закрыть в текущем CI/локальном окружении без физического устройства и реального push-контура:

| Gate | Устройство/окружение | Статус | Инструкция |
| --- | --- | --- | --- |
| Android 13+ smoke | Физическое устройство Android 13+ | PENDING_MANUAL | Выполнить AC-01..AC-07 из checklist |
| Android <13 smoke (опционально, но желательно) | Физическое устройство Android 12 и ниже | PENDING_MANUAL | Выполнить AC-08 из checklist |
| Push e2e | Реальный backend + FCM routing | PENDING_MANUAL | Отправить тестовый push, проверить deep-link и запись в Updates |

## Найденные дефекты и ограничения

- По автоматизируемой части блокирующих дефектов не выявлено.
- Ограничение: финальное release-решение зависит от ручного прогона на физическом устройстве.

## Текущее решение

- Предварительное решение: `NO-GO (условный)` до закрытия всех обязательных manual gates.
- Условие перехода к `GO`: AC-01..AC-07 имеют статус `PASS`, blocker/major отсутствуют.

## Шаблон для заполнения после ручного прогона

- Дата и исполнитель:
- Устройство 1 (модель/версия Android):
- Устройство 2 (если есть):
- Результаты AC-01..AC-09:
- Список дефектов (severity, шаги, workaround):
- Финальное решение (`GO`/`NO-GO`) и обоснование:

