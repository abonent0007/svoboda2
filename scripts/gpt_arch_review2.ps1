$prompt = @'
Quick sanity check of the REVISED DPI integration plan (in the attached file). Questions:
1. Is SOCKS5 CONNECT + HEV-alive an acceptable HTTPS-proof replacement for DPI mode? Which residual risks remain?
2. Own-package exclusion in DPI mode - acceptable tradeoff for ciadpi-as-subprocess? Any hidden side effects?
3. Dummy peer with AllowedIPs 0.0.0.0/0 + skip wgTurnOn - acceptable?
4. nativeStop() ordering (quit -> join -> close FD) - sound?

Answer: per question, verdict + one-line risk note. Final verdict: Ship / Ship with fixes / Needs changes.
'@

$out = 'C:\Users\Abonen0007\Documents\svoboda2\docs\gpt_arch_review2.txt'
$env:CHATGPT_NO_REASONING = '1'
Remove-Item $out -ErrorAction SilentlyContinue
& python C:\lib\chatgpt-ai.py ask --file 'C:\Users\Abonen0007\Documents\svoboda2\docs\DPI_13_14_ARCHITECTURE.md' $prompt 2>&1 | Out-File -Encoding utf8 $out
Write-Output "rc=$LASTEXITCODE"
Get-Content $out
