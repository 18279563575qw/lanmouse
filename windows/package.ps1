# 组装 Windows 免安装分发包：dist/LanMouse-Windows.zip
# 用法（仓库根目录或任意位置都可以）：
#   powershell -ExecutionPolicy Bypass -File windows\package.ps1
$ErrorActionPreference = 'Stop'

$win   = $PSScriptRoot
$root  = Split-Path -Parent $win
$dist  = Join-Path $root 'dist'
$stage = Join-Path ([IO.Path]::GetTempPath()) 'LanMouse-Windows'

# 1. 服务端 exe 不存在或源码更新时先编译
$serverExe = Join-Path $win 'LanMouseServer.exe'
$serverSource = Join-Path $win 'src\LanMouseServer.cs'
$serverRunning = Get-Process -Name 'LanMouseServer' -ErrorAction SilentlyContinue
$serverNeedsBuild = (-not (Test-Path -LiteralPath $serverExe) -or
    (Get-Item -LiteralPath $serverSource).LastWriteTimeUtc -gt
    (Get-Item -LiteralPath $serverExe).LastWriteTimeUtc)
if ($serverNeedsBuild -and $serverRunning) {
    Write-Warning 'LanMouseServer 正在运行，无法覆盖 exe；本次分发包沿用当前版本。'
} elseif ($serverNeedsBuild) {
    & (Join-Path $win 'build.ps1')
}

# 2. Android APK 不存在或构建产物更新时同步到 dist。
$distApk = Join-Path $dist 'LanMouse-debug.apk'
$builtApk = Join-Path $root 'android\app\build\outputs\apk\debug\app-debug.apk'
if (-not (Test-Path -LiteralPath $builtApk)) {
    & (Join-Path $root 'android\build-debug.ps1')
}
if (-not (Test-Path -LiteralPath $distApk) -or
    (Get-Item -LiteralPath $builtApk).LastWriteTimeUtc -gt
    (Get-Item -LiteralPath $distApk).LastWriteTimeUtc) {
    New-Item -ItemType Directory -Force -Path $dist | Out-Null
    Copy-Item -LiteralPath $builtApk -Destination $distApk
    $apkHash = (Get-FileHash -LiteralPath $distApk -Algorithm SHA256).Hash.ToLowerInvariant()
    [IO.File]::WriteAllText("$distApk.sha256", "$apkHash  LanMouse-debug.apk`n",
                            (New-Object Text.UTF8Encoding($false)))
}

# 3. 组装暂存目录
if (Test-Path -LiteralPath $stage) { Remove-Item -LiteralPath $stage -Recurse -Force }
New-Item -ItemType Directory -Force -Path $stage, (Join-Path $stage 'src'), (Join-Path $stage 'android-apk') | Out-Null

Copy-Item -LiteralPath (Join-Path $win 'LanMouseServer.exe'), (Join-Path $win 'start-server.bat'),
                            (Join-Path $win 'allow-firewall.bat'), (Join-Path $win 'allow-firewall.ps1'),
                            (Join-Path $win 'run.ps1'), (Join-Path $win 'build.ps1') -Destination $stage
Copy-Item -LiteralPath (Join-Path $win 'src\LanMouseServer.cs') -Destination (Join-Path $stage 'src')
Copy-Item -LiteralPath (Join-Path $win '使用说明-分发包.txt') -Destination (Join-Path $stage '使用说明-先看这个.txt')
Copy-Item -LiteralPath $distApk -Destination (Join-Path $stage 'android-apk')

# 4. 压缩并写入校验文件
New-Item -ItemType Directory -Force -Path $dist | Out-Null
$zip = Join-Path $dist 'LanMouse-Windows.zip'
if (Test-Path -LiteralPath $zip) { Remove-Item -LiteralPath $zip -Force }

Compress-Archive -Path $stage -DestinationPath $zip -Force
$hash = (Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash.ToLowerInvariant()
[IO.File]::WriteAllText("$zip.sha256", "$hash  LanMouse-Windows.zip`n", (New-Object Text.UTF8Encoding($false)))
Remove-Item -LiteralPath $stage -Recurse -Force

Write-Host "分发包: $zip"
Write-Host "SHA-256: $hash"
Write-Host "本地分发包已生成；正式发布时请将其作为 GitHub Release 资产上传。"
