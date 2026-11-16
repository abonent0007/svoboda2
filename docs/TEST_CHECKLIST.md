# Device Verification Checklist — v2.0.3

Device: Realme RMX3370 (Android 13, arm64-v8a) · serial 97db202c · network: LTE (Tele2)

## Build / install
- [x] `gradlew assembleDebug` — BUILD SUCCESSFUL (arm64, armeabi-v7a, universal)
- [x] `adb install -r app-arm64-v8a-debug.apk`
- [ ] App launches without crash (verify `dumpsys window | grep mCurrentFocus`)

## Foreground Service (P3.10-B)
- [ ] После подключения: `dumpsys activity services | grep -i svoboda` — сервис в foreground
- [ ] Уведомление «Свобода 2.0 — VPN-туннель активен» отображается в шторке
- [ ] После отключения: уведомление исчезает
- [ ] onRevoke (системный отзыв): UI показывает REVOKED, автопереподключения нет

## Способы подключения
- [ ] Главный экран: чипы AWG / WARP / DPI / AUTO
- [ ] DPI и AUTO не выбираются (в разработке), AWG/WARP выбираются
- [ ] Выбор сохраняется после перезапуска

## Подменю тонких настроек
- [ ] AWG: серверы, MTU (слайдер + пресеты 1380–1480), генератор .conf
- [ ] WARP: «Новая WARP-регистрация» — успешный запрос к Cloudflare, endpoint + IP
- [ ] DPI: список BDP-00…BDP-12, SOCKS-порт, «Собрать стратегию» — превью аргументов
- [ ] AUTO: политика предпочтения (AWG/WARP), резервное переключение

## Российские приложения — напрямую
- [ ] Открывается подменю, показаны только установленные приложения
- [ ] Переключатель категории (все банки / все гос / все веб)
- [ ] Покомпонентные переключатели
- [ ] «Применить» сохраняет конфигурацию; при активном VPN — переподключение
- [ ] Direct-маршрут работает: банковские приложения открываются без VPN (проверить Сбер/Т-Банк)

## MTU
- [ ] По умолчанию 1420; слайдер до 1480; пресеты 1380/1400/1420/1440/1460/1480 в одну линию

## Автообновление
- [ ] При запуске (2.0.3 == latest): ничего не предлагается
- [ ] Кнопка «Обновления»: статус «Обновлений нет»
- [ ] 2.0.2 → 2.0.3: при наличии релиза происходит скачивание и запуск установщика (проверить вручную)

## Регрессия
- [ ] AWG/WARP connect/disconnect
- [ ] Нет ложного CONNECTED после отзыва
- [ ] Handshake timeout → ошибка, backoff ≤ 60 с
- [ ] Split-tunnel применяется (ExcludedApplications)

## Evidence
- [ ] `docs/shots/release_main.png`
- [ ] `docs/shots/release_drawer.png`
- [ ] `docs/shots/release_mtu.png`
- [ ] `docs/shots/release_russian_direct.png`
- [ ] `docs/shots/release_method_settings.png`
