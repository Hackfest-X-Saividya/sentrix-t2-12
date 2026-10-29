$ErrorActionPreference = 'Stop'
$moduleRoot = Split-Path -Parent $PSScriptRoot
$jdkBin = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin' } else { $null }
if (-not $jdkBin -or -not (Test-Path (Join-Path $jdkBin 'javac.exe'))) {
    $jdkBin = Get-ChildItem (Join-Path $env:ProgramFiles 'Java') -Directory -Filter 'jdk-*' -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | ForEach-Object { Join-Path $_.FullName 'bin' } |
        Where-Object { Test-Path (Join-Path $_ 'javac.exe') } | Select-Object -First 1
}
if (-not $jdkBin) { throw 'A JDK with javac is required; set JAVA_HOME and retry.' }
$javac = Join-Path $jdkBin 'javac.exe'
$java = Join-Path $jdkBin 'java.exe'
$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("zerowatch-rule-smoke-" + [guid]::NewGuid().ToString('N'))
$classes = Join-Path $tempRoot 'classes'
$stub = Join-Path $tempRoot 'stub\org\springframework\stereotype\Component.java'
New-Item -ItemType Directory -Force $classes, (Split-Path -Parent $stub) | Out-Null
try {
    @'
package org.springframework.stereotype;
public @interface Component {}
'@ | Set-Content -NoNewline $stub
    $sources = @(
        $stub,
        (Join-Path $moduleRoot 'src\main\java\ai\zerotrace\zerowatch\model\EventType.java'),
        (Join-Path $moduleRoot 'src\main\java\ai\zerotrace\zerowatch\model\RuntimeEvent.java'),
        (Join-Path $moduleRoot 'src\main\java\ai\zerotrace\zerowatch\rules\Detection.java'),
        (Join-Path $moduleRoot 'src\main\java\ai\zerotrace\zerowatch\rules\DetectionRule.java'),
        (Join-Path $moduleRoot 'src\main\java\ai\zerotrace\zerowatch\rules\AccessDeniedBurstRule.java'),
        (Join-Path $moduleRoot 'src\main\java\ai\zerotrace\zerowatch\rules\RawSqlOperationRule.java'),
        (Join-Path $moduleRoot 'src\main\java\ai\zerotrace\zerowatch\rules\RuleEvaluator.java'),
        (Join-Path $PSScriptRoot 'VerifyZeroWatchRules.java')
    )
    & $javac --release 17 -d $classes @sources
    if ($LASTEXITCODE -ne 0) { throw 'javac failed to compile the ZeroWatch rule smoke harness.' }
    & $java -cp $classes VerifyZeroWatchRules
    if ($LASTEXITCODE -ne 0) { throw 'ZeroWatch rule smoke harness failed.' }
}
finally {
    if (Test-Path $tempRoot) { Remove-Item -LiteralPath $tempRoot -Recurse -Force }
}
