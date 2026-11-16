$root = 'C:\Users\Abonen0007\Documents\svoboda2\third_party'
$changed = 0

for ($pass = 1; $pass -le 3; $pass++) {
    $passChanged = 0
    $files = Get-ChildItem -Path $root -Recurse -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Length -gt 0 -and $_.Length -lt 512 }
    foreach ($f in $files) {
        $text = [System.IO.File]::ReadAllText($f.FullName, [System.Text.Encoding]::UTF8).Trim()
        if ($text -notmatch '^(\.{1,2}/)?[A-Za-z0-9_.\-/]+$') { continue }
        if ($text -match '\s') { continue }
        if ($text -notmatch '/') { continue }
        $target = Join-Path $f.DirectoryName $text
        if (-not (Test-Path -LiteralPath $target)) { continue }
        if ((Get-Item -LiteralPath $target).PSIsContainer) { continue }
        $src = Get-Content -LiteralPath $target -Raw
        Set-Content -LiteralPath $f.FullName -Value $src -NoNewline
        Write-Output ("fixed: " + $f.FullName.Replace($root, '') + "  <- " + $text)
        $passChanged++
        $changed++
    }
    if ($passChanged -eq 0) { break }
}

Write-Output "TOTAL FIXED: $changed"
