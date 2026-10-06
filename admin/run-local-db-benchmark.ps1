param(
    [int]$Size = 100000,
    [int]$BusinessThreads = 4,
    [int]$RequestSize = 128,
    [int]$Forks = 1,
    [int]$Warmups = 2,
    [int]$Iterations = 5,
    [string]$Duration = '1s',
    [string]$MaxHeap = '2g',
    [string]$ResultName = 'sqlite-h2-business-threads'
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$outputDir = Join-Path $PSScriptRoot 'target/local-db-jmh'
New-Item -ItemType Directory -Force $outputDir | Out-Null

$mavenCommand = Get-Command mvn.cmd, mvn -ErrorAction SilentlyContinue | Select-Object -First 1
if ($null -ne $mavenCommand) {
    $maven = $mavenCommand.Source
} else {
    $profileRoots = @([Environment]::GetFolderPath('UserProfile'))
    $javaCommand = Get-Command java -ErrorAction SilentlyContinue
    if ($null -ne $javaCommand) {
        $javaHome = Split-Path (Split-Path $javaCommand.Source -Parent) -Parent
        $javaProfile = Split-Path (Split-Path $javaHome -Parent) -Parent
        $profileRoots += $javaProfile
    }
    $maven = $profileRoots | Select-Object -Unique | ForEach-Object {
            $cache = Join-Path $_ '.m2/wrapper/dists'
            if (Test-Path -LiteralPath $cache) {
                Get-ChildItem -LiteralPath $cache -Filter 'mvn.cmd' -File -Recurse -ErrorAction SilentlyContinue
            }
        } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1 -ExpandProperty FullName
}
if ([string]::IsNullOrWhiteSpace($maven)) {
    throw 'Maven was not found in PATH or the local Maven wrapper cache'
}

Push-Location $projectRoot
try {
    & $maven -pl admin -am '-Dmaven.test.skip=true' '-Plocal-db-benchmark' package
    if ($LASTEXITCODE -ne 0) { throw 'Benchmark package failed' }
} finally {
    Pop-Location
}

$benchmarkJar = Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'target') -Filter '*-benchmarks.jar' |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if ($null -eq $benchmarkJar) { throw 'Benchmark JAR was not generated' }

$benchmark = 'com.yulinlin.admin.benchmark.LocalDatabaseWriteBenchmark.*'
$json = Join-Path $outputDir "$ResultName.json"
$log = Join-Path $outputDir "$ResultName.log"
& java -jar $benchmarkJar.FullName $benchmark -p "size=$Size" -p "businessThreads=$BusinessThreads" `
    -p "requestSize=$RequestSize" -f $Forks -wi $Warmups -i $Iterations `
    -w $Duration -r $Duration -bm avgt -tu ms -t 1 -prof gc -foe true -rf json -rff $json -o $log `
    -jvmArgsAppend "-Xms512m -Xmx$MaxHeap --enable-native-access=ALL-UNNAMED"
if ($LASTEXITCODE -ne 0) { throw "Benchmark failed; inspect $log" }
Get-Content -LiteralPath $log -Tail 60
