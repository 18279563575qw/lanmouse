param(
    [string]$Output = "LanMouseServer.exe"
)

$ErrorActionPreference = "Stop"
$source = Join-Path $PSScriptRoot "src\LanMouseServer.cs"
$outputPath = Join-Path $PSScriptRoot $Output
$compilerCandidates = @(
    (Join-Path $env:WINDIR "Microsoft.NET\Framework64\v4.0.30319\csc.exe"),
    (Join-Path $env:WINDIR "Microsoft.NET\Framework\v4.0.30319\csc.exe")
)

$compiler = $compilerCandidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
if (-not $compiler) {
    throw "未找到 .NET Framework 编译器 csc.exe。Windows 10/11 通常自带它。"
}

& $compiler /nologo /target:exe /platform:anycpu /optimize+ /out:$outputPath /r:System.Web.Extensions.dll $source
if ($LASTEXITCODE -ne 0) {
    throw "编译失败，退出码: $LASTEXITCODE"
}

Write-Host "构建成功: $outputPath"