# Fetch pinned, public native dependencies for Svoboda 2.0 without git.
# Place this file at: <svoboda2>\scripts\fetch_pinned_dpi_sources.ps1
# Run from PowerShell: powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\fetch_pinned_dpi_sources.ps1

$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$ThirdPartyRoot = Join-Path $ProjectRoot 'third_party'
# Short work root is required: nested archives (hev-task-system-src-arch-microblaze-*.s)
# exceed the legacy 260-character MAX_PATH when extracted under %TEMP%.
$WorkRoot = Join-Path $env:SystemDrive ("sdw-" + [Guid]::NewGuid().ToString('N').Substring(0, 8))
New-Item -ItemType Directory -Path $WorkRoot -Force | Out-Null
New-Item -ItemType Directory -Path $ThirdPartyRoot -Force | Out-Null

$Sources = @(
    [pscustomobject]@{ Repo='hufrea/byedpi'; Sha='ba532298de7b28cfe854aea83d061369d13ca290'; Path='byedpi' },
    [pscustomobject]@{ Repo='heiher/hev-socks5-tunnel'; Sha='4d6c334dbfb68a79d1970c2744e62d09f71df12f'; Path='hev-socks5-tunnel' },
    [pscustomobject]@{ Repo='heiher/hev-socks5-core'; Sha='4be2e621813ba0315cfacd995bf501bde91d6996'; Path='hev-socks5-tunnel/src/core' },
    [pscustomobject]@{ Repo='heiher/hev-task-system'; Sha='8d83bbbf79557138726c8ee5a5fae99cbb978d61'; Path='hev-socks5-tunnel/third-part/hev-task-system' },
    [pscustomobject]@{ Repo='heiher/lwip'; Sha='07dbf162c718cc78ddedb9e67c6ebd17065eaf13'; Path='hev-socks5-tunnel/third-part/lwip' },
    [pscustomobject]@{ Repo='heiher/yaml'; Sha='efa36117a8646d26d12b58e05bac472d7854a70d'; Path='hev-socks5-tunnel/third-part/yaml' }
)

function Invoke-GitHubApi {
    param([Parameter(Mandatory=$true)][string]$Uri)
    $headers = @{ 'User-Agent' = 'Svoboda2-DPI-source-bootstrap'; 'Accept' = 'application/vnd.github+json' }
    for ($attempt = 1; $attempt -le 3; $attempt++) {
        try {
            return Invoke-RestMethod -Uri $Uri -Headers $headers -Method Get
        } catch {
            if ($attempt -eq 3) { throw }
            Start-Sleep -Seconds (2 * $attempt)
        }
    }
}

function Add-PinnedArchive {
    param(
        [Parameter(Mandatory=$true)][string]$Repo,
        [Parameter(Mandatory=$true)][string]$Sha,
        [Parameter(Mandatory=$true)][string]$RelativePath
    )

    $dest = Join-Path $ThirdPartyRoot $RelativePath
    if (Test-Path -LiteralPath $dest) {
        $existing = @(Get-ChildItem -LiteralPath $dest -Force -ErrorAction SilentlyContinue)
        if ($existing.Count -gt 0) {
            # Already populated by a previous (possibly partial) run: keep it, record it.
            Write-Host "Skipping ${Repo} - destination already populated: $dest" -ForegroundColor Yellow
            $allFiles = @(Get-ChildItem -LiteralPath $dest -File -Recurse -Force -ErrorAction SilentlyContinue)
            $licenseFiles = @($allFiles | Where-Object { $_.Name -match '^(?i:license|licence|copying|notice|copyright)(\..*)?$' } | ForEach-Object { $_.FullName.Substring($dest.Length + 1) })
            return [pscustomobject]@{
                repository = $Repo
                commit = $Sha
                destination = (Resolve-Path -LiteralPath $dest).Path
                archiveUrl = "https://github.com/$Repo/archive/$Sha.zip"
                archiveSha256 = $null
                extractedFileCount = $allFiles.Count
                licenseFiles = $licenseFiles
                acquisitionStatus = 'skipped-existing'
            }
        }
    } else {
        New-Item -ItemType Directory -Path $dest -Force | Out-Null
    }

    # Verify that GitHub resolves this exact commit before downloading its archive.
    $commit = Invoke-GitHubApi -Uri "https://api.github.com/repos/$Repo/commits/$Sha"
    if ($commit.sha -ne $Sha) {
        throw "Commit mismatch for $Repo. Expected $Sha, got $($commit.sha)"
    }

    $zipPath = Join-Path $WorkRoot (($Repo -replace '/', '_') + '-' + $Sha + '.zip')
    $extractPath = Join-Path $WorkRoot (($Repo -replace '/', '_') + '-' + $Sha)
    $archiveUrl = "https://github.com/$Repo/archive/$Sha.zip"

    Write-Host "Downloading $Repo @ $Sha" -ForegroundColor Cyan
    $web = New-Object System.Net.WebClient
    $web.Headers.Add('User-Agent', 'Svoboda2-DPI-source-bootstrap')
    try {
        $web.DownloadFile($archiveUrl, $zipPath)
    } finally {
        $web.Dispose()
    }

    $archiveHash = (Get-FileHash -LiteralPath $zipPath -Algorithm SHA256).Hash.ToLowerInvariant()
    Expand-Archive -LiteralPath $zipPath -DestinationPath $extractPath -Force
    $roots = @(Get-ChildItem -LiteralPath $extractPath -Directory -Force)
    if ($roots.Count -ne 1) {
        throw "Unexpected archive layout for ${Repo}: expected one root directory, found $($roots.Count)"
    }

    foreach ($item in Get-ChildItem -LiteralPath $roots[0].FullName -Force) {
        $target = Join-Path $dest $item.Name
        Copy-Item -LiteralPath $item.FullName -Destination $target -Recurse -Force
    }

    $allFiles = @(Get-ChildItem -LiteralPath $dest -File -Recurse -Force)
    $fileCount = $allFiles.Count
    if ($fileCount -eq 0) { throw "No files extracted for $Repo" }
    $licenseFiles = @($allFiles | Where-Object { $_.Name -match '^(?i:license|licence|copying|notice|copyright)(\..*)?$' } | ForEach-Object { $_.FullName.Substring($dest.Length + 1) })

    return [pscustomobject]@{
        repository = $Repo
        commit = $Sha
        destination = (Resolve-Path -LiteralPath $dest).Path
        archiveUrl = $archiveUrl
        archiveSha256 = $archiveHash
        extractedFileCount = $fileCount
        licenseFiles = $licenseFiles
        acquisitionStatus = 'downloaded'
    }
}

