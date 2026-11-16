$brief = 'C:\Users\Abonen0007\Documents\svoboda2\docs\review_brief.md'
$patch = 'C:\Users\Abonen0007\Documents\svoboda2\docs\review_diff.patch'
$out = 'C:\Users\Abonen0007\Documents\svoboda2\docs\review_brief_full.md'

$content = [System.IO.File]::ReadAllText($brief, [System.Text.Encoding]::UTF8)
$diffText = [System.IO.File]::ReadAllText($patch, [System.Text.Encoding]::UTF8)

$content = $content.Replace('DIFF_PLACEHOLDER', $diffText)

[System.IO.File]::WriteAllText($out, $content, (New-Object System.Text.UTF8Encoding($false)))
Write-Output "written: $out ($((Get-Item $out).Length) bytes, $((Get-Content $out | Measure-Object -Line).Lines) lines)"
