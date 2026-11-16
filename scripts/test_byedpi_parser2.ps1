Get-Process byedpi -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
$exe = 'C:\Users\Abonen0007\Documents\svoboda2\third_party\byedpi\dist\host\byedpi.exe'
$dir = 'C:\Users\Abonen0007\Documents\svoboda2\third_party\byedpi\dist'
$out = Join-Path $dir 'bdp00_out.log'
$err = Join-Path $dir 'bdp00_err.log'
Remove-Item $out, $err -ErrorAction SilentlyContinue

$bdp00 = @(
    '-Ku','-a3','-An','-Kt,h','-d1','-s0+s','-d3+s','-s6+s','-d9+s','-s12+s','-s15+s',
    '-s20+s','-d25+s','-s30+s','-s35+s','-An','-p','1080'
)
Write-Output '--- BDP-00 (port 1080) ---'
$p = Start-Process -FilePath $exe -ArgumentList $bdp00 -NoNewWindow -PassThru -RedirectStandardOutput $out -RedirectStandardError $err
Start-Sleep -Seconds 2
if ($p.HasExited) {
    Write-Output "BDP-00 exit=$($p.ExitCode) (rejected)"
    Get-Content $out -ErrorAction SilentlyContinue | Select-Object -First 5
    Get-Content $err -ErrorAction SilentlyContinue | Select-Object -First 5
} else {
    $listening = (Get-NetTCPConnection -LocalPort 1080 -State Listen -ErrorAction SilentlyContinue | Measure-Object).Count
    Write-Output "BDP-00: alive after 2s, listeners on 1080: $listening"
    $p.Kill()
}
Get-Process byedpi -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
