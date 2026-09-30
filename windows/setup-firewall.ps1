param(
    [int]$Port = 8765
)

$ErrorActionPreference = "Stop"
$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
$principal = New-Object Security.Principal.WindowsPrincipal($identity)
$isAdmin = $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)

if (-not $isAdmin) {
    throw "请右键以管理员身份运行 PowerShell，再执行本脚本。"
}

$name = "LanMouse UDP $Port"
$existing = Get-NetFirewallRule -DisplayName $name -ErrorAction SilentlyContinue
if ($existing) {
    Remove-NetFirewallRule -DisplayName $name
}

New-NetFirewallRule `
    -DisplayName $name `
    -Direction Inbound `
    -Action Allow `
    -Protocol UDP `
    -LocalPort $Port `
    -Profile Private | Out-Null

Write-Host "已允许 专用网络 的入站 UDP $Port。"