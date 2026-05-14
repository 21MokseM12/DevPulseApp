# CI Quality Matrix

Этот документ фиксирует обязательный минимальный quality-набор перед merge и его соответствие локальным проверкам.

## Local to CI mapping

| Local command | CI gate | Merge decision |
| --- | --- | --- |
| `./gradlew :app:qualityCheck` | `Quality (ktlint + lint)` | Blocking |
| `./gradlew :app:testDebugUnitTest` | `Unit Tests` | Blocking |
| `./gradlew :app:testDebugUnitTest --tests "*Contract*" --tests "*MockWebServer*"` | `Contract Check` | Blocking |
| `./gradlew :app:assembleDebug` | `Assemble Debug` | Blocking |
| `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.devpulse.app.ui.DevPulseAppTest` | `Instrumented Smoke (Compose)` | Blocking |
| `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.devpulse.app.data.local.db.AppDatabaseMigrationTest` | `Instrumented Migration (Room)` | Blocking |

## Merge policy

- Blocking: все quality gates из матрицы выше. Любой `FAIL` блокирует merge.
- Warning-only: отсутствуют в текущем MVP quality profile.
- Retry policy: потенциально нестабильные тестовые шаги (`Unit Tests`, `Contract Check`, `Instrumented Smoke`) запускаются с одной автоматической повторной попыткой.
- Fail-fast: при падении blocking gate текущий job завершается ошибкой, что сохраняет время CI.

## Где смотреть отчеты

- Unit/Contract результаты: артефакты `quality-unit` и `quality-contract`.
- Lint/Ktlint отчеты: артефакт `quality-quality`.
- Assemble APK: артефакт `quality-assemble`.
- Instrumented smoke диагностика: артефакт `quality-instrumented-smoke` (JUnit XML, HTML-репорт, logcat, скриншоты).
- Instrumented migration диагностика: артефакт `quality-instrumented-migration` (`migration-diagnostics.md`, `logcat-migration.txt`, JUnit XML + HTML reports).
- Enforcement отчета диагностируемости: артефакт `quality-migration-diagnostics-validation` и блок `Migration diagnostics validation` в `GitHub Step Summary`.
- Сводка quality pipeline: артефакт `quality-summary` и раздел в `GitHub Step Summary`.

## Рекомендуемая локальная последовательность перед PR

1. `./gradlew :app:qualityCheck`
2. `./gradlew :app:testDebugUnitTest`
3. `./gradlew :app:testDebugUnitTest --tests "*Contract*" --tests "*MockWebServer*"`
4. `./gradlew :app:assembleDebug`
5. `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.devpulse.app.ui.DevPulseAppTest`
6. `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.devpulse.app.data.local.db.AppDatabaseMigrationTest`
