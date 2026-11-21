# P0 — AUTO: ТЗ на обязательное исправление (до любого публичного релиза 2.0.7)

Дата: 2026-11-18. Статус: **техзадание; код не писать без отдельного разрешения.**
Основание: `docs/EVIDENCE_TESTS.md`, `docs/AUTO_FAIL_2026-11-18_evidence/`, `docs/RESEARCH_2.0.8_CANDIDATES.md` п.7.

## 0. Параметры решения (зафиксированы пользователем)
- 2.0.7 — NO-GO до исправления AUTO и повторного теста.
- Историческая КТ `9460f5c`/`f954807` не меняется; `d9a18ce`/`ca9df2f` — docs-дополнения.
- AWG остаётся UNVERIFIED (другая сеть недоступна); это не релизный блокер, но не считать PASS.
- Релиз «только WARP» не делаем; минимальное исправление AUTO → отдельная release-candidate версия → полный smoke.
- P0 отделяется от P1/P2 (Quality Manager, Direct App Picker и т.д.). В этом ТЗ — только P0.
- Распространение информации: AWG на альтернативной SIM проверялся не будет до появления сети.

## 1. Три дефекта, которые нужно закрыть (и ещё два сопутствующих)
| # | Дефект | Где |
|---|---|---|
| D1 | AUTO считает «успехом» создание tunnel-интерфейса; data-plane (DNS/HTTPS) не проверяется → мёртвый `public:any_1` показан как «подключено» | `ConnectionManager.autoConnect()` (успешная итерация = `controller.connect(config) == true`); `ReconnectCoordinator.userConnect()` → `setStatus(null)` при ok |
| D2 | Anti-bounce (`autoHoldMinutes=5`) возвращает `true` из `autoConnect()` в обход перебора → координатор считает кандидата рабочим; failover не происходит; цикл «Повтор через Nс…» | `ReconnectCoordinator.kt:174-201` (scheduleReconnect → autoConnect → logAuto("anti-bounce: hold")); `ConnectionManager.kt:288-292` |
| D3 | Провалившийся кандидат не исключается из цикла; «успех» возвращается к нему же по истечении 5 мин; причин отказа нет в журнале | `ConnectionManager.kt:372-401` (for-loop без отслеживания причин; `logAuto("try candidate #N")` без причины отказа) |
| E1 | `JobCancellationException: StandaloneCoroutine was cancelled` попадает в пользовательский статус как текст ошибки; профиль перескакивает NL→FI | `ReconnectCoordinator` (reconnectJob.cancel при новой попытке) → `controller.setStatus(...)` |
| E2 | В DEGRADED-экране только «ОТКЛЮЧИТЬ»; нет «ПОВТОРИТЬ»; «Повтор через Nс…» показывает заглушку антидребезга, а не реальную попытку | `HomeScreen.kt` кнопка-toggle; `ReconnectCoordinator.setStatus("Повтор через ${delaySec}с…")` |

**Связь:** D1+D2+D3 — один контур «неверный критерий → блокировка failover → цикл». E1/E2 — усугубляют восприятие и снижают восстанавливаемость.

## 2. Цель и не-цели
**Цель (P0):** устранить D1/D2/D3/E1/E2 минимальными изменениями в контракте подключения.
**Не-цели:** Quality Manager (окно 5 циклов, SQLite-метрики, cooldown 30 мин — это P1/P2), переупорядочивание кандидатов пользователем,
новые транспорты, AWG на другой сети.

## 3. Новый контракт результатов (замена Boolean)
`sealed interface ConnectionResult` + варианты (ровно как согласовано):
- `CONNECTED_HEALTHY` — туннель поднят **и** data-plane подтверждён.
- `TRANSPORT_UP_VALIDATING` — туннель поднят, data-plane валидируется.
- `RECONNECT_DEFERRED` — попытка отложена антидребезгом; **никогда не превращается в CONNECTED**.
- `CANDIDATE_DEGRADED` — кандидат провалил data-plane; исключается из текущего цикла.
- `FAILOVER_IN_PROGRESS` — идёт переход к следующему кандидату.
- `NO_WORKING_ROUTE` — кандидаты исчерпаны; показываем «Нет рабочего маршрута» (не «Подключено»).
- `USER_DISCONNECTED` — отключено пользователем.

