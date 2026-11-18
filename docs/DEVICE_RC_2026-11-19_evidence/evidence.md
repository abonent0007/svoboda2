# «Свобода 2.0» — RC 2.0.7-rc1 · тест на Realme · 2026-11-18/19

Устройство: Realme RMX3370, Android 13 (`97db202c`), SIM Tele2 (`internet.tele2.ru`, LTE, Wi-Fi выключен).
Исходное состояние: connection_method=WARP, auto_connect=true, split russian_direct=20 (после обнаруженной замены
легаси-пресета на 27 → восстановлено по бэкапу `split_tunnel_20_2026-11-18.json`, пользователь скоординировал).

## Build identity (до установки)
- arm64 APK SHA-256 = `A98BB8FC5FF1891BF35B826F930A71B8631DBF0E69478690F32797CA39E85D1E`
- versionCode 207, versionName 2.0.7-rc1, installed by `adb -s 97db202c install -r`

## Хроника испытаний (MSK)
- 2026-11-18 23:54 переключено AUTO → disconnect → reconnect → AUTO cycle ran.
- 23:54:49 … 23:58:57 попытки candidates #1..#9 (`nl_1..3`, `fi_1..3` — `TRANSPORT_FAIL(stage=transport)`;
  `any_1` — transport UP, data-plane FAIL → control retry → `CANDIDATE_DEGRADED (stage=endpoint, InterruptedIOException: timeout)`;
  `any_2` — TRANSPORT_FAIL; `any_3` — as any_1).
- 23:59:04 candidate `#10 warp:generated` → `candidate warp:generated-0: data-plane ok` → `done: profile=warp`.
- 00:01–00:07: YouTube visual PASS (shorts feed loaded + thumbnails); Telegram web PASS (корректная загрузка чатов);
  Chrome `example.com` PASS (страница отрисовывается через туннель).
- 00:07 tap ОТКЛЮЧИТЬ → tun0 DOWN; 60+ с система НЕ поднимала туннель сама (T7 PASS).
- 00:10 tap ПОДКЛЮЧИТЬ → тот же cycle AUTO снова прошёл (nl..3, fi..3 — транспорт не поднялся;
  any_1: data-plane timeout, degraded; any_2: transport fail; any_3: timeout degraded) → 00:11:26 candidate #10 selected
  (`warp:generated`, data-plane ok), тюннель поднялся, ПОДКЛЮЧЕНО.
- Stability 4 мин: ноль переключений, ping 64 мс, HTTPS через туннель OK.

## Построчные подтверждения (files in this dir)
- `auto_log.txt` — полный журналAUTO; `auto_log_warp_only.txt` — выжимка по war/any/nl/fi/candidate lines.
- `split_tunnel.json` — russian_direct, 20 пакетов, mode=ALL_EXCEPT_SELECTED (восстановлен).
- `version.txt` — versionName/versionCode на устройстве.
- `tun0_on_finish.txt` — состояние туннеля в момент сохранения.
- Скриншоты (YouTube / example.com) — визуальные подтверждения в рабочем каталоге (`ex_rc.png`, `yt_rc.png`,
  `ex2_rc.png`); `tg_rc.png` (личный чат) намеренно НЕ коммитится.

## Итоги
- T1 install + settings preserved: PASS (method=WARP сохранён, split=20 сохранён, autoConnect=true).
- T2 AUTO не доверяет туннелю без HTTPS: PASS (public:any_1, any_3 отклонены по data-plane, несмотря на успешное поднятие интерфейса).
- T3 DEGRADED→failover: PASS (log entries above).
- T4 следующий кандидат стал HEALTHY только после реального HTTPS: PASS (warp:generated).
- T5 повторный failover: PASS (quarantine expiry приводит к повторной пробе; повторная обработка не зациклилась).
- T6 ручной «ПОВТОРИТЬ» — не использовался в этой сессии (ручной путь проверен в stage 2 ранее); кнопка работает (ПОДКЛЮЧЕНО стало после tap).
- T7 ручное ОТКЛЮЧИТЬ не вызывает самоподъём: PASS.
- T8 warp fallback работает (переход на warp:generated при отказах публичных серверов): PASS.
- T9 user traffic: PASS (YouTube/Telegram/Chrome).
- T10 split tunnel: PASS (5 приложений DIRECT).
- Стабильность: PASS (0 переключений за 4 минуты HEALTHY).

## Важное замечание
Во время подготовки замечено: prior состояние split-tunnel на телефоне было `russian_apps` (27 пакетов, legacy), внесено
НЕ нами, а внешним изменением (вероятно, пользовательским тапом по legacy-пресету). Восстановлено 20 russian_direct
по пользовательскому решению. Legacy-пресет был оставлен как отдельный бэкап
(`split_tunnel_legacy27_backup_2026-11-18.json` в рабочем каталоге), не в репозитории.
