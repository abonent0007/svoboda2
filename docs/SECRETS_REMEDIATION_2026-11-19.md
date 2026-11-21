# Секреты — прогресс ремедиации (2026-11-19, до истории-очистки)

**Статус:** Этап 1 (контроль доступа) — выполнен; Этап 2 (ротация) — инвентаризация готова, ожидает действий пользователя; Этап 3 (очистка Git) — подготовлен, ждёт ротации и подтверждения пользователя.
**Секретов в документе нет:** только SHA-256-фингерпринты (16 hex).

## Этап 1 — контроль доступа

| Шаг | Статус |
|---|---|
| Карантинная копия публичной истории (mirror @1812515, main + теги v2.0.0–v2.0.6) | DONE — `C:\Users\Abonen0007\Documents\svoboda2_quarantine_2026-11-19\mirror-svoboda2.git` + `SHA256SUMS.txt` (24 файла) + `refs_at_quarantine.txt` + `head_at_quarantine.txt`. Хранить строго локально: содержит все утёкшие ключи |
| Пассивный мониторинг DPI — остановлен по команде пользователя | DONE 13:15:09. Отчёт: `docs/DEVICE_RC_2026-11-19_evidence/passive_monitor_final_report_2026-11-19.md` (SHA-256 лога A89109D2F633D8F835AF015E590AB9D06DB344262FDB7D89408CA91E611B316F) |
| Репозиторий → private | ВЫПОЛНЕНО 2026-11-19 через stored git credential (PAT repo scope, без sudo — API принял); проверено API: `private: true`. Ветка `hardening-2026-11-19` запушена; приватный debug-релиз `build-hardening-2026-11-19-debug` (id 451303866, prerelease) и приватный release `build-hardening-2026-11-19-release` (id 451316622, R8, full release) с 3 release APK опубликованы. |
| Заморозка публикаций | ЗАФИКСИРОВАНО решением пользователя: до конца ремедиации никаких APK/публикаций/новых релизов |
| Удаление Release APK | Этап 3, после ротации |

## Этап 2 — ротация (по категориям)

### Сверка уникальных ключей (по fingerprints)

| Источник | Уникальные |
|---|---|
| HEAD `warp_identity.json` | 1 private_key + 1 token |
| HEAD `seeds/warp_verified_seeds.json` (74 raw_config) | **16** (configs сильно переиспользуют ключи) |
| HEAD `seeds/own_servers_seed.json` | 1 — ключ [Interface] (клиентский private key), [Peer] = server's public key (не секрет) |
| HEAD `public_servers/*.conf` (9 файлов) | **1** общий (`3ae608a3f2f4801d`) |
| HEAD итого | 19 private keys + 1 token (20 fingerprints); overlap'ов между группами — NONE |
| Mirror (как опубликовано) | 30 keys + 1 token; теги v2.0.0–v2.0.2 = 29 (ключ добавился в v2.0.3) |
| Локальные ветки сверх mirror | + 3 keys (local only, никогда не публиковались) |

Публичная утечка = **31 fingerprint (30 keys + 1 token)**; с учётом локально-незапушенного — 34.

### По категориям — статус и действия

| Категория | Статус | Выполнено | Нужное действие |
|---|---|---|---|
| warp_identity (7229d585354b1578 / token 77830673e2eb0900) | считать identity скомпрометированной, отозвать | НЕТ — потребительский API (`api.cloudflareclient.com/v0a2158/reg`) недоступен из сети; Zero Trust API (account/registration ID + `Zero Trust Write`) для consumer identity не применим без данных аккаунта | Ожидать восстановление CF API либо официального подтверждения, что consumer identity отозвать нечем — считать сгоревшей и не использовать |
| 74 seed identity → 16 unique (`040295bd…` … `cf8c29943e2e8c80`) | не используются в сборках | Удаление из дерева/APK — в Этапе 3. Сейчас: ни одна не попадает в сборки/ассеты | Нет технического способа «отозвать» через consumer API; трактуются как burned |
| Shared AWG key 9 public_servers (`3ae608a3f2f4801d`) | новая client identity + заменить peer-записи на контролируемых серверах | НЕТ | Уточнить: это собственные VPS (`31.76.110.142` NL ×3, `144.31.28.185` FI ×3 + `any_*`)? Если да — нужно SSH-доступ или пользователь сам заменяет `peer` на каждом; если нет — ключ сгорает, замена только через новые WARP/AWG enrollment'ы |
| own_servers (`640dd30cc08e8078`, [Interface]) | ротировать через SSH после подтверждения роли | Роль подтверждена: скомпрометирована клиентская пара; server side = `[Peer] PublicKey` (публичный ключ, не секрет). Новый client keypair + замена peer на `31.58.58.133:51820` (Frankfurt VPS) + новый seed | Нужен SSH-доступ к серверу или выполнение замены пользователем |

