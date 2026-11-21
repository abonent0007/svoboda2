# Приватный debug-билд `hardening-2026-11-19` — заметки к релизу

**Тег:** `build-hardening-2026-11-19-debug`
**Тип:** приватный debug build (debug signing), не релиза 2.0.7, устанавливается вручную.
**Кому:** только владелец и явно добавленные коллабораторы.

## Что внутри

- Ветка `hardening-2026-11-19` от `diagnostic/instrumentation` (6395725).
- Исправления: RemoteConfig (CF-Client-Version + список Telegram DC — remote API + дисковый кэш 24ч + вшитый fallback), `TelegramDc` и `WarpGenerator` переведены на него.
- 66 unit-тестов, 0 падений (`.\gradlew.bat :app:testDebugUnitTest`); `assembleDebug` собран.
- `tools/net_scan.py`: 40 сетевых узлов, 0 без обработки ошибок.
- ВНИМАНИЕ: APK вшивает скомпрометированные ассеты (`warp_identity.json`, `warp_verified_seeds.json` — 16 уникальных ключей, `own_servers_seed.json`, `public_servers/*.conf` — общий ключ). Подписи debug. Предназначена для доверенного круга.

## SHA-256

```
app-arm64-v8a-debug.apk   20900186 bytes  SHA256: 2bfb9ae3f2ab13acecea3827620feede7b38e06f4288e7c98aabfbf693afdfb3
app-armeabi-v7a-debug.apk 20831310 bytes  SHA256: 5f63da08f097270819c9715290179054d73751c4e72c6540d02e7a864f85d5f2
app-universal-debug.apk   22836597 bytes  SHA256: 5f36d3b04b9566dca1b2e4e9156731ea82a5201f17d110ee39d20d38c1533390
```

## Инструкция (приватный релиз, после перехода репозитория в Private)

1. `github.com/abonent0007/svoboda2/releases/new`
2. Choose a tag → `build-hardening-2026-11-19-debug` → Create new tag.
3. Release title: `Свобода 2.0 — private debug build (hardening-2026-11-19)`
4. Target: branch `hardening-2026-11-19` (либо main после merge).
5. Description: вставить ЭТОТ файл (без изображений).
6. Attach: `app-universal-debug.apk` (обязательно), `app-arm64-v8a-debug.apk`, `app-armeabi-v7a-debug.apk` (по желанию).
7. Pre-release checkbox: ВКЛ (чтобы не зваться latest и не попадать в автоапдейтер).
8. Publish release.

## После релиза

- Автоапдейтер `Updater.kt` проверяет `releases/latest` — приватный репозиторий с pre-release: публичные устройства, тянущие релизы PUBLIC-репозитория, релиз не затрагивают.
- Доли нужна ремедиация (Private → ротация Frankfurt/WARP → purge → новый чистый билд). Этот приватный debug-билд — промежуточная веха, не релиз 2.0.7.
