# RUNBOOK: очистка истории от секретных ассетов (Этап 3)

**НЕ ВЫПОЛНЯТЬ СЕЙЧАС.** Только после: (1) sudo-2FA сделан — репозиторий Private; (2) ротация по Этапу 2 подтверждена пользователем по каждой категории; (3) пользователь отдельно сказал «выполняй Этап 3». Ни один шаг force-push/удаления не выполнен.

**Учётные данные утечки:** 4 ассета (см. `SECRETS_REMEDIATION_2026-11-19.md`): `warp_identity.json`, `seeds/warp_verified_seeds.json`, `seeds/own_servers_seed.json`, `public_servers/` (9 .conf, 1 общий ключ). Уникально: 19 ключей + 1 токен в HEAD; в origin опубликовано 30 ключей + 1 токен; локально-незапушенные ветки содержат ещё 3 ключа.

## 0. Preconditions (все должны быть закрыты)

- [ ] Репозиторий Private (бейдж проверен).
- [ ] Ротация WARP: consumer identity отозвана через доступные средства ИЛИ официально задокументировано её сгорание; APK с новой регистрацией собирается только с новыми ключами.
- [ ] Frankfurt (`31.58.58.133`): старый peer заменён на новый client pubkey; старый серверный peer удалён; новый seed проверен.
- [ ] public_servers: серверы-владельцы подтвердили удаление старого общего peer; иначе ключи сгорели, заменяем только enrollment'ом.
- [ ] `python -m pip install git-filter-repo` — разрешено пользователем.
- [ ] Карантинная копия на месте: `C:\Users\Abonen0007\Documents\svoboda2_quarantine_2026-11-19\mirror-svoboda2.git` (fsck OK, SHA256SUMS.txt). Не удалять и не мутировать.
- [ ] Решение по Release APK: удалить все релизы v2.0.0–v2.0.6 (это сотрёт ~30 загрузок распространения) ИЛИ загрузить заменённые, очищенные APK и затем удалить старые.

## 1. Rewrite (на СВЕЖЕМ clone, не на рабочей копии)

```powershell
$pw = 'C:\Users\Abonen0007\Documents\svoboda2_purge_work'
mkdir $pw
git clone --mirror file:///C:/Users/Abonen0007/Documents/svoboda2_quarantine_2026-11-19/mirror-svoboda2.git "$pw\svoboda2.git"
cd $pw\svoboda2.git
git filter-repo --invert-paths `
  --path app/src/main/assets/warp_identity.json `
  --path app/src/main/assets/seeds/warp_verified_seeds.json `
  --path app/src/main/assets/seeds/own_servers_seed.json `
  --path app/src/main/assets/public_servers `
  --force
```

filter-repo удаляет эти пути из ВСЕХ refs. Документы не трогаем — в них ключей нет (только `<dummy …>` и template-строки кода).

## 2. Верификация нулевого результата (обязательная)

```powershell
python C:\Users\Abonen0007\.local\share\opencode\shell\fingerprint_inventory.py
```
Ожидаемо: unique_private_keys == 0 и tokens == 0 на ВСЕХ refs (кроме удалённых path'ей — нулительность должна быть полной). Любой ненулевой счётчик → стоп, разбираться.
Дополнительно: `git rev-list --objects --all | grep -E 'warp_identity|warp_verified_seeds|own_servers_seed|public_servers'` → пусто.

## 3. Публикация очищенной истории (только по отдельной команде пользователя)

```powershell
cd $pw\svoboda2.git
git remote add origin https://github.com/abonent0007/svoboda2.git
git push origin --force --all
git push origin --force --tags
```
force-push КАСАЕТСЯ И TAGS. Все существующие локальные клоны и working copy станут stale; наш `diagnostic/instrumentation` содержит незапушенные коммиты — их судьба решается отдельно и ДО push.

## 4. Локальная рабочая копия (после пуша, отдельным шагом)

- В `C:\Users\Abonen0007\Documents\svoboda2`: убедиться, что untracked-файлы в безопасности (они не под filter-repo; tracked дерево чистое → команда применима с --force).
- `git filter-repo --invert-paths` теми же путями (runbook step 1) в рабочей копии, проверить `git status` (untracked должны остаться), затем `git fetch origin --prune` + сверить, что refs совпадают.
- Локально-незапушенная `fix-fork-rc2` / `archive/rc2-failed-54ac856` содержат 3 ключа — после этого шага они должны показывать те же нули фингерпринтов.

## 5. GitHub Support (запрос шлёт пользователь от имени владельца)

Текст запроса (англ.): repo id `1459577517` (owner id `59847466`), first commit `8c517ec` (2026-11-15/16). Инцидент: опубликованы секретные ассеты в default branch + тегах + Release assets; перечислить пути. Запросить: purge cached views и blobs всех секретных ассетов, удаление stale refs/PR references, гарантию недоступности старых коммитов по SHA, и подтверждение по email.

## 6. Releases (после п.3–5)

Удалить все релизы v2.0.0…v2.0.6 (id: 446881032, 446867538, 446716145, 446666335, 446649528, 446643812, v2.0.0 — перечислить через API; в каждом по 4 APK, итого ~24 файла, ~210 МБ, суммарно ~30 загрузок) — либо, по решению пользователя, загрузить пересобранные APK и затем удалить старые. `svoboda2-latest-universal.apk` дублирует `*-universal.apk` по digest — учитывать при удалении.

## 7. Профилактика

- Включить **Push Protection / Secret scanning** (Settings → Advanced Security).
- Gitleaks в CI: `.github/workflows/gitleaks.yml` (scan на каждый push, блок при находке).
- APK build gate: проверка отсутствия `assets/**/warp_identity.json`, `*seeds*.json`, `public_servers/` с ключами в release APK (распаковать и грепнуть перед загрузкой).
- Процедура: новые WARP-регистрации только через ещё не переиспользуемую identity; старый 74-seed подход считать выведенным из эксплуатации.

## 8. Интерпретация "готово"

Этап 3 завершён только когда: (а) фингерпринты пусты везде; (б) GitHub Support письмом подтвердил очистку; (в) старые APK удалены/заменены; (г) unauth доступ к старым asset-URL отдаёт 404; (д) Push Protection + Gitleaks активны; (е) пользователь подтвердил, что старые credentials больше нигде не работают (ротация серверного peer пройдена, CF API — по возможности).
