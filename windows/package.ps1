# 组装 Windows 免安装分发包：dist/LanMouse-Windows.zip
# 用法（仓库根目录或任意位置都可以）：
#   powershell -ExecutionPolicy Bypass -File windows\package.ps1
$ErrorActionPreference = 'Stop'

$win   = $PSScriptRoot
$root  = Split-Path -Parent $win
$dist  = Join-Path $root 'dist'
$stage = Join-Path ([IO.Path]::GetTempPath()) 'LanMouse-Windows'

# 1. exe 不存在时先编译
if (-not (Test-Path -LiteralPath (Join-Path $win 'LanMouseServer.exe'))) {
    & (Join-Path $win 'build.ps1')
}

# 2. 组装暂存目录
if (Test-Path -LiteralPath $stage) { Remove-Item -LiteralPath $stage -Recurse -Force }
New-Item -ItemType Directory -Force -Path $stage, (Join-Path $stage 'src'), (Join-Path $stage 'android-apk') | Out-Null

Copy-Item -LiteralPath (Join-Path $win 'LanMouseServer.exe'), (Join-Path $win 'start-server.bat'),
                            (Join-Path $win 'allow-firewall.bat'), (Join-Path $win 'allow-firewall.ps1'),
                            (Join-Path $win 'run.ps1'), (Join-Path $win 'build.ps1'),
                            (Join-Path $win 'setup-firewall.ps1') -Destination $stage
Copy-Item -LiteralPath (Join-Path $win 'src\LanMouseServer.cs') -Destination (Join-Path $stage 'src')
Copy-Item -LiteralPath (Join-Path $win '使用说明-分发包.txt') -Destination (Join-Path $stage '使用说明-先看这个.txt')
Copy-Item -LiteralPath (Join-Path $dist 'LanMouse-debug.apk') -Destination (Join-Path $stage 'android-apk')

# 3. 压缩并写入校验文件
New-Item -ItemType Directory -Force -Path $dist | Out-Null
$zip = Join-Path $dist 'LanMouse-Windows.zip'
if (Test-Path -LiteralPath $zip) { Remove-Item -LiteralPath $zip -Force }

Compress-Archive -Path $stage -DestinationPath $zip -Force
$hash = (Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash.ToLowerInvariant()
[IO.File]::WriteAllText("$zip.sha256", "$hash  LanMouse-Windows.zip`r`n", (New-Object Text.UTF8Encoding($false)))
Remove-Item -LiteralPath $stage -Recurse -Force

Write-Host "分发包: $zip"
Write-Host "SHA-256: $hash"
Write-Host "请同步更新 README.md 中“分发包校验”一节的哈希。"
