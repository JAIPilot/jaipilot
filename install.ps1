param(
  [string]$Version = $env:JAIPILOT_VERSION,
  [string]$InstallDir = $(if ($env:JAIPILOT_INSTALL_DIR) { $env:JAIPILOT_INSTALL_DIR } else { Join-Path $env:LOCALAPPDATA "JAIPilot\bin" })
)
$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"
$repository = "JAIPilot/jaipilot"
if (-not [Environment]::Is64BitOperatingSystem -or $env:PROCESSOR_ARCHITECTURE -eq "ARM64") {
  throw "JAIPilot supports x64 Windows."
}
if (-not $Version) {
  $Version = (Invoke-RestMethod "https://api.github.com/repos/$repository/releases/latest").tag_name
}
if (-not $Version.StartsWith("v")) { $Version = "v$Version" }
if ($Version -notmatch '^v[0-9]+\.[0-9]+\.[0-9]+$') { throw "Invalid stable JAIPilot version." }
$asset = "jaipilot-x86_64-pc-windows-msvc.exe"
$compressed = [Version]::Parse($Version.Substring(1)) -ge [Version]"1.2.0"
if ($compressed) { $asset = "$asset.gz" }
$base = "https://github.com/$repository/releases/download/$Version"
New-Item -ItemType Directory -Force -Path $InstallDir | Out-Null
$temporary = Join-Path $InstallDir ([IO.Path]::GetRandomFileName())
New-Item -ItemType Directory -Path $temporary | Out-Null
try {
  $binary = Join-Path $temporary "jaipilot.exe"
  Invoke-WebRequest "$base/$asset" -OutFile $binary
  $checksumFile = Join-Path $temporary "checksum"
  Invoke-WebRequest "$base/$asset.sha256" -OutFile $checksumFile
  $checksum = ((Get-Content -Raw $checksumFile).Trim() -split '\s+')[0]
  if ($checksum -notmatch '^[0-9a-f]{64}$' -or (Get-FileHash $binary -Algorithm SHA256).Hash.ToLowerInvariant() -ne $checksum) {
    throw "Checksum mismatch. Existing installation unchanged."
  }
  if ($compressed) {
    $archive = "$binary.gz"
    Move-Item $binary $archive
    $source = [IO.File]::OpenRead($archive)
    $gzip = [IO.Compression.GZipStream]::new($source, [IO.Compression.CompressionMode]::Decompress)
    $output = [IO.File]::Create($binary)
    try { $gzip.CopyTo($output) } finally { $output.Dispose(); $gzip.Dispose(); $source.Dispose() }
    if ((Get-Item $binary).Length -gt 16777216) { throw "Native executable exceeds size limit." }
  }
  $reportedVersion = & $binary --version
  if ($LASTEXITCODE -ne 0 -or $reportedVersion -ne "JAIPilot CLI $($Version.Substring(1))") {
    throw "Downloaded CLI failed its version check. Existing installation unchanged."
  }
  Move-Item -Force $binary (Join-Path $InstallDir "jaipilot.exe")
} finally {
  Remove-Item -Recurse -Force $temporary
}
$userPath = [Environment]::GetEnvironmentVariable("Path", "User")
if (($userPath -split ';') -notcontains $InstallDir) {
  [Environment]::SetEnvironmentVariable("Path", "$InstallDir;$userPath", "User")
}
if (($env:Path -split ';') -notcontains $InstallDir) { $env:Path = "$InstallDir;$env:Path" }
Write-Host "Installed JAIPilot CLI $Version. Open a new terminal and run: jaipilot auth login"
