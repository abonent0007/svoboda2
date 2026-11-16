# Code Review Brief — «Свобода 2.0» (Russian no-root Android VPN client)

## Контекст проекта
- Приложение: **Свобода 2.0** (`svoboda.runet.abonent0007`), Kotlin + Jetpack Compose + GoBackend (WireGuard/AmneziaWG).
- Цель — no-root VPN на Android 8+ (minSdk 26, targetSdk 35).
- Последняя работа: подменю «Российские приложения — напрямую» (split-tunnel direct для банков/госуслуг/локальных веб-ресурсов) + мелкие исправления.

## Что проверить
1. **Корректность** — Compose state, корутины, области видимости диалогов, сохранение/восстановление состояния.
2. **Безопасность** — обработка ключей, Keystore, инъекция ExcludedApplications, отсутствие утечки приватных данных.
3. **Регрессии** — не сломана ли работа VPN/подключение.
4. **Архитектурное соответствие** — соответствие существующим паттернам (VpnController, AppGraph, SplitTunnelManager).

## Задача
Прочитай diff ниже целиком. Дай **нумерованный список findings**:
- severity: `Critical` / `High` / `Medium` / `Low` / `Nit`
- файл и контекст
- причина
- конкретное предложение по исправлению

Отдельно отметь:
- что сделано хорошо,
- что не проверено (что требует устройства/ручного теста),
- итоговый вердикт: **Ship it** / **Ship with minor fixes** / **Needs changes** / **Needs rethinking**.

Будь конкретен. Не выдумывай проблемы там, где их нет.

## Diff

```diff
DIFF_PLACEHOLDER
```
