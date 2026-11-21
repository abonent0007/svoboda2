# DPI — план восстановления (аудит 2026-11-19)

**Статус:** план + первичные результаты. Код НЕ изменён. Телефон: DPI_DIRECT, «ПОДКЛЮЧЕНО», ничего не трогаем без решения пользователя.
**Новый приоритет (решение пользователя):** вернуть прежнюю функциональность DPI (Telegram + YouTube + тестирование стратегий), а не наращивать AUTO/WARP.

**Правило проекта (обязательное архитектурное требование):** ни один компонент, который уже доказанно работал, не удаляется и не заменяется без регрессионного сравнения с его последней рабочей версией.

**Архив аудита:** `C:\Users\Abonen0007\Documents\svoboda2_audit_2026-11-19\` (3 snapshot: HEAD / af2ee74 / v2.0.6, evidence, MANIFEST, SECURITY_NOTICE). `.git` намеренно НЕ включён: история содержит публичные приватные ключи (см. направление E). Очищенный Git bundle — только после отдельного решения и purge истории.

## Направление A — восстановление известной рабочей конфигурации

- Найти последнюю сборку, где DPI одновременно давал Telegram и YouTube; зафиксировать: commit, SHA-256 APK, стратегию ByeDPI, порты, цепочку TUN → tun2socks/HEV → ciadpi, DNS, split tunnel, исключения.
- Веха: 0af722d (2026-11-16, DPI-1.3/1.4: ciadpi + HEV), 853d091 (DPI benchmark + AUTO v1), 2.0.6 (03a588c), RC1 (af2ee74, 2026-11-19).
- **PASS:** документированы exact ref + APK SHA-256 + конфиг; воспроизведено на устройстве при разрешении пользователя.
- Откат: n/a (read-only этап).

## Направление B — DPI Data Plane (факты на 2026-11-19, read-only)

- Chrome example.com **через TUN** (curl с устройства; shell uid 2000 → `ip rule` 13000 → таблица 1334 = tun94): HTTP 200 за ~0.65–0.75 с.
- **DNS через DPI флаки:** разовый `curl: (28) Resolving timed out after 8000 ms` при живом маршруте (обрыв 12:42, восстановился). Замеряется пассивным мониторингом.
- **Telegram DC:** ConnectionResetException (сохраняется, отдельно не тестировался сейчас).
- **YouTube:** не проверялся в этой сессии.
- Приложение: `SvobodaVpn … oldPhase=CONNECTED`, `markDegraded degraded=false` — health tick OK.
- **Не засчитывать `socks OK` как PASS DPI.** PASS = реальная HTTPS через TUN на уровне приложений (Chrome) + Telegram + YouTube + DNS без регулярных таймаутов.

## Направление C — краш при «тест стратегий» (первоисточник, dpi_crash.log, 2026-11-16 15:16:21)

- `F/net.abonent0007 … JNI DETECTED ERROR IN APPLICATION: JNI RegisterNatives called with pending exception java.lang.ClassNotFoundException: Didn't find class "hev.htproxy.TProxyService"`.
- Путь: `VpnController$prepareDpiSession$1.onDpiTunFd` → `HevHolder.nativeStart` → `HevBridge.<clinit>` (HevBridge.kt:12) → `loadLibrary("hev-socks5-tunnel")` → `libhev-socks5-tunnel.so (JNI_OnLoad+184)` → RegisterNatives в `hev/htproxy/TProxyService` → класс не существует в APK → **CheckJNI abort** (debuggable сборка) → tombstone_19 (`/data/tombstones`), «Force finishing activity svoboda.runet.abonent0007/.MainActivity».
- Trigger: tap «654,1956» (кнопка стратегий на экране DPI 11-16).
- **Текущее HEAD-состояние:** HEV core `.so` **без JNI вообще** (SHA-256 d40ee44a…, идентичен в `app/jniLibs` и `app/build/…/cxx/…/obj`); JNI glue — `app/src/main/cpp/hev_dpi_jni.cpp` → `libsvoboda-dpi.so`, классические символы `Java_svoboda_runet_abonent0007_dpi_HevBridge_nativeStart` — без `FindClass`/`RegisterNatives`/`JNI_OnLoad`. Описанный краш на свежей HEAD-сборке структурно невозможен, **если** в APK попадает именно этот core — требует бит-в-бит проверки трёх ABI (arm64-v8a, armeabi-v7a, x86_64) в собранном APK.
- dpi_crash.log снят с установки 11-16 (другая install-path), версия до HEAD-переписывания JNI.
- **Открытые вопросы аудитору:** (C1) сверить SHA-256 .so в собранном APK для трёх ABI; (C2) тип сборки/checkJNI; (C3) воспроизвести «тест стратегий» на HEAD-сборке на устройстве (при разрешении пользователя; процесс просто умирает — DPI-туннель не задевается); (C4) если не воспроизводится — закрыть.
- Мёртвый код `third_party/hev-socks5-tunnel/src/hev-jni.c` (PKGNAME `hev/htproxy`, CLSNAME `TProxyService`) — требует регрессионного аудита перед изменением.

