<#
  Полный прогон бенчмарка DPI: запуск через UI + сбор результатов из SQLite.
  Требует, чтобы телефон был свободен (сеть не должна быть занята другим трафиком).
#>
param(
    [int]$TimeoutMinutes = 180
)

$adb = 'C:\Users\Abonen0007\Android\Sdk\platform-tools\adb.exe'
$pkg = 'svoboda.runet.abonent0007'
$repo = 'C:\Users\Abonen0007\Documents\svoboda2'

function Tap($x, $y) { & $adb -s 97db202c shell input tap $x $y | Out-Null }
function Swipe($x1, $y1, $x2, $y2) { & $adb -s 97db202c shell input swipe $x1 $y1 $x2 $y2 350 | Out-Null }

Write-Host "== перезапуск приложения =="
& $adb -s 97db202c shell am force-stop $pkg | Out-Null
& $adb -s 97db202c logcat -c | Out-Null
& $adb -s 97db202c shell am start -n "$pkg/.MainActivity" | Out-Null
Start-Sleep -Seconds 18

Write-Host "== открыть настройки DPI =="
Tap 60 185; Start-Sleep -Seconds 2
Swipe 540 1700 540 500; Start-Sleep -Seconds 1
Tap 335 1185; Start-Sleep -Seconds 3

Write-Host "== открыть бенчмарк =="
Swipe 540 1700 540 1200; Start-Sleep -Seconds 1
Tap 390 1259; Start-Sleep -Seconds 3

Write-Host "== прокрутка к кнопке старта =="
for ($i = 0; $i -lt 14; $i++) { Swipe 540 1800 540 400; Start-Sleep -Milliseconds 250 }
& $adb -s 97db202c shell uiautomator dump /sdcard/ui_bench_start.xml | Out-Null
& $adb -s 97db202c pull /sdcard/ui_bench_start.xml "$repo\ui_bench_start.xml" | Out-Null
$nodes = powershell -NoProfile -ExecutionPolicy Bypass -File "$repo\scripts\ui_nodes.ps1" -File "$repo\ui_bench_start.xml"
$start = $nodes | Select-String "НАЧАТЬ ТЕСТИРОВАНИЕ" | Select-Object -First 1
if (-not $start) {
    Write-Host "Кнопка старта не найдена. Текущий экран:"
    $nodes | Select-Object -Last 12
    exit 1
}
Write-Host "Кнопка: $start"

Write-Host "== СТАРТ ПРОГОНА =="
Tap 540 2137
Start-Sleep -Seconds 5

$deadline = (Get-Date).AddMinutes($TimeoutMinutes)
$lastCount = 0
while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 60
    $lines = & $adb -s 97db202c logcat -d -v brief -s SvobodaDpiEngine:* 2>$null | Select-String "restartWithStrategy"
    $count = ($lines | Measure-Object).Count
    if ($count -ne $lastCount) {
        Write-Host ("{0}  стратегий перезапущено" -f $count)
        $lastCount = $count
    }
    $done = & $adb -s 97db202c logcat -d -v brief -s SvobodaDpiEngine:* 2>$null | Select-String "restartWithStrategy\(BDP-12\)" | Select-Object -First 1
    if ($count -ge 41) { Write-Host "Все стратегии прогнаны"; break }
}

Write-Host "== сбор базы =="
cmd /c "$adb -s 97db202c shell run-as $pkg cat databases/dpi_benchmark.db > $repo\bench_run.db"
cmd /c "$adb -s 97db202c shell run-as $pkg cat databases/dpi_benchmark.db-wal > $repo\bench_run.db-wal"
cmd /c "$adb -s 97db202c shell run-as $pkg cat databases/dpi_benchmark.db-shm > $repo\bench_run.db-shm"
python "$repo\scripts\inspect_benchmark_db.py" "$repo\bench_run.db"