try {
    # Verify that the four nested dependency pins match the gitlinks recorded
    # by the pinned HEV parent commit. This replaces `git submodule status`.
    $hevTree = Invoke-GitHubApi -Uri 'https://api.github.com/repos/heiher/hev-socks5-tunnel/git/trees/4d6c334dbfb68a79d1970c2744e62d09f71df12f?recursive=1'
    $actualGitlinks = @{}
    foreach ($entry in $hevTree.tree) {
        if ($entry.mode -eq '160000') { $actualGitlinks[$entry.path] = $entry.sha }
    }
    $expectedGitlinks = @{
        'src/core' = '4be2e621813ba0315cfacd995bf501bde91d6996'
        'third-part/hev-task-system' = '8d83bbbf79557138726c8ee5a5fae99cbb978d61'
        'third-part/lwip' = '07dbf162c718cc78ddedb9e67c6ebd17065eaf13'
        'third-part/yaml' = 'efa36117a8646d26d12b58e05bac472d7854a70d'
    }
    foreach ($path in $expectedGitlinks.Keys) {
        if (-not $actualGitlinks.ContainsKey($path) -or $actualGitlinks[$path] -ne $expectedGitlinks[$path]) {
            throw "HEV gitlink mismatch at $path. Expected $($expectedGitlinks[$path]), got $($actualGitlinks[$path])"
        }
    }
    if ($actualGitlinks.Count -ne $expectedGitlinks.Count) {
        throw "Unexpected number of HEV gitlinks. Expected 4, got $($actualGitlinks.Count)"
    }

    $manifest = @()
    foreach ($source in $Sources) {
        $manifest += Add-PinnedArchive -Repo $source.Repo -Sha $source.Sha -RelativePath $source.Path
    }

    # Minimal structural checks required before starting the NDK build.
    $requiredFiles = @(
        'byedpi/main.c',
        'byedpi/params.h',
        'hev-socks5-tunnel/Android.mk',
        'hev-socks5-tunnel/Application.mk',
        'hev-socks5-tunnel/build.mk',
        'hev-socks5-tunnel/src/core/include'
    )
    foreach ($relative in $requiredFiles) {
        $path = Join-Path $ThirdPartyRoot $relative
        if (-not (Test-Path -LiteralPath $path)) { throw "Required source path missing: $path" }
    }

    $lock = [pscustomobject]@{
        generatedUtc = [DateTime]::UtcNow.ToString('o')
        note = 'Source snapshots are from immutable commit SHA archives. Git metadata is not included.'
        sources = $manifest
    }
    $lockPath = Join-Path $ThirdPartyRoot 'DPI_SOURCES.lock.json'
    $lock | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $lockPath -Encoding UTF8

    Write-Host "`nSUCCESS: pinned sources extracted to $ThirdPartyRoot" -ForegroundColor Green
    Write-Host "Lock manifest: $lockPath"
    $manifest | Format-Table repository, commit, destination, extractedFileCount, archiveSha256 -AutoSize
    Write-Host "`nNext: inspect license files and run native build preparation. This script does not build or integrate the VPN."
} finally {
    Remove-Item -LiteralPath $WorkRoot -Recurse -Force -ErrorAction SilentlyContinue
}
