# Ligero CLI installer for Windows (PowerShell).
#   irm https://github.com/ligero-framework/ligero-cli/releases/latest/download/install.ps1 | iex
# Downloads the native ligero.exe (no JVM required), verifies its SHA-256
# against the checksum published with the release, installs it to
# %USERPROFILE%\.ligero\bin, and adds that directory to your user PATH.
$ErrorActionPreference = 'Stop'

$repo    = 'ligero-framework/ligero-cli'
$version = if ($env:LIGERO_VERSION) { $env:LIGERO_VERSION } else { 'latest' }
if ($version -notmatch '^[A-Za-z0-9._-]+$') { throw "Invalid LIGERO_VERSION: $version" }
$dir     = Join-Path $env:USERPROFILE '.ligero\bin'
$asset   = 'ligero-windows-x64.exe'

if ($version -eq 'latest') {
    $url = "https://github.com/$repo/releases/latest/download/$asset"
} else {
    $url = "https://github.com/$repo/releases/download/$version/$asset"
}

Write-Host "Installing Ligero CLI ($asset)…"
New-Item -ItemType Directory -Force -Path $dir | Out-Null

# Download to a temporary file and only move it into place once verified, so a
# failed or tampered download never becomes ligero.exe.
$tmp = Join-Path $dir ('.ligero-' + [Guid]::NewGuid().ToString('N') + '.tmp')
try {
    Invoke-WebRequest -Uri $url -OutFile $tmp -UseBasicParsing

    # Integrity: releases publish <asset>.sha256 next to each binary.
    $expected = $null
    try {
        $response = Invoke-WebRequest -Uri "$url.sha256" -UseBasicParsing
        $text = if ($response.Content -is [byte[]]) {
            [Text.Encoding]::ASCII.GetString($response.Content)
        } else { [string]$response.Content }
        $expected = ($text.Trim() -split '\s+')[0].ToLowerInvariant()
    } catch {
        if ($env:LIGERO_REQUIRE_CHECKSUM -eq '1') {
            throw "No checksum published for $asset and LIGERO_REQUIRE_CHECKSUM=1 - aborting."
        }
        Write-Warning "This release publishes no checksum for $asset - installing unverified."
    }
    if ($expected) {
        $actual = (Get-FileHash -Algorithm SHA256 -Path $tmp).Hash.ToLowerInvariant()
        if ($actual -ne $expected) {
            throw "Checksum mismatch for $asset (expected $expected, got $actual). The download is corrupt or has been tampered with; nothing was installed."
        }
        Write-Host "✔ Checksum verified"
    }
    Move-Item -Force -Path $tmp -Destination (Join-Path $dir 'ligero.exe')
} finally {
    if (Test-Path $tmp) { Remove-Item -Force $tmp }
}

# Add to the user PATH (idempotent).
$userPath = [Environment]::GetEnvironmentVariable('Path', 'User')
if (-not ($userPath -split ';' | Where-Object { $_ -eq $dir })) {
    [Environment]::SetEnvironmentVariable('Path', "$userPath;$dir", 'User')
    Write-Host "✔ Added $dir to your user PATH"
}

Write-Host ""
Write-Host "✔ Installed to $dir\ligero.exe"
Write-Host "Open a new terminal, then:"
Write-Host "  ligero version"
Write-Host "  ligero new my-api"
