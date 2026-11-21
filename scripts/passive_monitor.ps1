# Passive network monitoring over the current DPI route (2026-11-19).
# Authorized by user: keep DPI, observe 1-2 hours, no changes.
# NEVER toggles VPN method, NEVER runs AUTO/WARP, NEVER registers WARP,
# NEVER touches prefs/DNS/keys/quarantine.
# Method: curl from the device (shell uid 2000 -> ip rule 13000 -> table 1334 = VPN/tun94),
# i.e. real HTTPS through the TUN route. Zero screen/UI interaction; fully passive.

param(
    [double]$Hours = 2,
    [int]$IntervalMinutes = 15,
    [switch]$Once
)

$ErrorActionPreference = 'Continue'
$adb    = 'C:\Users\Abonen0007\Android\Sdk\platform-tools\adb.exe'
$device = '97db202c'
$log    = 'C:\Users\Abonen0007\Documents\svoboda2\docs\DEVICE_RC_2026-11-19_evidence\passive_monitor_2026-11-19.md'

function Write-Log([string]$line) {
    $ts = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'
    Add-Content -Path $log -Value "$ts | $line" -Encoding UTF8
}

function Check-Curl([string]$label, [string]$url) {
    $out = & $adb -s $device shell "curl -sS -o /dev/null -w '%{http_code}|%{time_total}|%{remote_ip}' --connect-timeout 20 --max-time 60 $url" 2>&1 | Out-String
    $m = [regex]::Match($out, '(\d{3})\|([\d.]+)\|([\d.:]*)')
    if (-not $m.Success) {
        $snip = ($out -replace '\s+', ' ').Trim()
        if ($snip.Length -gt 180) { $snip = $snip.Substring(0, 180) }
        Write-Log "$label | UNPARSED ($snip)"
        return
    }
    $code = [int]$m.Groups[1].Value
    $t    = $m.Groups[2].Value
    $ip   = $m.Groups[3].Value
    $err  = ''
    $errMatch = [regex]::Match($out, 'curl:\s*\((\d+)\)\s*(.+)')
    if ($errMatch.Success) { $err = " curl_exit=$($errMatch.Groups[1].Value) err='$($errMatch.Groups[2].Value.Trim())'" }
    $verdict = $null
    if ($code -ge 200 -and $code -lt 400) { $verdict = 'LOADED (2xx/3xx)' }
    elseif ($code -ge 400) { $verdict = 'REACHABLE (server answered HTTP)' }
    else { $verdict = 'FAIL (000: transport/TLS blocked)' }
    Write-Log "$label | http=$code time=${t}s ip=$ip$err | $verdict"
}

function Check-AppLog {
    $appPid = (& $adb -s $device shell 'pidof svoboda.runet.abonent0007').Trim()
    if ($appPid) {
        $lines = & $adb -s $device logcat -d -s 'SvobodaVpn:I' --pid=$appPid
        $interesting = $lines | Select-String -Pattern 'checkTunnel|markDegraded|selected|no working|regist|handshake|Transport' | Select-Object -Last 3
        foreach ($l in $interesting) { Write-Log "applog | $($l.Line.Trim())" }
        if (-not $interesting) { Write-Log 'applog | no recent SvobodaVpn lines' }
    } else {
        Write-Log 'applog | app process not found (VPN service may still hold the tunnel)'
    }
}

if (-not (Test-Path $log)) {
    @"
# Passive DPI monitoring - 2026-11-19

Authorized by user: keep DPI connected, passive observation 1-2 h.
Forbidden (and NOT performed by this script): changing connection_method,
starting AUTO/WARP, new WARP registration, touching DNS/keys/profiles/quarantine.

Method: curl from the device shell (uid 2000 -> ip rule 13000 -> table 1334 = VPN/tun94).
This is real HTTPS through the TUN route. Zero UI interaction.
example.com: baseline LOADED. Any 4xx/5xx from CF endpoints == server reachable.
000 == transport/TLS blocked (matches known WARP-reg outage).
DNS over DPI is slow/flaky; budgets raised to 20s connect / 60s total.
Intermittent FAIL rows are kept as-is (real signal, not hidden).

"@ | Add-Content -Path $log -Encoding UTF8
}

Write-Log "=== monitor started (Hours=$Hours, IntervalMinutes=$IntervalMinutes, Once=$Once) ==="
$deadline  = (Get-Date).AddHours($Hours)
$iteration = 0
do {
    $iteration++
    Write-Log "----- iteration $iteration -----"
    Check-Curl 'example.com (HTTPS via TUN)  ' 'https://example.com/'
    Check-Curl 'api.cloudflareclient.com/reg' 'https://api.cloudflareclient.com/v0a2158/reg'
    Check-Curl 'engage.cloudflareclient.com ' 'https://engage.cloudflareclient.com/'
    Check-AppLog
    if ($Once) { break }
    Start-Sleep -Seconds ($IntervalMinutes * 60)
} while ((Get-Date) -lt $deadline)
Write-Log '=== monitor finished ==='
