param(
    [Parameter(Mandatory = $true)]
    [string]$Version
)

$ErrorActionPreference = "Stop"
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$workspaceRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\.."))
$installRoot = Join-Path $workspaceRoot ".gradle\lanmouse-gradle"
$gradleHome = Join-Path $installRoot "gradle-$Version"
$gradleBat = Join-Path $gradleHome "bin\gradle.bat"

if (Test-Path -LiteralPath $gradleBat) {
    Write-Host "Gradle $Version 已存在: $gradleHome"
    exit 0
}

New-Item -ItemType Directory -Force -Path $installRoot | Out-Null
$zipPath = Join-Path $installRoot "gradle-$Version-bin.zip"
$checksumPath = "$zipPath.sha256"
$downloadUrl = "https://services.gradle.org/distributions/gradle-$Version-bin.zip"

Write-Host "正在下载 Gradle $Version ..."
Invoke-WebRequest -UseBasicParsing -Uri $downloadUrl -OutFile $zipPath
Invoke-WebRequest -UseBasicParsing -Uri "$downloadUrl.sha256" -OutFile $checksumPath

$expected = ((Get-Content -LiteralPath $checksumPath -Raw).Trim() -split '\s+')[0].ToLowerInvariant()
$actual = (Get-FileHash -LiteralPath $zipPath -Algorithm SHA256).Hash.ToLowerInvariant()
if ($expected -ne $actual) {
    throw "Gradle 下载校验失败。期望 $expected，实际 $actual"
}

Write-Host "正在解压 Gradle ..."
Expand-Archive -LiteralPath $zipPath -DestinationPath $installRoot -Force
if (-not (Test-Path -LiteralPath $gradleBat)) {
    throw "Gradle 解压后未找到 gradle.bat。"
}

Write-Host "Gradle 已安装: $gradleHome"
