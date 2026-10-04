param(
    [string]$Repository = 'E:\maven',
    [int]$Size = 200000,
    [int]$Forks = 2,
    [int]$Warmups = 3,
    [int]$Iterations = 5,
    [string]$Duration = '2s',
    [string]$ResultName = 'deep-clone-comparison'
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$outputDir = Join-Path $PSScriptRoot 'target/clone-jmh'
New-Item -ItemType Directory -Force $outputDir | Out-Null
$relativeJars = @(
    'org/openjdk/jmh/jmh-core/1.37/jmh-core-1.37.jar',
    'org/openjdk/jmh/jmh-generator-annprocess/1.37/jmh-generator-annprocess-1.37.jar',
    'net/sf/jopt-simple/jopt-simple/5.0.4/jopt-simple-5.0.4.jar',
    'org/apache/commons/commons-math3/3.6.1/commons-math3-3.6.1.jar',
    'org/apache/commons/commons-lang3/3.17.0/commons-lang3-3.17.0.jar',
    'com/esotericsoftware/kryo/5.6.2/kryo-5.6.2.jar',
    'com/esotericsoftware/minlog/1.3.1/minlog-1.3.1.jar',
    'com/esotericsoftware/reflectasm/1.11.9/reflectasm-1.11.9.jar',
    'org/objenesis/objenesis/3.4/objenesis-3.4.jar'
)
$jars = $relativeJars | ForEach-Object {
    $jarPath = Join-Path $Repository $_
    if (!(Test-Path -LiteralPath $jarPath)) { throw "Missing dependency: $jarPath" }
    $jarPath
}
$langClasses = Join-Path $projectRoot 'lang/target/classes'
if (!(Test-Path -LiteralPath $langClasses)) { throw 'Compile the lang module first (lang/target/classes required)' }
$classpath = "$langClasses;" + ($jars -join ';')
$reflectionDir = Join-Path $projectRoot 'lang/src/main/java/com/yulinlin/data/lang/reflection'
$sources = @('HandleAccess.java', 'ValueCopies.java', 'DeepCopies.java', 'ReflectionUtil.java') |
    ForEach-Object { Join-Path $reflectionDir $_ }
$sources += Join-Path $PSScriptRoot 'src/main/java/com/yulinlin/admin/DeepCloneBenchmark.java'
& javac --release 25 -encoding UTF-8 -cp $classpath -processorpath $classpath -processor org.openjdk.jmh.generators.BenchmarkProcessor -d $outputDir @sources
if ($LASTEXITCODE -ne 0) { throw 'Benchmark compilation failed' }
& java -cp "$outputDir;$classpath" org.openjdk.jmh.Main 'com.yulinlin.admin.DeepCloneBenchmark.*' -p "size=$Size" -f $Forks -wi $Warmups -i $Iterations -w $Duration -r $Duration -prof gc -foe true -rf json -rff (Join-Path $outputDir "$ResultName.json") -o (Join-Path $outputDir "$ResultName.log")
if ($LASTEXITCODE -ne 0) { throw 'Benchmark run failed; inspect the log' }
Get-Content (Join-Path $outputDir "$ResultName.log") -Tail 45