`ReconnectCoordinator` потребляет `ConnectionResult`:
- `userDisconnect` → USER_DISCONNECTED;
- при `userConnect(manual=true)` антидребезг **не применяется** никогда;
- потоки health переводят состояние в CONNECTED только после `CONNECTED_HEALTHY`;
- `startHealth` при 3 подряд неуспехах вместо «слепого» `scheduleReconnect(backoff)` отправляет в перебор кандидатов (см. §5), соблюдая текущий `sessionId` и отмены.

## 4. Data Plane Validation (обязательная, до «успеха»)
| Транспорт | DATA_PLANE check |
|---|---|
| AWG / WARP / публичные (incl. AUTO-ветка) | 2 независимых HTTPS GET через **data-plane самого транспорта** (собственный пакет → tun0; own пакет НЕ исключён — проверено): (1) `https://1.1.1.1/cdn-cgi/trace`, (2) `https://www.gstatic.com/generate_204` (ожид. 204); таймаут 5 с; 200/204 = OK |
| DPI (`DPI_DIRECT`/`DPI`) | Проба через локальный SOCKS5 (`fetchExitViaSocks` / `socksPing`, ciadpi): `https://www.cloudflare.com/cdn-cgi/trace` через `127.0.0.1:<dpiSocksPort>` |

Использовать существующий `NetProbe` (расширить его методами `validateDataPlane(via: TransportKind)`); `VpnService.protect()` и привязка сокета к физической сети не проверяют туннель.

Этапы кандидатного цикла (перебор):
```
TRANSPORT_UP → DATA_PLANE_VALIDATING →
  PASS ⇒ CONNECTED_HEALTHY (exit записан, lastGood зафиксирован, health-цикл стартует)
  FAIL ⇒ CANDIDATE_DEGRADED: кандидат исключён из цикла (per-run Set<String>),
         причина в auto_log.txt (стадия + вид ошибки), переход к следующему (FAILOVER_IN_PROGRESS)
кандидаты закончились ⇒ NO_WORKING_ROUTE (туннель поднимать/держать НЕ показываем как рабочее подключение;
         UI «Нет рабочего маршрута»; выход — явное «Повторить»/ручной выбор транспорта)
```

## 5. Anti-bounce: новое правило
- Hold действует только для HEALTHY-кандидатов (переключение между рабочими): минимум 5 мин между сменами
  (`autoHoldMinutes`), прежняя политика.
- При `CANDIDATE_DEGRADED` (подтверждаемого данными data-plane) переход к следующему кандидату не задерживается anti-bounce’ом:
  валидные причины (handshake fail / HTTPS timeout / endpoint timeout / DNS fail) помечают кандидата «отклонён в этом цикле»;
  повторная попытка того же endpoint возможна не раньше конца следующего полного цикла и не раньше cooldown ≤ цикла (не 5 мин «вслепую»).
- `RECONNECT_DEFERRED` не должен «залипать» в CONNECTED ни при каких условиях (см. §3).
- Требование тестов: детерминированный воспроизводимый сценарий «кандидат мёртв, следующий жив» — AUTO обязан переключиться без участия пользователя.

## 6. Лог отказов (причина на строку)
Каждая попытка `try candidate #N <id>` завершается одной строкой:
`21:22:10 candidate #3 <id>: TRANSPORT_FAIL(stage=handshake, detail=timeout 15s)` или
`21:05:35 candidate #7 public:any_1: DATA_PLANE_FAIL(stage=https, http=timeout, dns=ok, exit=unreachable)`.
Причина классифицируется: `HANDSHAKE`, `DNS`, `HTTPS`, `ENDPOINT_UNREACHABLE`; неизвестное — `UNKNOWN` с текстом.
Сохраняем последние 150 строк (формат прежний).

## 7. Обработка Cancellation (E1)
- Все `withLock`-корутины reconnect/health: `catch (CancellationException) { логировать как control event; не setStatus; не маркировать ошибкой }`.
- Пользовательский статус никогда не принимает exception.toString(); только человеческие тексты.

## 8. UI (E2)
- В `DEGRADED`/«НЕТ ИНТЕРНЕТА» два CTA: «ПОВТОРИТЬ» (одна полная попытка перебора из control-plane) и «ОТКЛЮЧИТЬ».
- Статус-строка показывает реальный этап («Проверяю данные…», «Кандидат №3 отклонён (HTTPS)», "Повтор через Nс" только с реальным таймером).
- `JobCancellationException` пользователю не показывается ни при каких условиях.

