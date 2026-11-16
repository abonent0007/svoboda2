$prompt = @'
You are a senior Android/NDK reviewer. I attach a plan for integrating ByeDPI (ciadpi) + HEV tun2socks into a Russian no-root Android VPN client (Svoboda 2.0).

Review the architecture in this document critically:
1. Is the GoBackend handoff approach sound? (single TUN fd passed to HEV instead of wgTurnOn)
2. Is a separate ciadpi process the right choice vs in-process? Risks?
3. Any fatal flaws: file descriptors lifecycle, process zombies, FGS, split-tunnel injection, zero-peer AWG config in establish()?
4. What is missing before implementation?
5. Rate severity of the 4 open questions.

Answer with a numbered findings list + severity (Critical/High/Medium/Low) per finding, then verdict: Ship / Ship with fixes / Needs rethinking.
'@

$out = 'C:\Users\Abonen0007\Documents\svoboda2\docs\gpt_arch_review.txt'
$env:CHATGPT_NO_REASONING = '1'
Remove-Item $out -ErrorAction SilentlyContinue
& python C:\lib\chatgpt-ai.py chat --session svoboda2 --file 'C:\Users\Abonen0007\Documents\svoboda2\docs\DPI_13_14_ARCHITECTURE.md' --timeout 600 $prompt *>&1 | Out-File -Encoding utf8 $out
Write-Output "rc=$LASTEXITCODE"
Get-Content $out -TotalCount 60
