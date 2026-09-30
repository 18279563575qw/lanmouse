$ErrorActionPreference = "Stop"
$projectRoot = $PSScriptRoot
$workspaceRoot = [IO.Path]::GetFullPath((Join-Path $projectRoot "..\.."))

$sdkCandidates = @(
    (Join-Path $workspaceRoot "Android\Sdk"),
    $env:ANDROID_HOME,
    $env:ANDROID_SDK_ROOT,
    (Join-Path $env:LOCALAPPDATA "Android\Sdk")
) | Where-Object { $_ -and (Test-Path -LiteralPath $_) } | Select-Object -Unique

if (-not $sdkCandidates) {
    throw "未找到 Android SDK。请先安装 Android SDK Platform 35、Build-Tools 和 Platform-Tools。"
}

$sdk = $sdkCandidates | Select-Object -First 1
$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk
$env:ANDROID_USER_HOME = Join-Path $workspaceRoot "Android\.android"
# 不要同时设置 ANDROID_PREFS_ROOT：AGP 8.x 会报
# AndroidLocationsException: Several environment variables ... contain different paths。
Remove-Item Env:\ANDROID_PREFS_ROOT -ErrorAction SilentlyContinue
$env:GRADLE_USER_HOME = Join-Path $workspaceRoot ".gradle\user-home"

$propertiesPath = Join-Path $projectRoot "local.properties"
$escapedSdk = $sdk.Replace("\", "\\").Replace(":", "\:")
[IO.File]::WriteAllText($propertiesPath, "sdk.dir=$escapedSdk`r`n", (New-Object Text.UTF8Encoding($false)))

& (Join-Path $projectRoot "bootstrap-gradle.ps1")

$gradleBat = Join-Path $workspaceRoot ".gradle\lanmouse-gradle\gradle-8.9\bin\gradle.bat"
& $gradleBat --no-daemon :app:assembleDebug
if ($LASTEXITCODE -ne 0) {
    throw "APK 构建失败，退出码: $LASTEXITCODE"
}

Write-Host ""
Write-Host "APK: $(Join-Path $projectRoot 'app\build\outputs\apk\debug\app-debug.apk')"