# Auth Contract Matrix

Этот документ фиксирует data/domain контракт для `AuthAction.Login` и `AuthAction.Register`.

## Endpoint mapping

- `AuthAction.Register` -> `POST /api/v1/clients`
- `AuthAction.Login` -> `POST /api/v1/clients/login`

## Normalized outcomes

- **Login success:** `2xx` от login endpoint.
- **Login failure:** любые API/network ошибки login endpoint, без переключения на register endpoint.
- **Register success:** `2xx` от register endpoint.
- **Register failure:** API/network ошибки register endpoint.

## Error normalization notes

- Login и register обрабатываются независимо, без скрытой подмены действия.
- UI всегда получает действие-специфичное сообщение (`Не удалось войти...` или `Не удалось зарегистрироваться...`).

## Re-login fix note (T4.2)

- Цепочка `register -> logout -> login` теперь всегда использует только login endpoint.
- Конфликтная fallback-ветка удалена из data-слоя, поэтому повторный вход больше не уходит в register-семантику.