### Команды для own-server ротации (выполняет тот, у кого SSH к 31.58.58.133)

```bash
# на клиентской машине (любое wg-compatible устройство):
wg genkey | tee client_private.key | wg pubkey > client_public.key
# на сервере (wg0.conf / drop-in): заменить старый Peer.PublicKey = <старый client pubkey>
#   на <новый client_public.key>; затем: sudo wg syncconf wg0 <(wg-quick strip wg0)
# (либо рестарт wg-quick@wg0)
# после этого: новый client .conf (новый PrivateKey, Endpoint 31.58.58.133:51820,
#   тот же [Peer] PublicKey сервера, AllowedIPs как раньше) → заменить seed в приложении и пересобрать APK
```

## Этап 3 — подготовлен

- `git filter-repo` — не установлен; `gitleaks` — не найден. Установка через `python -m pip` — требует разрешения пользователя.
- План (после ротации и подтверждения): удалить 4 asset-файла/папки из дерева → `git filter-repo --sensitive-data-removal` по всем refs → проверить отсутствие fingerprints во всех коммитах → `git push --force` (согласованный) → обращение в GitHub Support (очистка кеша/PR refs) → удаление старых Release APK → включить Push Protection → Gitleaks в CI → исключить ключи из APK (проверка боевой сборки).

## RDAP/WHOIS по серверам (2026-11-19, публичные реестры RIPE)

| IP | Subnet | Провайдер (registrant) | Страна | Регистрация | Замечание |
|---|---|---|---|---|---|
| 31.58.58.133 | 31.58.58.0/24 | FREAKHOSTING-LTD (freakhosting.com), admin Vlad Alexandru | DE | 2024-11-18 | арендованный VPS; abuse: report@abuseradar.com |
| 31.76.110.142 | 31.76.110.0/24 | Aurorix Networks, Amsterdam (aurorix.net; noc@antiddos.pw) | NL | 2026-05-11 | арендованный VPS; abuse: abuse@aurorix.net |
| 144.31.28.185 | 144.31.28.0/24 | NODE HOST LIMITED («Intezio»), London, abuse@intezio.net | FI | 2026-06-04 | арендованный VPS; admin/tech Intezio |

Вывод: это арендованные VPS у трёх разных провайдеров (2 с регистрацией в Лондоне + 1 в Амстердаме). Все три — «SUB-ALLOCATED PA». Контроль над WireGuard peer-записями на них определяется владельцем аккаунтов у этих провайдеров, но RDAP этого подтвердить/опровергнуть не может. Ротацию с серверной стороны выполняет владелец аккаунта провайдера; общий ключ публичных .conf (3ae60…) считать сгоревшим, если владелец — не пользователь.

## Ротация Frankfurt — подготовлена клиентская часть (2026-11-19, НИЧЕГО НЕ РАЗВЕРНУТО)

- Новый X25519 client keypair: `new_keys/frankfurt_client_private.key` (32B raw, base64), `frankfurt_client_public.key`. Публичный ключ выводится из приватного (проверено нодой). Отличен от скомпрометированного `640dd30cc08e8078`. АCL папки `new_keys` — только текущий пользователь.
- `new_keys/own_servers_seed.ROTATED.json` — заменяющий seed: `id = komarovai-frankfurt-1-rotated`, endpoint 31.58.58.133:51820 и server peer pubkey (PLgGwF9qY1…) сохранены, `PrivateKey` заменён на новый клиентский. [Interface]-ключ сервера не затронут — серверная сторона (peer-запись на 31.58.58.133) должна быть обновлена вручную: заменить старый клиентский peer на новый public key. До замены на сервере новый seed не работает против старого peer.
- X25519 проверен двумя независимыми реализациями с идентичным результатом DH (python-ladder == OpenSSL/Node).
- Ключи для WARP-сторон (identity/seeds/public_servers) не генерировались: отзыв возможен только через enrollment (CF API недоступен) — смысла нет до восстановления доступа.

## Текущее состояние

- Репозиторий `abonent0007/svoboda2` — приватный. Release APK публично не доступны.
- Форков: 0 (по данным GitHub на 2026-11-19). Stars: 1. Fork-detach зафиксирован диалогом GitHub.
- Все вышеперечисленные ключи считаются скомпрометированными и трактуются так до отзыва.