## 9. Именно этот код трогаем (минимальный объём P0)
- `ReconnectCoordinator.kt` — новый контракт (sealed ConnectionResult), убрать Boolean-интерпретацию; health-цикл: 3 неуспеха → план перебора кандидатов через координатор, если ручного выбора нет; проброс отмены как control event.
- `ConnectionManager.kt` — `autoConnect()`/`connectManual()`/`connectLastKnown()`: возвращают ConnectionResult; валидация data-plane внутри каждой кандидатной лямбды; FAIL ⇒ log + continue; успех ⇒ HEALTHY; `autoHoldMinutes` читается только в ветке HEALTHY.
- `NetProbe.kt` — добавить `validateDataPlane(kind)` (2 GET, 5с, классификация ошибки).
- `VpnController.kt` — при необходимости `markDegraded(false)` только из HEALTHY.
- `HomeScreen.kt` — DEGRADED: «ПОВТОРИТЬ»+«ОТКЛЮЧИТЬ»; status-строка без CTF.
- `main`/`Settings` — ничего. Версия bump ровно на 2.0.8-RC (или 2.0.7-fix2) на момент реализации; КТ не меняем до решения.
- Проверка перед реализацией probe (обязательно): own пакет не исключён в AWG/WARP ✓ (проверено grep’ом+device); probe не использует protect()/bindProcessToNetwork ✓/✗ — grep показал, что protect() не вызывается нигде; probe не делит сокет с транспортом ✓ (отдельный OkHttp stack); вид Probe→tun0 ✓ (эмпирика сегодняшняя: uid 10328 → tun0 при WARP).
- До реализации измерить то же в DPI-режиме (DPI: own пакет исключён из VPN намеренно; проверка — только SOCKS5; probe через app-socket там бессмыслен — отразить в коде/доках; на устройстве НЕ меряется до отдельного тестового задания).

## 10. Тесты
- Unit (JVM, без устройства): ConnectionResult-классификация, failover-цикл, `RECONNECT_DEFERRED` никогда не CONNECTED,
  anti-bounce не задерживает после CANDIDATE_DEGRADED, логирование причины (строки), cancellation-тихий путь.
- Интеграция (требует устройства и разрешения на тест):
  1. Свежий AUTO при нормальной сети Tele2: проходит в HEALTHY.
  2. Принудительно только «мёртвый» кандидат (тестовый флаг/файл seeds) → failover к следующему живучему без участия пользователя; затем NO_WORKING_ROUTE при отсутствии живых.
  3. 3 подряд деградации health → автоматический перебор, решительность за < N минут без пользователя.
  4. ОТКЛЮЧИТЬ/ПОВТОРИТЬ на экране DEGRADED работают; канселляция mid-failover не падает и не видна в статусе.
  5. AWG по-прежнему UNVERIFIED (другая сеть недоступна) — задокументировать, не выдавать за PASS.
- Эксперименты на устройстве — только после отдельного тестового задания пользователя.

## 11. Acceptance criteria (Done when)
- [ ] AUTO never shows Подключено без `CONNECTED_HEALTHY` (data-plane подтверждён двумя HTTPS-ответами).
- [ ] При отказе кандидата перебор продолжается автоматически; провал data-plane нигде не считается «подключением»; `RECONNECT_DEFERRED` никогда не CONNECTED.
- [ ] `any_1`-кейс воспроизводится: туннель создан, HTTPS нет → кандидат отвергнут, переход к следующему, запись причины.
- [ ] JobCancellationException не виден в UI.
- [ ] DEGRADED-экран: «ПОВТОРИТЬ»/«ОТКЛЮЧИТЬ»; статус-строка без raw exception.
- [ ] Полный smoke 2.0.7-RC: запуск, FGS, force-stop → чистая остановка, WARP WARP, DPI, split-tunnel, DNS-9, AUTO.
- [ ] Расширенное testing = отдельный этап; релиз только по отдельному «да».

## 12. Риски и откат
- Риск ложной деградации на часто теряемой Tele2 5G — смягчается двумя независимыми пробами и попыткой из той же сессии, а не из случайного DIRECT-сокета.
- Попытка валидации посчитает «страну != NL/FI» как FAIL, если ждать — нет: страны не часть валидации.
- Откат: изолированный commit на ветке; 2.0.7 КТ остаётся нетронутой; если RC не стабилен — возвращаемся к КТ-процессу.
