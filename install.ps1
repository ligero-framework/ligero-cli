# Ligero CLI installer for Windows (PowerShell).
#   irm https://github.com/ligero-framework/ligero-cli/releases/latest/download/install.ps1 | iex
# Downloads the native ligero.exe (no JVM required), installs it to
# %USERPROFILE%\.ligero\bin, and adds that directory to your user PATH.
$ErrorActionPreference = 'Stop'

$repo    = 'ligero-framework/ligero-cli'
$version = if ($env:LIGERO_VERSION) { $env:LIGERO_VERSION } else { 'latest' }
$dir     = Join-Path $env:USERPROFILE '.ligero\bin'
$asset   = 'ligero-windows-x64.exe'

if ($version -eq 'latest') {
    $url = "https://github.com/$repo/releases/latest/download/$asset"
} else {
    $url = "https://github.com/$repo/releases/download/$version/$asset"
}

Write-Host "Installing Ligero CLI ($asset)…"
New-Item -ItemType Directory -Force -Path $dir | Out-Null
Invoke-WebRequest -Uri $url -OutFile (Join-Path $dir 'ligero.exe')

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
