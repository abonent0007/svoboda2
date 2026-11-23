# Дизайн: переделка модулей подключений «Свобода 2.0» → 2.1.0

Дата: 2026-11-23. Статус: утверждено пользователем (порядок SP2→SP3→SP1, агрегатор отложен).

## Контекст

На устройстве (Realme RMX3370, Tele2) работает только **AWG**. WARP устарел: генерацию
конфигов достать негде (Cloudflare API из РФ недоступен, публичные сиры мертвы).
DPI требует доводки по образцу проверенного приложения **ByeByeDPI**
(github.com/romanvht/ByeByeDPI).

## Целевая архитектура

- Методы подключения: **AWG** (AmneziaWG, свои серверы + автогенерация) и **DPI**
  (локальный обход, ciadpi+HEV — движок остаётся).
- **AUTO = AWG → DPI** (без WARP).
- WARP удалён полностью: метод, настройки, генератор, профили, seed'ы, публичные
  серверы, WARP-ветка AUTO, префы, ассеты, состояние регистрации.

## Фазы

### SP2 — удаление WARP (первая)
Убрать: `ConnectionMethod.WARP`, `WarpSettings`, `WarpGenerator`/`WarpProfileFactory`/
`WarpTemplates`/`WarpModels`/`SeedCatalog`/`GeneratedProfileStore`/`QuicInitial`,
публичные серверы (`PublicServerCatalog` + `assets/public_servers`), WARP-ветку AUTO,
`useWarp`/`connectWarpWithGeneration`/`connectGeneratedProfile`/`connectPublicServer`/
`applyWarpSplit`, префы (`warpTunnelApps`, `autoIncludePublicServers`,
`publicServerCountries`, `serviceDirectSelection`, `lastGoodConfig`/`lastGoodLabel`),
`assets/seeds/*`, `assets/warp_identity.json`, `WarpRegistrationPhase`.
`ConfigNormalizer` и `ConfigValidator` используются и для AWG — перенести в `vpn`/`net`
без WARP-специфики. Обновить тесты.

### SP3 — автогенерация AWG
- Identity: `GET https://{warp-workers|warp-sub|warp-vercel-chi|warp-vercel-murex|warp-netlify}.warp-api.cfd`
  (заголовок `X-Client: WARP`, fallback по списку) → `{privKey, peer_pub, client_ipv4, client_ipv6}`.
- Сборка AWG-конфига: `[Interface] PrivateKey/Address/DNS/MTU/S1..S4=0/Jc=4/Jmin=40/Jmax=70/H1..H4/I1…`;
  `[Peer] PublicKey/AllowedIPs/Endpoint/PersistentKeepalive`.
- **3 предустановки** AWG 2.0: v1 — штатный I1-блоб; v2 — QUIC-маска; v3 — SIP INVITE I1+I2.
- **Страны**: Standard (Cloudflare anycast, без регистрации) + FI/NL/PL/LV/DE/EE/RU + [LTE] FI/PL/DE
  (зарубежные — с пометкой «требует регистрации в Telegram-боте llimonix»).
- Опции: DNS (из существующего каталога), MTU, IPv6, PersistentKeepalive, AWG 3.0/3.1.
- Сохранение как профиля сервера (OwnServer) и подключение штатным AWG-путём.

### SP1 — DPI по ByeByeDPI (функции, движок не трогаем)
Перенести: именованные пресеты CMD, тест через прокси (с прогрессом), списки доменов,
импорт/экспорт настроек, автозапуск при загрузке, обработка Private DNS.
Не дублировать то, что уже есть: каталог стратегий, конструктор, YouTube-подбор, mapdns,
тест через SOCKS (_raw socket_).

### SP4 — агрегатор подписок (отложен)
`https://sub-aggregator.vercel.app/data.txt` — V2Ray/Vless/VMess/Trojan/SSR.
Решение о применении принять позже.

## Версия
2.1.0 (versionCode 220). Публикация — после проверки на устройстве и ОК пользователя.

## Приёмка
- Сборка release + 66 unit-тестов.
- На устройстве: AWG по сгенерированному профиля подключается и даёт выход;
  DPI работает; AUTO переключается AWG→DPI; WARP в приложении отсутствует.
- Регрессия: split-tunnel (20 direct), About, апдейтер, YouTube-подбор.
