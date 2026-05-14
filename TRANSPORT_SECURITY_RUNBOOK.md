# Transport Security Runbook (S-2)

Документ фиксирует обязательный процесс для production-ready transport security в Android-клиенте.

## Fail-closed принципы

- `staging` и `release` обязаны использовать `https` base URL.
- `staging` и `release` обязаны иметь валидные `sha256/...` pin-ы.
- При отсутствии валидных pin-ов авторизация блокируется в `AuthTransportSecurityGuard`.
- При невалидной production-конфигурации (`http` или без pin-ов) сборка/инициализация сети падает fail-fast.

## Обязательные секреты CI/release

Перед любой `staging/release` сборкой должны быть подставлены секреты:

- `devpulse.stagingCertPins=sha256/<active>,sha256/<next>`
- `devpulse.releaseCertPins=sha256/<active>,sha256/<next>`

Если секреты не подставлены или имеют неверный формат, transport security считается неготовым, а релиз блокируется.

## Checklist перед merge/release

- [ ] Получены актуальный и следующий pin для `staging` от backend/security.
- [ ] Получены актуальный и следующий pin для `release` от backend/security.
- [ ] Секреты pin-ов добавлены в CI variables/secret storage (не в git).
- [ ] Локально/в CI прошли `./gradlew :app:testDebugUnitTest`.
- [ ] Локально/в CI прошли `./gradlew :app:build` с подставленными pin-секретами.
- [ ] Проверен auth flow на `https` endpoint (staging/release).
- [ ] Проверено, что секреты не попали в логи и crash payload.

## Ротация pin-ов

1. Добавьте новый pin, не удаляя предыдущий (`active + next`).
2. Выпустите релиз с обоими pin-ами (перекрытие минимум один цикл сертификата).
3. После подтвержденного переключения сертификата удалите устаревший pin в следующем релизе.
4. Повторите проверку checklist перед каждым выпуском.
