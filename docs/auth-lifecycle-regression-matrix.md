# Auth Lifecycle Regression Matrix

Матрица фиксирует критические сценарии жизненного цикла auth и ожидаемый итог.

## Сценарии

- `register -> logout -> login` -> успешный re-login, `startupDestination = Subscriptions`.
- `register -> logout -> login (NotFound)` -> вход отклоняется, остаемся на `Auth`, показывается actionable ошибка про недоступный сервис.
- `login -> logout -> login` -> повторный вход не ломает session lifecycle и повторно возвращает на `Subscriptions`.
- `unregister (success)` -> полный local cleanup (`session/updates/push/permission`) и переход на `Auth`.
- `unregister (timeout)` -> local state не очищается, перехода на `Auth` нет, показывается ошибка.

## Автоматизация

- Unit: `MainViewModelTest.loginLogoutLogin_cycle_keepsStartupDestinationConsistent`.
- Integration:
  - `AuthLifecycleRegressionIntegrationTest.registerLogoutLogin_chain_reachesSubscriptionsWithoutRegression`
  - `AuthLifecycleRegressionIntegrationTest.reloginFailure_afterLogout_keepsAuthDestinationAndShowsMappedError`
  - `SettingsLifecycleFlowIntegrationTest.unregister_success_runsLifecycleFlow_networkThenLocalReset`
  - `SettingsLifecycleFlowIntegrationTest.unregister_timeout_keepsUiResponsive_withoutLocalResetOrNavigation`
