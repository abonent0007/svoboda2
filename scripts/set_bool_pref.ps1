<# Add/replace a boolean pref in svoboda2_prefs.xml WITHOUT losing anything else. #>
param(
    [Parameter(Mandatory = $true)][string]$Key,
    [Parameter(Mandatory = $true)][ValidateSet('true', 'false')][string]$Value
)

$adb = 'C:\Users\Abonen0007\Android\Sdk\platform-tools\adb.exe'
$pkg = 'svoboda.runet.abonent0007'
$tmp = "$env:TEMP\svoboda2_prefs_work.xml"
$xml = "$env:TEMP\svoboda2_prefs_edit.xml"
$backup = "$env:TEMP\svoboda2_prefs_BACKUP_mapdns.xml"

Write-Host "== pull prefs (cmd redirect keeps raw bytes) =="
& $adb -s 97db202c shell am force-stop $pkg | Out-Null
cmd /c "$adb -s 97db202c shell run-as $pkg cat shared_prefs/svoboda2_prefs.xml > `"$tmp`""
if ($LASTEXITCODE -ne 0 -or (Get-Item $tmp).Length -lt 100) { throw "pull failed" }
Copy-Item $tmp $backup -Force

Write-Host "== set $Key = $Value =="
python "$PSScriptRoot\set_bool_pref.py" $tmp $Key $Value $xml
if ($LASTEXITCODE -ne 0) { throw "edit failed" }

Write-Host "== push back (base64) =="
$b64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($xml))
& $adb -s 97db202c shell "run-as $pkg sh -c 'echo $b64 | base64 -d > /data/user/0/$pkg/shared_prefs/svoboda2_prefs.xml.tmp && mv /data/user/0/$pkg/shared_prefs/svoboda2_prefs.xml.tmp /data/user/0/$pkg/shared_prefs/svoboda2_prefs.xml'"
if ($LASTEXITCODE -ne 0) { throw "push failed" }

Write-Host "== verify =="
& $adb -s 97db202c shell "run-as $pkg cat shared_prefs/svoboda2_prefs.xml" | Select-String -Pattern $Key

Write-Host "== relaunch =="
& $adb -s 97db202c shell am start -n svoboda.runet.abonent0007/.MainActivity
