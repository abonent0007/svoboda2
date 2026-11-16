# Nova-Android-Fork — reference review (read-only)

Источник: `C:\Users\Abonen0007\Documents\Nova-Android-Fork`
Лицензия проекта: **GPL-3.0** (`LICENSE`), THIRD-PARTY.md это подтверждает.

## ⚠️ Ограничение
Код и бинарники Nova **нельзя копировать** в Svoboda 2.0 (Apache-2.0/MIT) — это сделает
Svoboda производной GPL-3.0. Допустимо: изучение архитектуры и **самостоятельная
реализация тех же идей** (clean-room), со ссылкой на концепцию.

## Что полезно как пример (не копировать)

| Файл Nova | Идея | Куда применимо у нас |
|-----------|------|----------------------|
| `DesyncProxyManager.kt` | Native-движок запускается **отдельным процессом**; ожидание готовности SOCKS-порта с таймаутом; лимит рестартов; kill stale-процессов | DPI-1.3/1.4 — менеджер жизненного цикла ciadpi |
| `DesyncOptions.kt` | Сборка argv **списком**, валидация значений, `normalized()` | Наш `ByeDpiStrategyValidator` / argv для BDP-00 |
| `AwgSocksBridge.kt` | Мост AWG ↔ SOCKS | Перспективный режим `DPI_OVER_AWG` |
| `AlwaysOnVpn.kt` | Обработка Always-on в приложении | сверка с нашим P3.10 |
| `ConnectionSelectorPolicy.kt` | Явная политика порядка транспортов и подписей | будущий `AUTO` (DPI-6) |
| `CloudflareTrace.kt` | HTTPS-proof внешнего IP | у нас уже есть `NetProbe` |
| `jniLibs/*/libtun2proxy.so` | tun2proxy (upstream MIT) как TUN→SOCKS | альтернатива hev; рассматривать отдельно |

## Наблюдение по архитектуре
Nova использует **процесс + локальный SOCKS5** (движок как executable `libnova-desync.so`),
а не JNI. Для Svoboda выбран путь ByeByeDPI: **JNI + hev-socks5-tunnel** с передачей TUN FD —
это лучше подходит к `VpnService`, где FD уже принадлежит нашему процессу.

## Вывод
Nova оставляем как **справочник паттернов** (менеджмент процесса, readiness-probe, argv-валидация,
политика выбора транспорта). Исходники DPI берём только из закреплённых MIT-репозиториев
(`hufrea/byedpi`, `heiher/hev-socks5-tunnel`), уже полученных в `third_party/`.