## Направление D — «пропавшая» кнопка тестирования стратегий

- В `HomeScreen.kt` её **никогда не было** (`git log -S 'DpiBenchmarkDialog'|'Benchmark'|'ТЕСТ'` — пусто). Тест стратегий открывается из «Настройки → DPI · тонкие настройки → DpiSettings → `DpiBenchmarkDialog`» (`MethodSettings.kt:137`, `showBenchmark`).
- В 2.0.3 (0df5a6d) per-method UI увезли в отдельные submenus; главный экран = «ПОДКЛЮЧИТЬ / ПРОВЕРИТЬ СВЯЗЬ».
- «Исчезновение» = (а) краш в момент нажатия (визуально неотличимо от «кнопки нет»), (б) вложенность на 2 уровня, (в) отсутствие точки входа с главного экрана.
- **Решение по UX — только после A** (восстановление) и только пользователем: возвращать ли вход на главный экран; реализовывать точечно, с отдельным коммитом и откатом.

## Направление E (новое, из аудита) — 76 приватных ключей + CF token в публичном git

- См. `SECURITY_NOTICE.md` в архиве: `warp_identity.json` (key+token+device_id), `warp_verified_seeds.json` (74 raw_config PrivateKey), `own_servers_seed.json` (own AWG server key + 31.58.58.133:51820), `public_servers/*.conf` (9 файлов, ОДИН общий PrivateKey).
- Репозиторий `github.com/abonent0007/svoboda2` — был публичным на момент проверки 2026-11-19. Все ключи скомпрометированы с первого коммита (8c517ec).
- Архив аудита эти файлы не содержит. Ротация + purge истории + замена/удаление GitHub Release APK — по явной авторизации пользователя.

## Порядок работ (PASS-критерий + откат для каждого этапа)

| Этап | Что | PASS | Откат |
|---|---|---|---|
| 1 | A: идентифицировать рабочую конфигурацию (read-only) | ref + APK SHA-256 + конфиг задокументированы | n/a |
| 2 | Восстановить Telegram + YouTube на рабочей конфигурации | оба работают + Chrome HTTPS | возврат на RC1/diagnostic сборку |
| 3 | C: on-device проверка краша на HEAD-сборке | «тест стратегий» открывает диалог, FATAL отсутствует | не требуется (краш безопасен) |
| 4 | D: UX-решение пользователя по кнопке; точечный фикс | видна, открывает диалог, UI не сломан | revert коммита |
| 5 | Стратегия сохраняется между переподключениями | после reconnect — выбранная конфигурация | revert |
| 6 | Дублирование процессов / порты / lifecycle (если аудит подтвердит) | отдельный коммит + ревью | revert |
| 7 | Только после 1–6: архитектурный рефакторинг | — | — |

## Контрольная матрица DPI (решение пользователя, FINAL)

Chrome HTTPS · Telegram DC подключаются и чаты загружаются · YouTube (лента, миниатюры, видео) · DNS без регулярных таймаутов · «тест стратегий» виден и не крашит · отчёт со списком стратегий и результатами · выбранная стратегия сохраняется после переподключения · split tunnel 20 direct-пакетов неизменны. `socks OK` ≠ PASS.

## Текущий фокус

AUTO/WARP прогоны · новые WARP identity · DPI→AUTO · смена DNS/ключей/quarantine/split tunnel · релиз 2.0.7 · любые правки кода — до завершения этапов 1–3 и решений пользователя.

## Открытые вопросы пользователю

1. Авторизация на ротацию/purge секретов (E): force-push очищенной истории, удаление/замена Release APK, ротация own-server ключа (SSH к VPS) — и что делать при недоступном CF API (новые WARP identity пока не зарегистрировать).
2. Авторизация на on-device воспроизведение краша (C3) для HEAD-сборки.
3. Нужна ли кнопка «тест стратегий» на главном экране (D)?
4. Судьба пассивного мониторинга после ~15:00 (лог уже даёт DNS/HTTPS/CF-базлайн; продлевать?).
