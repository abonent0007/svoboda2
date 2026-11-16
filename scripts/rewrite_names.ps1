$ErrorActionPreference = 'Stop'
$g = 'C:\Users\Abonen0007\dev\git\cmd\git.exe'
Set-Location 'C:\Users\Abonen0007\Documents\svoboda2'
$filter = 'export GIT_AUTHOR_NAME=abonent0007; export GIT_COMMITTER_NAME=abonent0007; export GIT_AUTHOR_EMAIL=59847466+abonent0007@users.noreply.github.com; export GIT_COMMITTER_EMAIL=59847466+abonent0007@users.noreply.github.com'
$env:FILTER_BRANCH_SQUELCH_WARNING = '1'
& $g filter-branch --env-filter $filter --tag-name-filter cat -- --all 2>&1 | Select-Object -Last 6
Write-Output "rc=$LASTEXITCODE"
