# Конфигурация окружений

В проекте `app` модуль использует `BuildConfig.BASE_URL` и `BuildConfig.ENVIRONMENT` для выбора API-окружения без правок исходного кода.

## Доступные окружения

- `debug` -> `http://10.0.2.2:8080/`
- `staging` -> `https://staging-api.devpulse.example/`
- `release` -> `https://api.devpulse.example/`

## Как собирать нужное окружение

- Debug: `./gradlew :app:assembleDebug`
- Staging: `./gradlew :app:assembleStaging`
- Release: `./gradlew :app:assembleRelease`

## Локальный backend

- Для Android Emulator локальный хост нужно указывать как `10.0.2.2`, поэтому для `debug` используется `http://10.0.2.2:8080/`.
- В `debug` сборке включен cleartext HTTP-трафик, поэтому эмулятор может ходить на локальный `http://10.0.2.2:8080/` без TLS.
- Для физического устройства используйте reverse-проброс порта:
  - `adb reverse tcp:8080 tcp:8080`
  - после этого `debug` сборка будет обращаться к локальному API через USB.

## Firebase/FCM конфигурация

- `google-services` plugin применяется автоматически только если найден один из файлов:
  - `app/google-services.json`
  - `app/src/debug/google-services.json`
  - `app/src/staging/google-services.json`
  - `app/src/release/google-services.json`
- Если конфигурация отсутствует, сборка продолжит работать, но FCM token запрашиваться не будет (лог: `FCM отключен: google-services.json не найден`).

## Безопасный provisioning `google-services.json`

- Запросите файл у владельца Firebase-проекта (не через публичные чаты/тикеты без ограничений доступа).
- Сохраняйте файл только локально в одном из путей выше.
- Не коммитьте файл в git: пути добавлены в `.gitignore`.
- Для QA рекомендуется отдельный Firebase-проект и отдельный `google-services.json` для `staging`.

## Smoke-проверка push на реальном устройстве

1. Установите `staging` или `debug` сборку с валидным `google-services.json`.
2. Откройте приложение хотя бы один раз с интернетом.
3. Проверьте получение token в `logcat` по тегам `PushInitializer`/`DevPulseFcmService`.
4. Отправьте тестовый push на token через Firebase Console (`Cloud Messaging`).
5. Проверьте ожидаемый e2e-flow:
   - payload принят сервисом;
   - событие сохранено в Room;
   - показано системное уведомление;
   - тап по уведомлению открывает экран `Updates`.

### Android 13+

- Разрешение `POST_NOTIFICATIONS` должно быть запрошено и выдано.
- Без разрешения уведомление не отображается (ожидаемое поведение).

### Android ниже 13

- Runtime-разрешение на уведомления не требуется.
- Уведомление отображается при включенных уведомлениях приложения.

### Проверка устойчивости payload handling

- Payload без `url/link` или с невалидным URL игнорируется без crash.
- Payload без `content/body/description` сохраняется с fallback-сообщением: `Проверьте новые изменения по отслеживаемой ссылке.`
- Канал `devpulse_updates` должен быть создан при старте приложения и доступен в системных настройках.

## Внутренний beta релиз

- Для внутреннего релиза используйте `staging` сборку: `./gradlew :app:assembleStaging`.
- Готовый APK: `app/build/outputs/apk/staging/app-staging.apk`.
- Release notes и smoke checklist: `BETA_INTERNAL_RELEASE.md`.
