# router-libs — артефакты userspace-ядра мульти-роутинга (mihomo / sing-box / Clash)

Сюда кладутся **готовые .so** (или исходники + build-скрипт) ядра роутинга.
Каталог не содержит бинарников — их добавляет агент, который соберёт ядро.

## Ожидаемые файлы (по ABI)

```
router-libs/
  arm64-v8a/librouter.so
  armeabi-v7a/librouter.so
  x86_64/librouter.so
```

Имена могут отличаться (libmihomo.so, libsing-box.so) — фиксируются в
`app/src/main/java/svoboda3/runet/abonent0007/router/RouterBridge.kt`.

## Контракт

- JNI-мост: `RouterBridge.start(context, config, tunFd)` / `stop()` / `stats()`.
- Принимает **существующий** TUN fd от VpnService (не создаёт свой — один VPN на пользователя).
- Конфиг — YAML/JSON ядра (tun input, upstream'ы, правила по UID/пакету).
- Правила должны уметь: direct / DPI-socks / AWG / WARP как разные upstream'ы для разных приложений.

## Интеграция

- `app/src/main/jniLibs/<abi>/` — symlink/копия сюда для упаковки в APK (см. README проекта).
- Лицензии ядра — в `docs/SVOBODA3.md` (раздел «Лицензии»).
