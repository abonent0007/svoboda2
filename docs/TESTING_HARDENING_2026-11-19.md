# Отчёт тестирования и хардненинга — 2026-11-19 (ветка `hardening-2026-11-19`)

## Команды и исходы

```
.\gradlew.bat :app:testDebugUnitTest      → было: BUILD SUCCESSFUL (36 тестов)
(позднее, с cleanTest)                     → 66 тестов, 0 failures, 0 errors
.\gradlew.bat :app:assembleDebug            → BUILD SUCCESSFUL (split APK arm64-v8a / armeabi-v7a / universal, локально)
python tools/net_scan.py                    → 40 сетевых узлов, 0 без обработки ошибок
```

Итоговая таблица приборов (`app/build/test-results/testDebugUnitTest/*.xml`):

| Suite | Тестов | Падений |
|---|---|---|
| RemoteConfigStoreTest (новый) | 14 | 0 |
| DataPlaneProbeTest | 6 | 0 |
| AntiBounceGateTest (новый) | 6 | 0 |
| AutoFailoverTest | 22 | 0 |
| CandidateCycleEligibilityTest (новый) | 5 | 0 |
| ClassifyFailureTest (новый) | 5 | 0 |
| SingleJobTrackerTest | 8 | 0 |

## Какие ошибки нашлись и как исправлены

1. **org.json недоступен в локальных unit-тестах** → 5 падений RemoteConfigStoreTest были артефактом окружения, не продукта. Исправление: `RemoteConfigStore` переведён на `kotlinx.serialization` (как SeedCatalog/SplitTunnelManager в проекте). Production-код с того же момента покрыт теми же codepath'ами, что и тесты.
2. **Тест с неверным ожиданием (`quarantine lifts`)** — моя ошибка в новом тесте: `canTry()` учитывает и `failedInCycle` (очищается ТОЛЬКО `beginCycle()`), а окно quarantine — отдельно. Исправлен тест; поведение продукта (quarantine переживает циклы) задокументировано в комментарии теста.
3. **Харднкод CF-Client-Version** — заменён на `RemoteConfig.cfClientVersion()` (remote API → дисковый кэш 24ч → embedded). Поведение по умолчанию-идентично прежнему (remote.json содержит ту же версию `a-6.30-3596`).
4. **Харднкод Telegram DC** — заменён на `RemoteConfig.telegramDcs()` с embedded-списком как fallback (семантика не менялась: 6 тех же адресов).
5. **net_scan: 11 меток «!!»** — все проверены вручную и признаны не требующими правок: (a) константы данных/пресеты DNS в UI и каталогах; (b) импорты; (c) SOCKS5/TCP в DpiDomainTester находятся внутри общего `try` бенчмарк-измерителя (сбой = строка результата, не падение).

Ошибок в production-коде, которые открыли бы новый дефект, тесты НЕ показали — существующая логика (сделанная в RC2-ревью: DEFERRED≠CONNECTED, bypassHold/bypassQuarantine, R1-часы quarantine) подтвердилась как рабочая.

## Что проверено дополнительно (чеклист по запросу)

- Логические переходы: anti-bounce окно/байпас, фазы DEFERRED/гейты, clearHealth/clearReconnect (single-owner), quarantine-окно и сетевое восстановление кандидата — покрыты и зелёные.
- Внешние данные (смена во времени): RemoteConfigStore — remote/кэш/embedded/fallback на мусор/404/500/пусто/частичный список DC с фильтрацией не-IP — все ветви покрыты.
- Заглушки: хардкоды CF_CLIENT_VERSION и DC-список заменены на remote-источник; embedded fallback сохранён (поведение не регрессировало); остальные хардкоды (перечислены в CHANGELOG) оставлены сознательно как протокольные/продуктовые константы.
- Diff-скан импортов и сетевых вызовов: `tools/net_scan.py` — результат выше; ни один сетевой вызов не оставлен без обработки ошибок.
- Публикация APK на GitHub — не выполнена (репозиторий приватный; ассеты скомпрометированы). Условия — в CHANGELOG.
