param([Parameter(Mandatory=$true)][ValidatePattern('^BDP-\d{2}$')][string]$Strategy)

$adb = 'C:\Users\Abonen0007\Android\Sdk\platform-tools\adb.exe'
$pkg = 'svoboda.runet.abonent0007'
$tmp = "$env:TEMP\svoboda2_prefs.xml"
$xml = "$env:TEMP\svoboda2_prefs_edit.xml"
$backup = "$env:TEMP\svoboda2_prefs_BACKUP.xml"

Write-Host "== pull prefs =="
& $adb -s 97db202c shell am force-stop $pkg | Out-Null
# PowerShell '>' re-encodes adb stdout to UTF-16 - use cmd redirection to keep raw bytes.
cmd /c "$adb -s 97db202c shell run-as $pkg cat shared_prefs/svoboda2_prefs.xml > `"$tmp`""
if ($LASTEXITCODE -ne 0 -or (Get-Item $tmp).Length -lt 100) { throw "pull failed" }
Copy-Item $tmp $backup -Force
Write-Host "backup -> $backup ($((Get-Item $backup).Length) bytes)"

Write-Host "== set dpi_strategy_id = $Strategy =="
python "$PSScriptRoot\edit_dpi_strategy.py" $tmp $Strategy $xml
if ($LASTEXITCODE -ne 0) { throw "edit failed" }

Write-Host "== push back (base64 over adb, no redirects for cmd) =="
$b64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($xml))
& $adb -s 97db202c shell "run-as $pkg sh -c 'echo $b64 | base64 -d > /data/user/0/$pkg/shared_prefs/svoboda2_prefs.xml.tmp && mv /data/user/0/$pkg/shared_prefs/svoboda2_prefs.xml.tmp /data/user/0/$pkg/shared_prefs/svoboda2_prefs.xml'"
if ($LASTEXITCODE -ne 0) { throw "push failed" }
Write-Host "== verify =="
& $adb -s 97db202c shell "run-as $pkg cat shared_prefs/svoboda2_prefs.xml" | Select-String -Pattern "dpi_strategy_id"

Write-Host "== relaunch =="
& $adb -s 97db202c shell am start -n svoboda.runet.abonent0007/.MainActivity
