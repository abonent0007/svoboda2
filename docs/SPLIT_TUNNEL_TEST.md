# Этап 2 — Раздельная маршрутизация на SIM (2.0.7, WARP)

Дата испытаний: 2026-11-18. Устройство: Realme RMX3370 (Android 13), serial `97db202c`.
Оператор: Tele2 (5G/LTE, internet.tele2.ru, host `rmnet_data2`), статический IPv6 на rmnet_data2 отсутствует.
Wi-Fi (DurDom2): выключен весь час тестов (`WifiState 0`). Трафик шёл только через SIM.

## Часть 1 — Подготовка и идентификация тестовой сборки

### Установленная сборка (после фиксации КТ)
| Параметр | Значение |
|---|---|
| versionCode / versionName | 207 / 2.0.7 |
| Содержит коммит | `993c3c4` (fix(nav): BACK closes drawer first) — поверх КТ |
| SHA-256 установленного base.apk | `228D23E59D01811219F56A262DA7F8105BF303E825A9527C370BCFF61F04DF2D` |
| Равен локальному `app-arm64-v8a-debug.apk` | да (байт в байт; собран 2026-11-17 18:43:37) |
| Установлена | 2026-11-17 18:43:56 (`installer=pc`, `adb install -r`) |

Историческая КТ `CONTROL_POINT_2.0.7.md` (9460f5c / f954807, SHA-256 arm64 `5DA76241…`) не переписывается.
Тест проводился именно на сборке с `228D23E5…` (2.0.7 + BACK-фикс), а не на зафиксированном КТ-артефакте.

### Тестовый ретрансляционный контекст
- Способ подключения переключён в приложении **вручную на WARP** (не AUTO), без force-stop.
- Активный WARP-профиль при финальной проверке: `№11 · WARP` (WARP · новый при первой серии);
  туннель перепроверялся через in-app probe (own package → tun0) и Chrome.
- HTTPS-проверка туннеля: probe → `IP 2a09:bac5:…:43 / ping 53/64/73 мс`;
  после force-stop/relaunch → `IP 2a09:bac5:5153:3137::4e7:44 / ping 57/69/87 мс`.
  Chrome (не в исключениях, идёт через tun0): `https://api.ipify.org/?format=json` → `{"ip":"104.28.198.244"}`.
- Замечание: probe-сервис геолокацирует WARP-выход как «Россия» для текущего egress;
  зафиксировано как измеренное, без интерпретации сверх этого.

### Восстановление конфигурации split-tunnel (до начала теста приложений)
Во время настройки состояния я по ошибке нажал кнопку «Пресет: Российские приложения»
(legacy-пресет `russianApps`, 27 пакетов) вместо диалога «Российские приложения — напрямую»,
что молча перезаписало профиль `warp` (`russian_direct`, 20 пакетов) в `split_tunnel.json`.
Восстановлено вариантом B по согласованию: ровно 20 пакетов из RussianDirectCatalog,
`presetId=russian_direct`, `mode=ALL_EXCEPT_SELECTED`, `updatedAt` и остальных 12 профилей не изменены.
Проверено: побайтовое совпадение payload (кроме `updatedAt`), ровно 20 уникальных пакетов,
legacy-only 5 пакетов отсутствуют, бэкапы до/после сохранены.
Тест приложений проводился только после успешного восстановления и проверки маршрутов.

## Часть 2 — Результаты испытаний

