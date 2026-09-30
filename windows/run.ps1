param(
    [string]$Bind = "0.0.0.0",
    [int]$Port = 8765,
    [string]$Token = "lanmouse",
    [switch]$NoAuth
)

$ErrorActionPreference = "Stop"
$exe = Join-Path $PSScriptRoot "LanMouseServer.exe"
if (-not (Test-Path -LiteralPath $exe)) {
    & (Join-Path $PSScriptRoot "build.ps1")
}

$arguments = @("--bind", $Bind, "--port", $Port.ToString())
if ($NoAuth) {
    $arguments += "--no-auth"
} else {
    $arguments += @("--token", $Token)
}

& $exe @arguments