# Свобода 2.0

Android-клиент VPN на AmneziaWG/WARP без root-прав.
Пакет: `svoboda.runet.abonent0007`.

## Сборка
```
set JAVA_HOME=C:\Users\<you>\dev\jdk17
set PATH=...;C:\Users\<you>\dev\go\bin;...git\cmd;...mingw64\bin
.\gradlew.bat assembleDebug --console=plain --no-daemon
```
Release (с подписью):
```
set KEYSTORE_PATH=...\release.keystore
set KEYSTORE_PASSWORD=...
set KEY_ALIAS=...
set KEY_PASSWORD=...
.\gradlew.bat assembleRelease --console=plain --no-daemon
```
Выходные APK: `app/build/outputs/apk/{debug,release}/`.

## Архитектура
- `:tunnel` — vendored `amneziawg-android` (Apache-2.0, v3.1.20260814) + Go `amneziawg-go`.
- `app` — Compose UI, `VpnController`, `ConnectionManager`, `WarpGenerator` (Cloudflare WARP reg),
  `SeedCatalog` (74 проверенных профилей, default №11), `OwnServerStore`, `Updater`.
- MTU по умолчанию 1240 (пользовательский mandate); IPv4-only по умолчанию.

## Обновления
GitHub Releases: `abonent0007/svoboda2`. Приложение проверяет новые релизы и ставит APK
через FileProvider. Откат: `docs/ROLLBACK.md`.

## Лицензия
Код приложения — по согласованию с автором. Модуль `:tunnel` — Apache-2.0 (amnezia-vpn/amneziawg-android).
