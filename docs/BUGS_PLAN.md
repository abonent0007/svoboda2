# План лечения «Свобода 2.0» — прогон на устройстве 2026-11-21

Устройство: Realme RMX3370 (`97db202c`), Android 13, Tele2 LTE.
База: `main` = `5f3ab34` (2.0.9). Прогон: logcat + `uiautomator` + `dumpsys connectivity`/`ip`.

## Исправлено в этой сессии

### F1 · DPI: собственный пакет не исключался из VPN → рекурсия (CRITICAL)
- **Симптом:** DPI не подключался (`SOCKS5 probe failed`, «НЕТ ИНТЕРНЕТА»), хотя ciadpi работал.
- **Причина:** `VpnController.applySplitTunnel` удалял собственный пакет (`direct -= own`),
  поэтому исходящие сокеты ciadpi (тот же UID) уходили в TUN → HEV → SOCKS5 → снова TUN.
- **Fix:** в DPI-режиме (`currentProfileId == "dpi"`) собственный пакет добавляется в
  `ExcludedApplications`. Проверено: `ip rule` — UID 10328 не в VPN-таблице; ciadpi жив.

### F2 · DPI: проба через OkHttp/Conscrypt не проходила (CRITICAL)
- **Симптом:** `candidate dpi: CANDIDATE_DEGRADED (stage=socks)` при живом ciadpi;
  ручной `curl`/`python` через тот же SOCKS5 — успешно.
- **Причина:** OkHttp (`Proxy.Type.SOCKS`) на Android отдаёт ClientHello, который ciadpi
  не десинхронирует (в логе ciadpi «split cancel» / зависание на `pollout`).
- **Fix:** `NetProbe.fetchExitViaSocks`/`probeViaSocks` переписаны на сырой `Socket` +
  SOCKS5 CONNECT по домену + `SSLSocket`. Результат: `data-plane ok (socks)`, `CONNECTED`,
  выход 92.36.67.99 (Warsaw), ping ~290 мс — как в рабочей сборке.

### F3 · DPI: дефолтная стратегия была экспериментальная `dpi_x03`
- **Причина:** коммит `be255aa` сменил дефолт `BDP-00` → `dpi_x03` («рабочая конфигурация»),
  но `dpi_x03` на RMX3370/Tele2 не проходит пробу.
- **Fix:** дефолт возвращён к `BDP-00` (эталон, `DPI_DEFAULT_STRATEGY_ID`). Проверено: CONNECTED.

### F4 · DPI: неудачная проба убивала ciadpi, оставляя мёртвый туннель
- **Fix:** в `ConnectionManager.socksDataPlaneResult` убран `stopProcess()` при отказе —
  туннель остаётся живым, повтор/health могут восстановить.

### F5 · Split-tunnel: режим в шторке показывался неверно и не сохранялся
- **Симптом:** в шторке «весь трафик через VPN», хотя применяется `ExcludedApplications` (20 пакетов).
- **Причина 1:** `remember(splitProfileId)` кэшировал `resolve()` при первом составе (до создания
  `split_tunnel.json`) → показывал legacy `ALL`.
- **Причина 2:** `ConnectionManager.applyWarpSplit` перезаписывал режим из списков на каждом
  подключении, теряя выбор пользователя.
- **Fix:** кэш обновляется при открытии шторки (`remember(splitProfileId, drawerOpen)`);
  `applyWarpSplit` сохраняет режим и синхронизирует только соответствующий список.
- **Проверено:** в шторке «все кроме выбранных»; выбор пишется в `split_tunnel.json`.

## Остаётся (зафиксировано, не исправлялось)

### B1 · Семантика режима split-tunnel неоднозначна
- `globalDirectApps` применяется всегда, поэтому «весь трафик через VPN» (ALL) недостижим,
  пока в списке 20 российских приложений. UI может вводить в заблуждение.
- `applySplitTunnel`: при непустых allow- и direct-списках приоритет у allowlist
  (в комментарии заявлен приоритет direct). На практике allowlist пуст по умолчанию.

### B2 · «О приложении», диалог «Обновления», нейросети — не в выпущенной 2.0.9
- Кандидаты в 2.0.10.

## Известные ограничения (не баги)
- WARP — ближайший PoP Cloudflare (`loc=RU` в РФ); это норма, не обход геоблока.
- AWG `komarovai-frankfurt-1` — `TRANSPORT_FAIL` (сервер недоступен из РФ), нужна ротация.
- Cloudflare API для новой WARP-регистрации из РФ недоступен (SSL timeout).
- Telegram DC (149.154.x/91.108.x) через DPI недоступны (IP-блок) — нужен AWG/WARP.

## Проверено на устройстве (2026-11-21)
- AUTO → WARP (сгенерированные профили) → CONNECTED, выход CF; стабильно.
- DPI (BDP-00) → CONNECTED, выход 92.36.67.99, ping ~290 мс, health healthy.
- Chrome через DPI-туннель: example.com загружается.
- Split-tunnel: 20 пакетов исключены (проверено по UID-диапазонам VPN).
- 66 unit-тестов зелёные.
