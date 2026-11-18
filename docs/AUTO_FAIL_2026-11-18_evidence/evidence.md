# Этап AUTO — 2026-11-18 21:02–21:23 MSK, Realme RMX3370, Tele2 LTE (сим), Wi-Fi выключен

**Вердикт: AUTO FAIL.** Проверка провалена детерминированно; ручной WARP работоспособен; 2.0.7 → NO-GO.

## Окружение до переключения (baseline, зафиксировано)
- APK `228D23E59D01811219F56A262DA7F8105BF303E825A9527C370BCFF61F04DF2D` (2.0.7 + `993c3c4`),
  versionCode 207, versionName 2.0.7.
- Способ: WARP; tunnel UP; split-tunnel = 20 пакетов russian_direct; `auto_include_public_servers=true`;
  LTE Tele2 (`internet.tele2.ru`, rmnet_data2), Wi-Fi DisabledState.
- DNS `1.1.1.1,1.0.0.1`; `auto_connect=true`.
- Исходно открытый WARP-сеанс работал (HTTPS: probe 2a09:bac5:5153:3137::4e7:44, ping 57/69/87 мс; Chrome → Cloudflare egress).

## Ход испытания (по `auto_log.txt`, локальное время MSK)
21:02:2x Пользователь (агент) переключил способ на AUTO (в приложении, без force-stop/переустановки).
21:02:54 AUTO start; order=AWG,WARP,DPI; countries=NL,FI,US; AWG: own servers=0 → try #1…#7.
21:05:10 try #7 → **candidate #7 selected: public:any_1**; tunnel создан.
UI: «НЕТ ИНТЕРНЕТА»; probe → **«IP недоступен · пинг не проходит»**; IP/Страна/Ping = «—».
21:07:18, 21:09:26, 21:15:59, 21:18:07 anti-bounce hold 5m (AUTO не переключает мертвый кандидат).
21:11:34 anti-bounce истёк → AUTO стартует заново → try #1…#7 → **21:13:51 снова any_1** → DEGRADED.
YouTube (`com.google.android.youtube`) — не поднялся (сеть мертва). Telegram (`org.telegram.messenger.web`) —
экран «Соединение…», соединение не установилось.
21:18:51 AUTO снова перезапустился; в цикле появилась новая ошибка:
**«ОШИБКА · JobCancellationException: StandaloneCoroutine was cancelled»**, профиль переключался
«Нидерланды · 878 (публичный)» → «Финляндия · 864 (публичный)», поверх «Повтор через 45с…».

## Почему AUTO не восстановился (архитектурный дефект, подтверждается кодом)
1. `ReconnectCoordinator` считает «успехом» создание tunnel-интерфейса (`connection success`),
   data-plane (DNS/HTTPS) не проверяет. Мёртвый public Anycast проходит как «выбран».
2. После 3 подряд неуспешных health-проб (30 с интервал) → DEGRADED + `scheduleReconnect(backoff 2с)`
   → `autoConnect()` → AUTO снова выбирает any_1 и снова поднимает туннель → health снова не проходит →
   бесконечный цикл («Повтор через 2с…»), который не лечит кандидата и не приводит к другому.
   (`ReconnectCoordinator.kt:124–235`, `ConnectionManager.kt:286`.)
3. Anti-bounce hold (`autoHoldMinutes=5`) возвращает `true` из `autoConnect()` → координатор
   помечает состояние CONNECTED без фактического восстановления (или вызывает новый перебор → тот же any_1).
4. Кандидаты #1–#6 не подняли туннель; причин отказов в `auto_log.txt` нет (ждутся только строки try/selected).
5. Отдельный брак: `JobCancellationException` (гонка отмены reconnect-корутины) всплывает
   в пользовательский статус как есть; профиль при этом меняется (NL→FI) — приложения
   переключались по мёртвым публичным серверам.

## После испытания
21:20 Зафиксировано. 21:20–21:23 переключено обратно на WARP (в приложении, tap WARP → ОТКЛЮЧИТЬ → ПОДКЛЮЧИТЬ;
через повторное приведение MainActivity наверх — без force-stop/переустановки/сброса настроек).
**Способ = WARP; профиль «WARP · новый»; ПОДКЛЮЧЕНО; HTTPS probe: IP 2a09:bac5:5153:1478::20a:e0, ping 89/97/105 мс.**
Split-tunnel 20 пакетов russian_direct не тронут (проверено маршрутами: sber 10308, tinkoff 10251, pochta 10669 — DIRECT).

## Приложения
- YouTube — приложение не открывается (туннель «up», данные не ходят).
- Telegram — «Соединение…», не подключился.
(Оба работают через ручной WARP — проверено 17.11.2026 и подтверждалось сегодня до переключения на AUTO.)

## Файлы в этом каталоге
- `auto_log.txt` — полный журнал (выгрузка 21:20 MSK).
- `connectivity.txt` — dumpsys connectivity (включая VPN network{81/94}).
- `split_tunnel.json` — состояние russian_direct=20 до/во время испытания (не менялся).
- `version.txt` — версия сборки на устройстве.
- `wifi.txt` — Wi-Fi DisabledState.
- Скриншот дефектного главного экрана (через 60 с после probe): `mainnow.png` в корне репозитория (НЕ коммитить — рабочий артефакт).
