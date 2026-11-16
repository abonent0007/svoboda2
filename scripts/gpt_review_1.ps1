$prompt = @"
Ты — senior Android-инженер и security-ревьюер «Свободы 2.0» (no-root Android VPN client, Kotlin + Compose + GoBackend).
Ниже diff недавних изменений: RussianDirectDialog, HomeScreen (подменю методов + Российские приложения — напрямую), RussianDirectCatalog, Prefs, WarpGenerator, build.gradle.

Проверь: корректность (Compose state handling, coroutine scopes, диалоги), безопасность (ключи, Keystore, инъекция конфига), регрессии, архитектурное соответствие.

Ответ: нумерованный список findings — severity (Critical/High/Medium/Low), файл, контекст, причина, предложение.
В конце — один вердикт: Ship it / Ship with minor fixes / Needs changes / Needs rethinking.
Будь точен, не выдумывай проблемы.
"@
python C:\lib\chatgpt-ai.py chat --session svoboda2 --file 'C:\Users\Abonen0007\Documents\svoboda2\docs\review_diff.patch' --timeout 600 $prompt 2>&1 | Out-File -Encoding utf8 'C:\Users\Abonen0007\Documents\svoboda2\docs\gpt_review_1.txt'
Write-Output "DONE rc=$LASTEXITCODE"
Get-Content 'C:\Users\Abonen0007\Documents\svoboda2\docs\gpt_review_1.txt' | Select-Object -First 5