### Маршруты (uid-lookup, `ip route get 1.1.1.1 uid <uid>`)
| UID | Приложение | Маршрут ДО восстановления 20 (legacy) | Маршрут ПОСЛЕ restore |
|---|---|---|---|
| 10308 | ru.sberbankmobile | rmnet_data2 (direct) | rmnet_data2 (direct) |
| 10251 | com.idamob.tinkoff.android | direct | direct |
| 10794 | ru.alfabank.mobile.android | direct | direct |
| 10270 | ru.tinkoff.investing | tun0 | direct |
| 10863 | ru.gosuslugi.auto | tun0 | direct |
| 10314 | ru.gosuslugi.goskey | tun0 | direct |
| 10787 | ru.fns.lkfl | tun0 | direct |
| 10299 | ru.mosmetro.metro | tun0 | direct |
| 10706 | ru.tele2.mytele2 | tun0 | direct |
| 10461 | ru.rt.atv.ultimate | tun0 | direct |
| 10669 | com.octopod.russianpost.client.android | tun0 | direct |
| 10793 | ru.ozon.app.android | direct | direct |
| 10588 | com.avito.android | tun0 | direct |
| 10784 | com.yandex.browser | direct | direct |
| 10199 | ru.yandex.taxi | tun0 | direct |
| 10543 | ru.yandex.music | — | direct |
| 10041 | ru.yandex.yandexmaps | — | direct |
| 10333 | ru.yandex.yandexnavi | — | direct |
| 10840 | ru.dodopizza.app | — | direct |
| 10844 | ru.vkusvill | — | direct |
| 2000 | shell (контроль) | tun0 | tun0 |
| 10328 | app (own) | tun0 | tun0 |

Замечание к `atv`: uid `ru.rt.atv.ultimate` (`10461`) не менялся между замерами; изменился маршрут —
`tun0` до применения russian_direct, `direct` после (ожидалось). Расхождений по uid нет.

### Контрольные переключения preset (split-tunnel OFF → ON)
| Действие | Результат |
|---|---|
| Диалог «Российские приложения — напрямую»: ВСЕ три категории выключены (0/4, 0/7, 0/9) → «ПРИМЕНИТЬ · весь трафик через VPN» | После перестройки: все 8 проверенных uid → tun0 (table 1072). `split_tunnel.json`: `{warp:{presetId:"russian_direct", updatedAt:…}}` (mode по умолчанию ALL) |
| Те же категории включены (4/4, 7/7, 9/9) → «ПРИМЕНИТЬ · 20 напрямую» | После перестройки: все проверенные uid → direct. `split_tunnel.json`: `russian_direct` / `ALL_EXCEPT_SELECTED` / ровно те же 20 пакетов (set-equal c бэкапом восстановления) |

### Пять приложений — функциональность и маршруты
Сбербанк, Т-Банк и Альфа дошли до экрана `OplusBiometricPrompt` (биометрическая защита Realme) —
до аутентификации; никаких учётных данных не вводилось.
Госуслуги (`ru.gosuslugi.android`) на устройстве не установлено — проверены семейства
`ru.gosuslugi.auto` («Госуслуги Авто») и `ru.gosuslugi.goskey` («Ключ Госуслуг»).
Почта России дошла до `MainActivity`.

| Приложение | Пакет | Установлено | UID | new ESTABLISHED-сокеты (local=SIM) | Класс |
|---|---|---|---|---|---|
| Сбербанк | ru.sberbankmobile | да | 10308 | 12 (79.133.169.64/61/98:1443, 62.76.91.139:443, 193.238.132.73:1443, +ipv4-mapped v6) | APP_FUNCTIONAL + DIRECT_ROUTE_CONFIRMED |
| Т-Банк | com.idamob.tinkoff.android | да | 10251 | 4 (178.130.128.21/129.47, 193.17.93.194, 18.65.39.62:443) — маршруты uid→rmnet_data2 подтверждены | APP_FUNCTIONAL + DIRECT_ROUTE_CONFIRMED |
| Альфа-Банк | ru.alfabank.mobile.android | да | 10794 | 20 (217.12.98.0/24 и др.:443) | APP_FUNCTIONAL + DIRECT_ROUTE_CONFIRMED |
| Госуслуги Авто | ru.gosuslugi.auto | да | 10863 | 9 (109.207.1.118, 172.217.x, 213.180.193.226:443) | APP_FUNCTIONAL + DIRECT_ROUTE_CONFIRMED |
| Госключ | ru.gosuslugi.goskey | да | 10314 | 1 (193.162.30.87:443) | APP_FUNCTIONAL + DIRECT_ROUTE_CONFIRMED |
| Почта России | com.octopod.russianpost.client.android | да | 10669 | 12 (91.215.37.131:443 и др.) | APP_FUNCTIONAL + DIRECT_ROUTE_CONFIRMED |

