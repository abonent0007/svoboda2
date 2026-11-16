
---

## 6. Реализовано в этой сессии (по итогам ревью)

### P0 — AUTO v1 (сделано)

- Кнопка **AUTO** на главном экране активна (`ConnectionMethod.AUTO.available = true`).
- `ConnectionManager.autoConnect()`: кандидаты строятся по порядку транспортов
  (AWG → WARP → DPI) и по приоритету стран (NL → FI → US):
  свои AWG-серверы по `countryCode` → любые AWG-серверы → WARP-профили с измеренной
  страной → обычный WARP → DPI.
- Переключения по foreground-приложению НЕТ (по правке GPT) — это рвёт активные сессии.
- Антидребезг: удержание текущего транспорта `autoHoldMinutes` (по умолчанию 5 мин);
  переход на следующего кандидата — только при неудаче подключения.
- Строгая/гибкая политика страны (`autoCountryStrict`): в строгом режиме AUTO не уходит
  на другие страны, если профиля нужной страны нет.
- Страна выхода у WARP-профиля **измеряется** (Cloudflare trace → loc) после подключения
  и пишется в профиль (`observedCountry`, `countryCheckedAt`).
- UI: раздел «AUTO · автоматический подбор» — порядок транспортов, приоритет стран
  NL/FI/US, строгая страна, удержание.

### Раздельное туннелирование по сервисам (сделано)

- Каталог диапазонов IP по сервисам (16 сервисов: YouTube, Telegram, Discord, WhatsApp,
  Twitter, Instagram, Facebook, Viber, Itch.io, RuTracker, Rutor, NNMClub, BestChange,
  AnimeGo, Signal, Proton) — данные из `nellimonix/warp-config-generator-vercel` (MIT).
- Выбор сервисов → `AllowedIPs` в конфиге → наш GoBackend строит маршруты **по AllowedIPs**,
  поэтому в туннель уходит только трафик выбранных сервисов, остальное идёт напрямую
  (включая банки и госсервисы).
- Пикер сервисов добавлен и в WARP-раздел, и в DPI-раздел.

### Генератор WARP (расширен)

- Шаблоны: AWG 1.0 (junk), AWG 2.0 (I1/I2/I3), AWG 3.0-параметры
  (ContentPaddingAddition, RekeyAfterTime, RekeyTimeout, RejectAfterTime,
  KeepaliveTimeout, MaxHandshakeAttempts — поддерживаются нашим парсером), AWG 3.1
  (RandomTrailers/DisableCookies), LTE-режим (MTU 1280 + keepalive 25 + Cloudflare-only).
- Свой генератор QUIC-Initial для I1 (`QuicInitial`) — пакет по форме RFC 9000.
- DNS-пресеты (7 шт.) + собственная строка DNS + **проверка валидности реальным
  DNS-запросом** (✓/✗ + задержка) — и в настройках DNS приложения, и в генераторе WARP.
- Endpoint-пресеты (Cloudflare anycast / hostname engage.cloudflareclient.com / FRA),
  пул публичных подсетей, генерация N профилей с рандомизированными junk-параметрами.

### Чего сознательно не делаем сейчас (P2/P3)

- Настоящие одновременные разные туннели для разных приложений — только single-tun +
  multi-upstream роутер (sing-box с package-based правилами; `process_name` на Android
  недоступен). Требует отдельной экспериментальной ветки.
- Защита сокетов ciadpi от VPN (FD-passing или in-process) — влияет на устойчивость
  DPI-режима и на end-to-end измерения.
- QUIC/UDP через SOCKS5-ciadpi не переносится — YouTube QUIC и голос не покрыты.
