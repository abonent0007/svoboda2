# Frankfurt VPS (31.58.58.133) — runbook ротации скомпрометированного client peer

**Статус:** подготовлено к выполнению. Выполняется только пользователем (или оператором с его доступом) на сервере.
**Причина:** утечка `[Interface]` PrivateKey bundled seed'а («komarovai-frankfurt-1», own_servers_seed.json) — любой обладатель ключа может авторизоваться как этот клиент.
**Новый client public key (можно передавать открытым текстом):**
`+JKYBmflSCosTX6fcZu5LByJe2ArVNU7yg/XtEipby8=`
**Приватную часть храните локально**: `C:\Users\Abonen0007\Documents\svoboda2_quarantine_2026-11-19\new_keys\frankfurt_client_private.key` (ACL: только ваш пользователь; Documents не синхронизируется в облако — проверено 2026-11-19: OneDrive отсутствует, других клиентов синхронизации не найдено).

## Шаг 0. Сохранить старый peer (для анализа/отката)
На сервере: `sudo cp /etc/wireguard/awg0.conf /etc/wireguard/awg0.conf.bak-$(date +%Y%m%d)` (путь может быть `awg0.conf` / `wg0.conf` / `netplan`-less amneziawg — подставить фактический файл).

## Шаг 1. Добавить НОВЫЙ peer (не удаляя старый)
В секцию `[Peer]` (амнезия-форк, `PostUp`/`SaveConfig` как настроено) добавить:
```
[Peer]
PublicKey = +JKYBmflSCosTX6fcZu5LByJe2ArVNU7yg/XtEipby8=
AllowedIPs = 172.16.0.2/32
```
`AllowedIPs` должен совпадать с выдачей адреса клиенту (172.16.0.2 в bundled seed). Совпадений с другими peer не допускается.
Применить: `sudo awg syncconf awg0 <(awg-quick strip awg0)` (amneziawg) либо `sudo wg syncconf wg0 <(wg-quick strip wg0)` (wireguard).
Проверить: `sudo awg show awg0 peers` — новый ключ появился.

## Шаг 2. Переключить клиент на новый seed
На телефоне (после разрешения пользователя): заменить own-server seed на `new_keys/own_servers_seed.ROTATED.json` (только после Шага 1!), переподключить DPI/AWG метод, убедиться:
`sudo awg show awg0 latest-handshakes` — новый peer рукопожатся.

## Шаг 3. Проверка работоспособности
- Handshake fresh, трафик идёт (ping/HTTPS через туннель, DNS OK).
- Старый peer удалён ли — проверить после Шага 4.

## Шаг 4. Удалить старый скомпрометированный peer
Найти старый ключ: `sudo awg show awg0 peers` и сравнить с отпечатками утёкших (640dd30cc08e8078 — визуальное сличение по файлу; значения ключей см. в карантине).
Удалить: `sudo awg set awg0 peer <old_client_pub> remove`.
Применить: `sudo awg syncconf awg0 <(awg-quick strip awg0)`.

## Шаг 5. Проверка отзыва
- `awg show awg0 peers` — старого ключа нет.
- Попытка подключиться со старым seed → handshake не проходит (на сервере: `latest-handshakes` не обновляется; на клиенте: handshake timeout).
- Записать результат в `docs/SECRETS_REMEDIATION_2026-11-19.md` (отпечаток нового peer, sha256 базовой64[:16] = `a3e80a3da5b5951f`; старый сгоревший = `640dd30cc08e8078`).

## Откат
Если новый peer не взлетел до Шага 4: вернуть `awg0.conf.bak-<дата>` (`sudo cp` обратно + `awg syncconf`), клиент на старый (сгоревший!) seed не возвращать — лучше временно без AWG. Ошибки Шага 4 необратимы: перед удалением убедиться, что новый peer работает.

## Дальше (после подтверждённой ротации)
- Разблокировать Этап 3 (purge/Releases/Support) — только по отдельному решению пользователя.
- Frankfurt [Interface] (серверный ключ) не затронут — он и так никогда не покидал сервер.
- own_servers .conf seed обновлён на стороне репозитория только после Шага 5.
