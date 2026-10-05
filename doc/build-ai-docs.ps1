#requires -Version 7.0
param([switch]$Check)
$ErrorActionPreference = 'Stop'
$docRoot = $PSScriptRoot
$repoRoot = Split-Path $docRoot -Parent
$topicFiles = @(
    '00-context.md', '10-orm.md', '12-relations.md', '15-datasources.md', '16-sqlite.md', '17-postgresql.md', '20-transactions.md', '30-http.md',
    '40-reflection.md', '50-utilities.md', '90-troubleshooting.md'
)
$parts = @(
    '# yulinlin-data：外部 AI 单文件接入指南',
    '> 自动生成，请勿直接编辑。维护源为 doc/topics/，生成命令：./doc/build-ai-docs.ps1。',
    '用途：上传一个文件给外部 AI。已包含当前全部专题；无需再上传相同专题、旧案例或性能报告。制品版本 3.0；JDK 25；Spring Boot 3.5。示例的验证范围见各专题。',
    '阅读顺序：上下文 → 按任务阅读 ORM/级联/事务、HTTP、反射或其他工具 → 排障与交付检查。示例地址、表名、账号均为占位，执行写操作前必须按业务确认。'
)
foreach ($name in $topicFiles) {
    $path = Join-Path $docRoot "topics/$name"
    $content = [IO.File]::ReadAllText($path).Replace("`r`n", "`n").Trim()
    # Promote topic titles to sections only outside fenced examples.
    $insideFence = $false
    $lines = foreach ($line in ($content -split "`n")) {
        if ($line -match '^\s*```') { $insideFence = !$insideFence }
        if (!$insideFence -and $line -match '^#{1,5} ') { '#' + $line } else { $line }
    }
    $parts += "<!-- source: doc/topics/$name -->`n" + ($lines -join "`n")
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

# Check current navigation/docs only; historical examples have a separate status.
$files = @((Join-Path $repoRoot 'README.md'), (Join-Path $repoRoot 'llms.txt'))
$files += (Get-ChildItem $docRoot -File -Filter '*.md').FullName
$files += $topicFiles | ForEach-Object { Join-Path $docRoot "topics/$_" }
$files += Join-Path $docRoot 'archive/README.md'
foreach ($file in $files) {
    $content = [IO.File]::ReadAllText($file)
    $fences = [regex]::Matches($content, '(?m)^\s*```').Count
    if ($fences % 2 -ne 0) { throw "Unclosed code fence: $file" }
    $prose = [regex]::Replace($content, '(?ms)^\s*```.*?^\s*```[^\r\n]*', '')
    foreach ($match in [regex]::Matches($prose, '\[[^\]]*\]\(([^)]+)\)')) {
        $target = $match.Groups[1].Value.Trim('<', '>')
        if ($target -match '^[a-zA-Z][a-zA-Z0-9+.-]*:' -or $target.StartsWith('#')) { continue }
        $target = [Uri]::UnescapeDataString(($target -split '#')[0])
        if (!(Test-Path -LiteralPath (Join-Path (Split-Path $file -Parent) $target))) {
            throw "Broken link in ${file}: $target"
        }
    }
}
Write-Host "OK: $($topicFiles.Count) topics; AI guide synchronized; current file links and fences checked."
