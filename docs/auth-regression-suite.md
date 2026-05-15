# Auth Regression Suite

Единый вход для полного auth regression набора:

```bash
./scripts/auth-regression-gate.sh
```

## Что запускает gate

1. Auth unit/integration набор (`testDebugUnitTest` c фильтром):
   - `com.devpulse.app.ui.auth.*`
   - `com.devpulse.app.data.repository.DefaultAuthRepositoryTest`
   - `com.devpulse.app.data.remote.Auth*`
2. Auth UI instrumentation набор:
   - `com.devpulse.app.ui.auth.AuthUiIntegrationTest`

## Назначение

- Дает один стабильный вход для проверки auth перед merge/release.
- Фиксирует explicit regression-сценарии по login/register, validation, network retry и lifecycle cancellation.
