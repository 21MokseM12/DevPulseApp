# Instrumented Smoke Matrix

Документ фиксирует минимальный критичный набор Compose/instrumented smoke-сценариев, которые блокируют merge.

## Набор smoke-сценариев

1. **Cold start -> Auth**
   - Проверка стартовой маршрутизации без сессии.
   - Ожидаемый результат: виден экран `Auth`.
2. **Auth -> Subscriptions**
   - Проверка happy-path авторизации и перехода в защищенную зону.
   - Ожидаемый результат: открыт экран `Subscriptions`.
3. **Subscriptions add/remove**
   - Проверка добавления и удаления подписки.
   - Ожидаемый результат: ссылка появляется в списке и удаляется через confirm без зависания состояния.
4. **Subscriptions -> Updates + mark-read**
   - Проверка навигации к ленте и изменения unread-счетчика.
   - Ожидаемый результат: unread уменьшается после `mark as read`.

## Как запускать локально

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.devpulse.app.ui.DevPulseAppTest
```

## Диагностика при падениях

- JUnit XML/HTML отчеты: `app/build/outputs/androidTest-results/` и `app/build/reports/androidTests/`.
- `logcat`: `app/build/reports/androidTests/logcat-smoke.txt`.
- Скриншоты smoke-шагов: `app/build/outputs/androidTest-results/smoke-screenshots/`.
