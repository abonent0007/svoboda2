# CHANGELOG — hardening branch `hardening-2026-11-19` (2026-11-19)

Ветка: `hardening-2026-11-19` от `diagnostic/instrumentation` (6395725). Ничего не пушено, RC1/RC2 не тронуты, устройство не тронуто.

## Итог проверок

- `:app:testDebugUnitTest` — **66 тестов, 0 падений** (было 36 → +30 новых/закрытие пробелов).
- `:app:assembleDebug` — собирается (debug APK 20.9–22.8 МБ, локально, никуда не публиковались).
- `tools/net_scan.py` — 40 сетевых узлов, 0 без обработки ошибок (все помеченные «!!» — константы данных / умышленные каталоги / содержится внутри try бенчмарка).

## Что было сломано → как починил

| Что | Причина | Исправление |
|---|---|---|
| org.json в unit-тестах RemoteConfigStore | В локальных unit-тестах org.json недоступен → `runCatching` молча возвращал embedded, 5 тестов падали | Парсер/ди-сериализация переведены на kotlinx.serialization (паттерн проекта, как в SeedCatalog) |
| CandidateCycleEligibilityTest «quarantine lifts» | Моя ошибка в тесте: `canTry` = не в `failedInCycle` И окно вышло; `failedInCycle` очищается только `beginCycle()` (quarantine — осознанно НЕТ) | Тест исправлен: после окна вызывается `beginCycle()`; поведение продукта задокументировано в комментарии |
| Харднкод `CF_CLIENT_VERSION` (`a-6.30-3596`) в WarpGenerator | Заменился редко, молча устаревал при протухании версии клиента CF | `RemoteConfigStore` + `RemoteConfig` (suspend), TTL кэша 24ч, дисковый кэш, embedded fallback; `WarpGenerator` читает через RemoteConfig |
| Харднкод списка Telegram DC (`DC_IPS`) в TelegramDc | Telegram меняет адреса DC; список устаревал в APK | Список добавлен в `RemoteBundle.telegram_dcs`; RemoteConfigStore+remote.json; `DC_IPS` сохранён как embedded-fallback (deprecated) |
| remote.json | — | `config/remote.json` — единственное внешнее состояние (cf_client_version + список DC), служит источником для RemoteConfig после слияния в main |

## Заглушки / устаревшие константы заменены

- `WarpGenerator.CF_CLIENT_VERSION` → `RemoteConfig.cfClientVersion()` (remote → disk-кэш → embedded `a-6.30-3596`). Константа НЕ удалена — оставлена как источник embedded-дефолта (`@Deprecated`).
- `TelegramDc.DC_IPS` → `RemoteConfig.telegramDcs()` (remote → кэш → embedded 6 адресов). Константа НЕ удалена (`@Deprecated`, совместимость).
- Новый файл `AppGraph`-стиль: `RemoteConfig` object (endpoint raw.githubusercontent.com/…/config/remote.json) + JVM-тестируемый `RemoteConfigStore`.

## Логические пробелы, закрытые тестами

- `CandidateCycle.nextEligibleAt()` — минимум quarantine-дедлайнов; null при пустоте/после окна (тест).
- `CandidateCycle` — дубликаты отказов схлопываются; insertions стабильны.
- `AntiBounceGate.proceed` — 5 граничных условий (bypassHold, нет healthy-сессии, hold=0/отрицательный, ровно на границе окна, за 1мс до).
- `classifyFailure` — все FailureKind, null-kind, http code, усечение сообщения до 80, blank-сообщение без хвостовой запятой.
- `RemoteConfigStore` — remote wins/cache написан/свеж из кэша без сети/404/мусорный JSON/пустой cf_client_version/игнор неизвестных ключей/stale кэш при 500/roundtrip списка DC/фильтрация мусора в DC/fallback при полном мусоре/частичном мусоре.
- Connectivity-верификатор `tools/net_scan.py` (network-callsite audit: 40 узлов, 0 без обработки).

## НЕ выполнено (явно)

- Публикация APK на GitHub — не выполнена. Любой APK из текущего дерева вшивает скомпрометированные ассеты (семена/ключи/токен) со связкой с `public_servers`/`warp_identity`. Публикация сейчас не ведётся. Условия для публикации: (1) репозиторий Private; (2) ротация peer/enrollment (Frankfurt→по вашему доступу; WARP-стороны — когда восстановится CF API); (3) чистая пересборка с новыми ассетами; (4) явное разрешение пользователя.
- «Замени ВСЕ хардкоженные значения на серверные API» — сделано для 2 классов данных (CF_CLIENT_VERSION, список Telegram DC); остальное сознательно оставлено статикой: протокольные константы (REG_URL, CF trace URL), DNS-каталоги пресетов (Malw/geohide — продуктовые значения), MTU/порты/лимиты (protocol bounds), IP-диапазоны сервисов split-tunnel (`ServiceIpRanges` — умышленный вшитый datasource, см. доку), детали ByeDPI/HEV третьих сторон, тестовые хосты. Отдельный проход «расшить весь статический inventory в remote.json» — только после ремедиации и отдельным решением (утечки самого remote.json-канала не допустимы до восстановления доверия).
- Кнопка «Тест стратегий DPI» на главном экране — отложена вами до фикса crash + проверки на отдельном устройстве.
- `wasmKnownGaps`: ничего из устройства не воспроизводилось; JVM-тесты не доказывают device-поведение.

## Проверка

```
set JAVA_HOME=C:\Users\Abonen0007\dev\jdk17
gradlew.bat :app:cleanTestDebugUnitTest :app:testDebugUnitTest
gradlew.bat :app:assembleDebug
python tools/net_scan.py
```

## Дополнение 2026-11-21 (hardening-2026-11-19, коммит 541876b)

- **Health и «Проверить связь» теперь честные**: проба привязана к VPN-сети (`NetProbe.fetchExitBound` / `tcpPingBound` через `Network.socketFactory`), т.е. идёт через TUN. Раньше не-DPI проба шла напрямую из приложения (оно исключено из VPN) и могла показывать «здорово» при мёртвом туннеле. DPI по-прежнему проверяется через локальный SOCKS (он и есть туннель).
- **«Пресет: Российские приложения»** — больше не перезаписывает список молча: диалог с числом добавляемых/удаляемых пакетов, действия «Объединить» / «Заменить», и кнопка «Отменить пресет» после применения.
- **ActionChip** — единая кнопка с контуром/выделением; заменены «голые» `TextButton` в настройках методов (WARP/DPI/AUTO), `ServiceScopePicker`, `DnsResolverPicker`.
- **Автообновление** по умолчанию ВЫКЛ, пока репозиторий приватный (releases/latest недоступен).
- Удалены мёртвые prefs (`autoPreferWarp`, `autoFallback`, `dpiCustomStrategyPrev`) и мёртвые параметры (`DpiConfig.serviceScope`, `WarpProfileFactory.serviceScope`).
- Исходник релиза зафиксирован: `541876b` (ветка `hardening-2026-11-19`). Пересборка от этого коммита: `app-universal-release.apk` SHA-256 `b5ff922d…`, `app-arm64-v8a-release.apk` `5030b1f4…`, `app-armeabi-v7a-release.apk` `f95e6215…`.
- Консультация  council (duck gpt-oss 120B + Claude Haiku): подтверждено, что allow/deny в одном `VpnService.Builder` взаимоисключаемы (effective-set считаем в приложении, передаём один режим) и что health-проба обязана идти через TUN.
