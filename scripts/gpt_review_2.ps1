$ErrorActionPreference = 'Continue'
$out = 'C:\Users\Abonen0007\Documents\svoboda2\docs\gpt_review_2.txt'
Remove-Item $out -ErrorAction SilentlyContinue
& python C:\lib\chatgpt-ai.py chat --session svoboda2 --file 'C:\Users\Abonen0007\Documents\svoboda2\docs\review_brief_full.md' --timeout 600 'Perform the code review per the instructions in the attached brief.md. Do NOT change any code; review only.' *>&1 | Out-File -Encoding utf8 $out
Write-Output "rc=$LASTEXITCODE"
Get-Content $out -TotalCount 10
