# Auth Contract Matrix

Этот документ фиксирует data/domain контракт для `AuthAction.Login` и `AuthAction.Register`.

## Endpoint mapping

- `AuthAction.Register` -> `POST /api/v1/clients`
- `AuthAction.Login` -> `POST /api/v1/clients/login`

## Login fallback policy

Когда `POST /api/v1/clients/login` недоступен, клиент применяет fallback на `POST /api/v1/clients`.

Fallback включается только если login endpoint явно отсутствует:

- `404`, `405`, `501`
- или `400 Bad Request` с маркерами отсутствующего endpoint (`not found`, `no static resource`, `method not allowed`, `not implemented`)

## Normalized outcomes

- **Login success:** `2xx` от login endpoint.
- **Login success через fallback:** `2xx` от register endpoint.
- **Login success через fallback-conflict:** `400` от register endpoint с маркером `already exists` (legacy backend семантика).
- **Login failure:** любые другие API/network ошибки после login/fallback.
- **Register success:** `2xx` от register endpoint.
- **Register failure:** API/network ошибки register endpoint.

## Error normalization notes

- `already exists` в fallback-login нормализуется в успешный вход.
- `not found` и неформатированный `400` не маскируются и возвращаются как ошибка выбранного действия.
- UI всегда получает действие-специфичное сообщение (`Не удалось войти...` или `Не удалось зарегистрироваться...`).

## Re-login root-cause note (T4.1)

- Цепочка `register -> logout -> login` ожидает чистый `POST /api/v1/clients/login`.
- Фактическая реализация могла переключать login в fallback-register при `404/405/501`.
- Если fallback-register отвечал `400` без распознаваемых marker-ов `already exists`, ошибка возвращалась в UI как `Bad Request`.
- Корень проблемы локализован в `DefaultDevPulseRemoteDataSource.loginClient` и `AuthFallbackPolicy`.
