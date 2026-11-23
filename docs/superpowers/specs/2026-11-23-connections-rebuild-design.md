# Дизайн: переделка модулей подключений «Свобода 2.0» → 2.1.0

Дата: 2026-11-23. Статус: утверждено пользователем.

## Контекст

На устройстве (Realme RMX3370, Tele2) устойчиво работает **AWG**. WARP был мёртв из-за
недоступности Cloudflare API/сидов, но у пользователя есть собственный **Cloudflare Worker
`obnova-warp`**, который проксирует генерацию и регистрацию (обходит SNI-блок и зеркала).
DPI требует доводки по образцу **ByeByeDPI** (github.com/romanvht/ByeByeDPI).

## Целевая архитектура

- Методы: **AWG**, **WARP**, **DPI**. **AUTO = AWG → WARP → DPI**.
- WARP остаётся, но генерация/регистрация идут через relay `obnova-warp`
  (`GET /` → identity; `GET /reg` → регистрация; `GET /health`).
- Зарубежные серверы генератора (tribukvy.ltd) — опция с пометкой о регистрации в боте.

## Фазы (порядок утверждён)

### SP2' — WARP через relay (вместо удаления)
- `WarpGenerator`: запись на relay (`/` для identity, `/reg` для регистрации) с fallback
  на публичные mirror-эндпоинты; убрать прямые обращения к `api.cloudflareclient.com`.
- Кешировать identity на время сессии; пробовать mirror при 429/1015.
- Настройки WARP: показывать статус relay, кнопку обновления identity.

### SP3 — автогенерация AWG
- Identity: relay `obnova-warp` (`/`) с fallback на `warp-*.warp-api.cfd`.
- Сборка AWG-конфига: `[Interface] PrivateKey/Address/DNS/MTU/S1..S4=0/Jc=4/Jmin=40/Jmax=70/H1..H4/I1…`;
  `[Peer] PublicKey/AllowedIPs/Endpoint/PersistentKeepalive`.
- 3 предустановки AWG 2.0: v1 — штатный I1-блоб; v2 — QUIC-маска; v3 — SIP INVITE I1+I2.
- Страны: Standard (Cloudflare anycast) + FI/NL/PL/LV/DE/EE/RU + [LTE] FI/PL/DE
  (зарубежные — с пометкой «требует регистрации в боте llimonix»).
- Опции: DNS (существующий каталог), MTU, IPv6, PersistentKeepalive, AWG 3.0/3.1.
- Сохранение как профиля сервера (OwnServer) и подключение штатным AWG-путём.

### SP1 — DPI по ByeByeDPI (функции; движок ciadpi+HEV не трогаем)
Именованные пресеты CMD, тест через прокси (с прогрессом), списки доменов, импорт/экспорт
настроек, автозапуск при загрузке, обработка Private DNS. Не дублировать то, что уже есть:
каталог стратегий, конструктор, YouTube-подбор, mapdns, тест через SOCKS (raw socket).

### SP4 — агрегатор подписок (отложен)
`https://sub-aggregator.vercel.app/data.txt` — V2Ray/Vless/VMess/Trojan/SSR.

## Версия
2.1.0 (versionCode 220). Публикация — после проверки на устройстве и ОК пользователя.

## Приёмка
- Сборка release + unit-тесты.
- На устройстве: AWG по сгенерированному профилю; WARP через relay; DPI; AUTO AWG→WARP→DPI.
- Регрессия: split-tunnel (20 direct), About, апдейтер, YouTube-подбор.
