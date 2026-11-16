$prompt = @'
Review this design brief for an Android no-root VPN client ("Svoboda 2.0", Russian market, DPI bypass focus). Be critical and concrete. Answer in Russian, structured as numbered findings with severity (Critical/High/Medium/Low), then answer the 5 review questions, then a final verdict: Ship A / Ship A then B / Needs rethink.
'@

$out = 'C:\Users\Abonen0007\Documents\svoboda2\docs\gpt_auto_design_review.txt'
$env:CHATGPT_NO_REASONING = '1'
Remove-Item $out -ErrorAction SilentlyContinue
& python C:\lib\chatgpt-ai.py chat --session svoboda2 --file 'C:\Users\Abonen0007\Documents\svoboda2\docs\AUTO_MULTI_PROFILE_DESIGN.md' --timeout 900 $prompt *>&1 | Out-File -Encoding utf8 $out
Write-Host "rc=$LASTEXITCODE"
Get-Content $out
