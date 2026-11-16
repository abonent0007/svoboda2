# Свобода 2.0

Android no-root VPN-клиент на **AmneziaWG / WARP** с per-app split-tunnel,
раздельными настройками подключений и автообновлением с GitHub Releases.

`svoboda.runet.abonent0007` · minSdk 26 · targetSdk 35 · arm64-v8a + armeabi-v7a

## Скриншоты

| Главный экран | Меню (настройки) |
|---|---|
| ![Главный экран](docs/shots/release_main.png) | ![Меню](docs/shots/release_drawer.png) |

Подключённое состояние:

![Подключено](docs/shots/release_connected.png)

## Возможности

- **Транспорт:** AmneziaWG (свои серверы) и WARP (генерация конфигурации через Cloudflare API).
- **Профили:** 74 seed-конфига (default №11), собственные серверы, история и fingerprint профилей.
- **Безопасность:** конфиги шифруются AES-256-GCM через Android Keystore; `allowBackup=false`.
- **Split-tunnel:** ALL / ONLY_SELECTED / ALL_EXCEPT_SELECTED + пресеты (RU-приложения, мессенджеры, браузеры).
- **Сеть:** DNS и IPv6 per-profile, MTU 1200–1480 (по умолчанию 1420) с быстрыми пресетами 1380–1480.
- **Устойчивость:** ReconnectCoordinator (backoff 2/5/10/20/40/60 c + джиттер), health-check туннеля,
  проверка handshake **и** HTTPS-proof перед публикацией `CONNECTED` (без ложных состояний).
- **Android Lifecycle:** `onRevoke`, Always-on callback, Foreground Service (`systemExempted`), восстановление после смерти процесса.
- **Способы подключения:** AWG и WARP доступны; DPI и AUTO — в разработке (кнопки неактивны).
- **Автообновление:** проверка `releases/latest` при запуске, скачивание APK и передача системному установщику;
  отключается в меню (`Автообновление`).

## Сборка

```bat
set JAVA_HOME=C:\path\to\jdk17
set PATH=%JAVA_HOME%\bin;C:\path\to\go\bin;C:\path\to\git\cmd;C:\path\to\git\usr\bin;C:\path\to\mingw64\bin;%PATH%
gradlew.bat assembleDebug --console=plain --no-daemon
```

> `git\usr\bin` и `mingw64\bin` обязательны: без них NDK make падает на `uname -m`.

Release-сборка (подпись из окружения):

```bat
set KEYSTORE_PATH=...\release.keystore
set KEYSTORE_PASSWORD=...
set KEY_ALIAS=...
set KEY_PASSWORD=...
gradlew.bat assembleRelease --console=plain --no-daemon
```

Артефакты: `app/build/outputs/apk/{debug,release}/` (split APK по ABI + universal).

## Архитектура

- `:tunnel` — vendored `amneziawg-android` (Apache-2.0, v3.1.20260814) + `amneziawg-go`;
  патч lifecycle: `LifecycleCallback` (`onVpnRevoked` / `onAlwaysOnTriggered`) и `onRevoke()`.
- `app` — Compose UI, `VpnController`, `ConnectionManager`, `WarpGenerator`, `SeedCatalog`,
  `OwnServerStore`, `ReconnectCoordinator`, `ConnectionStateRepository`, `Updater`.
- `third_party/` — исходники DPI-движка (см. ниже) с закреплёнными SHA и лицензиями.

## DPI (roadmap)

Цель — вернуть локальный no-root DPI-bypass (YouTube/Telegram), не затрагивая VPN-ядро.

- Эталон: **ByeByeDPI v1.8.2** (архитектура `VpnService TUN → hev-socks5-tunnel → ciadpi SOCKS5`).
- Исходники получены и закреплены (MIT/BSD), манифест: `third_party/DPI_SOURCES.lock.json`.
- Документация: `docs/BYE_BYE_DPI_PORT_MAP.md`, `docs/DPI_STATUS.md`, `docs/BYEDPI_STRATEGY_LAB.md`.
- Контрольная стратегия BDP-00:
  `-Ku -a3 -An -Kt,h -d1 -s0+s -d3+s -s6+s -d9+s -s12+s -d15+s -s20+s -d25+s -s30+s -d35+s -An`.

## Обновления

Приложение проверяет `https://api.github.com/repos/abonent0007/svoboda2/releases/latest`.
Версии сравниваются численно; откат не предлагается. Скачанный APK устанавливается через
системный установщик (FileProvider). Откат вручную: `docs/ROLLBACK.md`.

## Лицензии

- `:tunnel` / `amneziawg-go` — Apache-2.0 / MIT (AmneziaVPN, WireGuard LLC).
- ByeDPI (`third_party/byedpi`) — MIT.
- hev-socks5-tunnel, hev-socks5-core, hev-task-system, yaml — MIT; lwIP — BSD-3-Clause.
- Код ByeByeDPI (GPL-3.0) **не** копировался; JNI/интеграция будет собственной.
