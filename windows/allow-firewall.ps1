# 一键允许 LanMouse 服务端通过 Windows 防火墙。
# 未以管理员身份运行时，会自动弹出授权窗口（点“是”）。
param(
    [int]$Port = 8765
)

$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
$isAdmin = (New-Object Security.Principal.WindowsPrincipal($identity)).IsInRole(
    [Security.Principal.WindowsBuiltInRole]::Administrator)

if (-not $isAdmin) {
    Write-Host "需要管理员权限，正在弹出授权窗口，请在弹窗里点“是”..."
    Start-Process -FilePath 'powershell.exe' -Verb RunAs -ArgumentList `
        "-NoProfile -ExecutionPolicy Bypass -File `"$PSCommandPath`" -Port $Port"
    exit
}

$ErrorActionPreference = 'Stop'
$name = "LanMouse UDP $Port"

if (Get-NetFirewallRule -DisplayName $name -ErrorAction SilentlyContinue) {
    Remove-NetFirewallRule -DisplayName $name
}

New-NetFirewallRule `
    -DisplayName $name `
    -Direction Inbound `
    -Action Allow `
    -Protocol UDP `
    -LocalPort $Port `
    -Profile Private, Domain | Out-Null

Write-Host ""
Write-Host "完成：已允许 专用网络 的入站 UDP $Port。"

# 当前网络若是“公用网络”，Windows 仍会拦截入站连接，这里提示并询问是否临时放行。
$publicProfiles = Get-NetConnectionProfile -ErrorAction SilentlyContinue |
    Where-Object { $_.NetworkCategory -eq 'Public' }

if ($publicProfiles) {
    Write-Host ""
    Write-Host "注意：下面这些网络被 Windows 归类为“公用网络”，默认会拦截入站连接："
    foreach ($profile in $publicProfiles) {
        Write-Host ("  - " + $profile.Name + "  (" + $profile.InterfaceAlias + ")")
    }
    Write-Host "推荐做法：设置 → 网络和 Internet → 点当前网络 → 改成“专用网络”，然后重试。"
    Write-Host ""
    $answer = Read-Host "不想改网络类型的话，也可以现在临时放行公用网络 (y = 放行 / 直接回车 = 不放行)"
    if ($answer -eq 'y' -or $answer -eq 'Y') {
        New-NetFirewallRule `
            -DisplayName "$name (Public)" `
            -Direction Inbound `
            -Action Allow `
            -Protocol UDP `
            -LocalPort $Port `
            -Profile Public | Out-Null
        Write-Host "已放行公用网络。提醒：只在可信局域网使用，公共 Wi-Fi 下别人也可能尝试连接。"
    }
}

Write-Host ""
Write-Host "现在可以双击 start-server.bat 启动服务端了。"
Read-Host "按回车键关闭这个窗口"
