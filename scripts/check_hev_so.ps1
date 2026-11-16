$readelf = 'C:\Users\Abonen0007\Android\Sdk\ndk\27.2.12479018\toolchains\llvm\prebuilt\windows-x86_64\bin\llvm-readelf.exe'
$libs = @(
    'C:\Users\Abonen0007\Documents\svoboda2\third_party\hev-socks5-tunnel\libs\arm64-v8a\libhev-socks5-tunnel.so',
    'C:\Users\Abonen0007\Documents\svoboda2\third_party\hev-socks5-tunnel\libs\x86_64\libhev-socks5-tunnel.so'
)
foreach ($lib in $libs) {
    Write-Output "=== $lib ==="
    if (-not (Test-Path $lib)) { Write-Output 'MISSING'; continue }
    Write-Output ("size: {0:N0} bytes" -f (Get-Item $lib).Length)
    & $readelf -h $lib | Select-String -Pattern 'Machine|Class|Type'
    Write-Output '-- LOAD alignment --'
    & $readelf -l $lib | Select-String -Pattern 'LOAD' | Select-Object -First 3
    Write-Output '-- NEEDED --'
    & $readelf -d $lib | Select-String -Pattern 'NEEDED'
    Write-Output '-- exports (hev_socks5_tunnel) --'
    & $readelf --dyn-syms $lib | Select-String -Pattern 'hev_socks5_tunnel_(main_from_str|quit)' | Select-Object -First 4
    Write-Output ''
}