Внешний IP банковским приложениям не присваивался (они его не показывают); доказательство прямого маршрута —
локальный адрес сокета `10.114.147.138`/`::ffff:10.114.147.138` (SIM) и `ip route get <remote> uid <uid>` → rmnet_data2.

### netstats (поддерживающе, грубо: bucket 7200 с)
- uid=10308 (sber): rb=20989 tb=14309 (DEFAULT, mobile)
- uid=10251 (tinkoff): rb=138563 tb=95768 (DEFAULT, mobile)
- uid=10794 (alfa): rb=251484 tb=711223 (FOREGROUND, mobile)
- uid=10669 (pochta): rb=6527 tb=5341 (DEFAULT, mobile)
Данные не используются как единственное доказательство.

### IPv6 / DNS
- IPv4/IPv6 excluded: `ip -6 route get 2606:4700:4700::1111 uid 10308` → `Network is unreachable` —
  у исключённых приложений IPv6 нет вообще (SIM без IPv6; они не получают v6 и через tun0). Утечек нет, как и канала.
- Не-исключённые (app/shell): v6 через tun0 (`table 1071`, src `2606:4700:110:…`).
- DNS: исключённые приложения используют операторский DNS (10.221.138.1/10.220.138.1) — ожидаемо для direct.
  Туннелированные — DNS tun0 (1.1.1.1/1.0.0.1, UsePrivateDns).

### Устойчивость
| Проверка | Результат |
|---|---|
| ОТКЛЮЧИТЬ VPN → ПОДКЛЮЧИТЬ (WARP) | tun0 исчез/поднялся; в финальной серии table перечитывалась (1066→1071→1072) — норма перестройки |
| Force-stop приложения | tun0 исчез (`am force-stop`), VPN down |
| Повторный запуск приложения | VPN автоматически восстановлен (ПОДКЛЮЧЕНО, способ WARP, профиль №11, ping 62 мс), `connection_method=WARP`, `auto_connect=true`, exclusion-config 20 пакетов цел |
| Перестроение через «ПРИМЕНИТЬ» в диалоге | Маршруты перестроены корректно в обе стороны (OFF: всё в tun0; ON: 20 direct), настройки не теряются |

## Определения итогов (для каждого приложения)
- `APP_FUNCTIONAL` — приложение запускается и доходит до рабочего экрана;
- `DIRECT_ROUTE_CONFIRMED` — доказан выход мимо VPN (локальный SIM-адрес / uid-route / rmnet_data2);
- `TUNNEL_ROUTE_CONFIRMED` — доказан выход через VPN;
- `POLICY_APPLIED_UNVERIFIED` — приложение есть в исключениях, настройка применена, но обход туннеля не доказан;
- `UNKNOWN` — приложение работает, но маршрут достоверно не установлен.

## Не подтверждено / границы
- Аутентификация в приложениях не проводилась (биометрический шлюз) — работа подтверждена до экрана входа.
- Госуслуги (основное приложение) не установлено — вместо него проверены `ru.gosuslugi.auto` и `ru.gosuslugi.goskey`.
- WARP-выход по probe определён как «Россия» — как и что это значит для региональных особенностей —
  выходит за рамки этого теста (проверяется отдельно по задачам доступа к конкретным ресурсам).
- Wi-Fi DurDom2 не использовался (ломает excluded-пакет); переносимость выводов на Wi-Fi — не заявляется.
- «Пресет: Российские приложения» (legacy overwrite) больше не нажимается; находка — в backlog 2.0.8.
