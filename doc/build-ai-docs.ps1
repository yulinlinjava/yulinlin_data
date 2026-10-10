#requires -Version 7.0
param([switch]$Check)
$ErrorActionPreference = 'Stop'
$docRoot = $PSScriptRoot
$repoRoot = Split-Path $docRoot -Parent
$manualFiles = @(
    '01-接入与数据源.md',
    '02-CRUD与统计分析.md',
    '03-关联查询与代理.md',
    '04-工具类.md',
    '05-扩展开发与维护.md',
    '06-接口安全.md',
    '07-SQLite与H2性能报告.md',
    '08-查询缓存.md'
)
$aiFiles = @($manualFiles[0..3]) + $manualFiles[5] + $manualFiles[7]
$parts = @(
    '# yulinlin-data AI 使用指南',
    '> 派生文件，维护源为 doc 下六个使用专题。重新导出：./doc/build-ai-docs.ps1。',
    '用途：给不能读取仓库的 AI 提供一个附件。包含接入、CRUD/统计/事务、关联代理、工具、接口安全和查询缓存；内部扩展与完整性能报告不在此导出中，按需另提供第五或第七专题。',
    '适用 JDK 25、Spring Boot 3.5.16、制品 3.0。2026-10-10 已运行本地库 JMH、JDK 25 全模块测试编译、lang/缓存专项测试和 Schema 定向测试；代码片段不等于所有数据库服务器均已集成验证，真实账号、表、路径和接口由业务提供。'
)
foreach ($name in $aiFiles) {
    $path = Join-Path $docRoot $name
    $content = [IO.File]::ReadAllText($path).Replace("`r`n", "`n").Trim()
    $insideFence = $false
    $lines = foreach ($line in ($content -split "`n")) {
        if ($line -match '^\s*```') { $insideFence = !$insideFence }
        if (!$insideFence -and $line -match '^#{1,5} ') { '#' + $line } else { $line }
    }
    $parts += "<!-- source: doc/$name -->`n" + ($lines -join "`n")
}
$expected = ($parts -join "`n`n---`n`n") + "`n"
$output = Join-Path $docRoot 'AI接入指南.md'
if ($Check) {
    if (!(Test-Path -LiteralPath $output) -or
        [IO.File]::ReadAllText($output).Replace("`r`n", "`n") -cne $expected) {
        throw 'AI guide is stale. Run ./doc/build-ai-docs.ps1 first.'
    }
} else {
    [IO.File]::WriteAllText($output, $expected, [Text.UTF8Encoding]::new($false))
}
$files = @((Join-Path $repoRoot 'README.md'), (Join-Path $repoRoot 'llms.txt'),
    (Join-Path $docRoot 'README.md'), $output)
$files += $manualFiles | ForEach-Object { Join-Path $docRoot $_ }
foreach ($file in $files) {
    $content = [IO.File]::ReadAllText($file)
    if ([regex]::Matches($content, '(?m)^\s*```').Count % 2 -ne 0) {
        throw "Unclosed code fence: $file"
    }
    $prose = [regex]::Replace($content, '(?ms)^\s*```.*?^\s*```[^\r\n]*', '')
    foreach ($match in [regex]::Matches($prose, '\[[^\]]*\]\(([^)]+)\)')) {
        $target = $match.Groups[1].Value.Trim('<', '>')
        if ($target -match '^[a-zA-Z][a-zA-Z0-9+.-]*:' -or $target.StartsWith('#')) { continue }
        $target = [Uri]::UnescapeDataString(($target -split '#')[0])
        if (!(Test-Path -LiteralPath (Join-Path (Split-Path $file -Parent) $target))) {
            throw ("Broken link in " + $file + ": " + $target)
        }
    }
}
Write-Host "OK: $($manualFiles.Count) manuals; $($aiFiles.Count) exported for AI; current links and fences checked."
