# Handoff — 2026-11-21 (после прогона и релиза 2.0.11)

## Линии разработки
| Ветка | Коммит | Статус |
|---|---|---|
| **main** | `07fbfc7` | стабильная **«Свобода 2.0.11»** (2.0.11, versionCode 211): AUTO не роняет маршрут, WARP lastGood, DPI-при-Telegram-DC |
| **experiment/svoboda3** | `e15c1c0` | эксперимент «Свобода 3.0» (пакет `svoboda3.runet.abonent0007`) |

GitHub: `https://github.com/abonent0007/svoboda2` (public).
Релизы: **v2.0.11** — https://github.com/abonent0007/svoboda2/releases/tag/v2.0.11 (3 APK).
Предыдущие: v2.0.10 (`bdae9da`), v2.0.9 (`5f3ab34`).

## Устройство
Realme RMX3370 (`97db202c`), Android 13, Tele2 LTE. Установлена **2.0.11** (release, debug-подпись).
Состояние: метод **AUTO** → профиль **WARP · последний рабочий**, **ПОДКЛЮЧЕНО**, выход `2a09:bac5:…` (CF PoP), ping ~22 мс.
Split: 20 российских пакетов исключены (`global_direct_apps`, direct).
Проверено в 2.0.11: Chrome / YouTube / Telegram — через туннель; Avito — direct.

## Что исправлено (см. `docs/BUGS_PLAN.md`)
**2.0.11:** F6 AUTO не роняет рабочий DPI из-за Telegram DC; F7 AUTO проверяет WARP lastGood.
**2.0.10:** F1 DPI-рекурсия (исключение своего пакета); F2 проба OkHttp→сырой SOCKS; F3 дефолт `BDP-00`; F4 ciadpi не убивается; F5 split-tunnel режим.

## Открытые хвосты
1. **B1** семантика режима split-tunnel (ALL недостижим при непустом `globalDirectApps`) — зафиксировано, не менялось.
2. Ротация секретов / purge — репо публичный, ключи в истории; требует явной авторизации.
3. AWG `komarovai-frankfurt-1` — `TRANSPORT_FAIL` (нужна ротация сервера).
4. Прогон YouTube-стратегий (43 шт.) — вручную из настроек DPI.
5. Мусор в корне репо (`ui_*.xml`, `*.png`, `auto_log_*`, `logcat_*`, `build*.log`) — не коммитить; почистить при желании.

## Быстрый старт
- Сборка: `$env:JAVA_HOME='C:\Users\Abonen0007\dev\jdk17'; $env:ANDROID_HOME='C:\Users\Abonen0007\Android\Sdk'; .\gradlew.bat :app:assembleRelease :app:testDebugUnitTest` (66 тестов).
- Подпись: `KEYSTORE_PATH=$env:USERPROFILE\.android\debug.keystore`, `KEYSTORE_PASSWORD=android`, `KEY_ALIAS=androiddebugkey`, `KEY_PASSWORD=android`.
- adb: `C:\Users\Abonen0007\Android\Sdk\platform-tools\adb.exe -s 97db202c`.
- git: `C:\Users\Abonen0007\dev\git\cmd\git.exe`.
- Устройство debuggable только у debug-сборки; release — `run-as` недоступен.
