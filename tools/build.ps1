# 声迹 - 构建脚本 (Windows / PowerShell 5.1+)
#
# 作用: 在工程路径**含中文**时也能正常构建。
#   背景: AGP 与 Kotlin 编译器在非 ASCII 路径下有已知问题 —— AGP 会直接报错
#   (可用 gradle.properties 的 android.overridePathCheck=true 放行), 但 Kotlin
#   编译器仍会因 argfile 转义 bug 找不到源文件 (报 "ASRu97F3u9891..." 这种乱码路径)。
#   因此本脚本在需要时自动建一个**纯 ASCII 的目录联接 (junction)**, 通过联接构建。
#
# 用法 (在工程任意位置):
#   powershell -ExecutionPolicy Bypass -File tools\build.ps1 assembleStdDebug
#   powershell -ExecutionPolicy Bypass -File tools\build.ps1 assembleStdDebug assembleFullDebug
#   powershell -ExecutionPolicy Bypass -File tools\build.ps1 --offline assembleStdDebug
#
# 不传参数时默认构建标准版 (assembleStdDebug)。
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$GradleArgs
)

$ErrorActionPreference = "Stop"
$proj = Split-Path -Parent $PSScriptRoot          # tools/ 的上一级 = 工程根
if (-not (Test-Path (Join-Path $proj "gradlew.bat"))) {
    throw "找不到 gradlew.bat, 请确认脚本位于 <工程根>/tools/ 下: $proj"
}
if (-not $GradleArgs -or $GradleArgs.Count -eq 0) { $GradleArgs = @("assembleStdDebug") }

# JDK: 优先用已设的 JAVA_HOME, 否则尝试常见安装位置
if (-not $env:JAVA_HOME) {
    $cand = @("D:\Java\jdk-17.0.20+8", "C:\Program Files\Java\jdk-17.0.20+8") +
            (Get-ChildItem "D:\Java", "C:\Program Files\Java" -Directory -ErrorAction SilentlyContinue |
                Where-Object { $_.Name -like "jdk-17*" } | ForEach-Object { $_.FullName })
    $jdk = $cand | Where-Object { $_ -and (Test-Path (Join-Path $_ "bin\javac.exe")) } | Select-Object -First 1
    if ($jdk) { $env:JAVA_HOME = $jdk; Write-Host "[build] JAVA_HOME -> $jdk" }
    else { Write-Warning "[build] 未找到 JDK 17, 请先设置 JAVA_HOME" }
}
if ($env:JAVA_HOME) { $env:PATH = (Join-Path $env:JAVA_HOME "bin") + ";" + $env:PATH }

# 路径含非 ASCII 时, 用同盘的 ASCII 联接构建
$buildRoot = $proj
if ($proj -match "[^\x00-\x7F]") {
    $qual = Split-Path -Qualifier $proj                 # 例如 "D:"
    $link = Join-Path $qual "_shengji_asr_build"
    if (Test-Path $link) {
        $cur = (Get-Item $link -Force).Target
        if ($cur -and ($cur -ne $proj)) {
            throw "ASCII 联接已存在但指向别处: $link -> $cur (请先手动删除该联接)"
        }
    } else {
        New-Item -ItemType Junction -Path $link -Target $proj | Out-Null
        Write-Host "[build] 工程路径含非 ASCII 字符, 已建立 ASCII 联接: $link -> $proj"
    }
    $buildRoot = $link
} else {
    Write-Host "[build] 工程路径已是 ASCII, 直接构建: $proj"
}

Push-Location $buildRoot
try {
    Write-Host "[build] gradlew $($GradleArgs -join ' ')  (cwd=$buildRoot)"
    & .\gradlew.bat @GradleArgs
    if ($LASTEXITCODE -ne 0) { throw "构建失败, gradlew 退出码 $LASTEXITCODE" }
    Write-Host "[build] 完成。产物: app\build\outputs\apk\<flavor>\debug\"
    Write-Host "[build] 复制到交付工作区 output\: python tools\package_delivery.py"
} finally {
    Pop-Location
}