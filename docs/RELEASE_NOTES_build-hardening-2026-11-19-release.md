# Приватный RELEASE-билд `hardening-2026-11-19`

**Тег:** `build-hardening-2026-11-19-release`
**Тип:** release (R8, minifyEnabled, shrinkResources), signature = debug keystore (релизный ключ не настроен; та же подпись, что и у debug-установок на устройстве), НЕ 2.0.7.
**Дистрибуция:** только участники приватного репозитория `abonent0007/svoboda2`. Публичным пользователями эта сборка недоступна; автоапдейтер (неавторизованный GitHub API) на приватном репозитории возвращает 404 — обновление извне невозможно.

## Состав

- Ветка `hardening-2026-11-19` от `diagnostic/instrumentation` (6395725).
- RemoteConfig (CF-Client-Version + список Telegram DC: remote API → дисковый кэш 24ч → вшитый embedded fallback), `TelegramDc` и `WarpGenerator` переведены на него.
- 66 unit-тестов зелёные (`:app:testDebugUnitTest`); `:app:assembleRelease` собран; `tools/net_scan.py` — 40 сетевых узлов, 0 без обработки ошибок.
- ВНИМАНИЕ: APK **вшивает скомпрометированные ассеты** (`warp_identity.json`, `warp_verified_seeds.json` (16 уникальных ключей), `own_servers_seed.json`, `public_servers/*.conf` (общий ключ 3ae60…)). **Распространять только внутри доверенного круга репозитория.** Отзыв и ротация — отдельный этап ремедиации; установка поверх существующей debug-сборки возможна без удаления приложения (та же подпись).
- 2.0.7 НЕ выпущен. RC1/RC2 не изменялись. Это промежуточная milestone-сборка для внутреннего контроля.

## SHA-256

```
app-arm64-v8a-release.apk   3589007 bytes  SHA256: 4fa15c113bffa011c5554a612d464781851d0afa2903ef002acc6c6f9bd12c72
app-armeabi-v7a-release.apk 3583707 bytes  SHA256: 76cda02ef8f5f306c192a48eed46f2e191fdca6bdf2a19a6eed4abcca98da65d
app-universal-release.apk   5372802 bytes  SHA256: f4e9b5bd8d3a2ee6d34e6c3066a170df65b1ef5cf75767711e43fc44811a2004
```

## Проверка после установки

1. Установка поверх debug-сборки (та же debug-подпись) — ОК.
2. Подключение по DPI работает как раньше (поведение не менялось функционально).
3. Тесты: `.\gradlew.bat :app:cleanTestDebugUnitTest :app:testDebugUnitTest` → 66/66.
