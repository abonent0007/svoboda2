$ErrorActionPreference = 'Stop'
$g = 'C:\Users\Abonen0007\dev\git\cmd\git.exe'
Set-Location 'C:\Users\Abonen0007\Documents\svoboda2'

# Backup refs
& $g show-ref | Out-File -Encoding utf8 "C:\Users\Abonen0007\AppData\Local\Temp\opencode\svoboda2_refs_backup.txt"
& $g branch -a > "C:\Users\Abonen0007\AppData\Local\Temp\opencode\svoboda2_branches_backup.txt"
Write-Output 'backup written'

# Rewrite all authors to abonent0007 noreply
$envFilter = @'
if [ "$GIT_AUTHOR_NAME" != "abonent0007" ]; then export GIT_AUTHOR_NAME="abonent0007"; fi
if [ "$GIT_COMMITTER_NAME" != "abonent0007" ]; then export GIT_COMMITTER_NAME="abonent0007"; fi
export GIT_AUTHOR_EMAIL="59847466+abonent0007@users.noreply.github.com"
export GIT_COMMITTER_EMAIL="59847466+abonent0007@users.noreply.github.com"
'@

$env:FILTER_BRANCH_SQUELCH_WARNING = '1'
& $g filter-branch --env-filter $envFilter --tag-name-filter cat -- --all 2>&1 | Select-Object -Last 5
Write-Output "filter-branch rc=$LASTEXITCODE"
