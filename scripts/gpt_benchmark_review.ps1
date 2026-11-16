$prompt = @'
Review this report about an Android no-root VPN client. Focus on the 6 questions at the end (benchmark methodology, ranking model, baseline, statistics, Telegram handling, what to persist). Be critical and concrete, answer in Russian: numbered findings with severity, then direct answers to the 6 questions, then a short prioritized action list. Also flag any technical error in the described implementation.
'@

$out = 'C:\Users\Abonen0007\Documents\svoboda2\docs\gpt_benchmark_review.txt'
$env:CHATGPT_NO_REASONING = '1'
Remove-Item $out -ErrorAction SilentlyContinue
& python C:\lib\chatgpt-ai.py chat --session svoboda2 --file 'C:\Users\Abonen0007\Documents\svoboda2\docs\REPORT_benchmark_auto.md' --timeout 900 $prompt *>&1 | Out-File -Encoding utf8 $out
Write-Host "rc=$LASTEXITCODE"
Get-Content $out
