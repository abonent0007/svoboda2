# Свобода 2.0 — приватный релиз v2.0.8 (hardening)

**Версия:** 2.0.8 (versionCode 208) · **Тег:** `v2.0.8` · **Тип:** release (R8), подпись debug keystore (релизный ключ не настроен; та же подпись, что у всех debug-установок).
**Дистрибуция:** репозиторий приватный. Автообновление публичного канала не используется.

## Состав

- Ветка `hardening-2026-11-19`: `6395725` → `231fc5a` (bump до 2.0.8).
- RemoteConfig: CF-Client-Version и список Telegram DC читаются из remote API с дисковым кэшем (24ч) и вшитым embedded fallback — харднкод заменён, поведение по умолчанию идентично.
- 66 unit-тестов зелёные; собрано `:app:assembleRelease` (minify + shrinkResources).

## ВНИМАНИЕ

APK вшивает скомпрометированные ассеты (warp_identity + 16 ключей семян + ключ собственного сервера + общий ключ public_servers). Сборка предназначена для доверенного круга приватного репозитория.

## SHA-256

```
app-arm64-v8a-release.apk   3589779 bytes  SHA256: 250be24d98a9ffd21c6ba15d63655458a40d6cd076c20e1fc948cdf80011ba32
app-armeabi-v7a-release.apk 3584479 bytes  SHA256: b37d608dda8164d0d93fed5abc55663dc0fae66b1e245d61b425d5867cf9db11
app-universal-release.apk   5373574 bytes  SHA256: 02cda5056abba921d28bc6ffc58008b78c90afa40918db5976bf279ef71791ac
```

Установка поверх предыдущей debug-установки — без удаления приложения (та же подпись).
