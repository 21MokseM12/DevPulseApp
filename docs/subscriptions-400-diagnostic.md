# Диагностика 400 для пустого списка подписок

## Сценарий воспроизведения
- Условие: новый пользователь без подписок, локальный кэш пустой.
- Шаг: первый запрос списка подписок через `getSubscriptions(forceRefresh = false)`.
- Наблюдение: backend отвечает `400 Bad Request` вместо `200 []`.

## Локализация источника
- `API` слой: `GET /api/v1/links`.
- `Transport`: HTTP `400`.
- `Body` (repro payload): `{"description":"No subscriptions found for this client","code":"EMPTY_SUBSCRIPTIONS"}`.
- `Mapper`: `ApiErrorMapper.mapApiError(...)` превращает ответ в `ApiErrorKind.BadRequest`.
- `Repository`: `DefaultSubscriptionsRepository.getSubscriptions(...)` получает `RemoteCallResult.ApiFailure` и отдает `SubscriptionsResult.Failure`.
- `UI`: `SubscriptionsViewModel` на ветке failure выставляет `errorMessage`, из-за чего экран уходит в error-state.

## Подтверждающий интеграционный тест
- `DefaultSubscriptionsRepositoryMockWebServerTest.getSubscriptions_repro400_forEmptyAccount_localizesEndpointStatusAndBody`.
- Тест фиксирует метод, endpoint, статус и распарсенный payload, чтобы сценарий был воспроизводимым.
