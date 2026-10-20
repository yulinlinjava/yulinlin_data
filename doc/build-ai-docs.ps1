#requires -Version 7.0
param([switch]$Check)
$ErrorActionPreference = 'Stop'
# 只处理 Markdown；不调用 Maven、Java 编译器或测试。
$generatorArgs = @((Join-Path $PSScriptRoot 'build-ai-docs.mjs'))
if ($Check) { $generatorArgs += '--check' }
& node @generatorArgs
if ($LASTEXITCODE -ne 0) { throw '文档生成或静态链接检查失败，请查看上方详情。' }
