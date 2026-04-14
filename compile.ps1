# compile.ps1 - Compiles the Smart Inventory System
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$src  = Join-Path $root "src\main\java"
$out  = Join-Path $root "out"
$cp   = @(
    "target\lib\zxing-core.jar",
    "target\lib\zxing-javase.jar",
    "target\lib\webcam-capture.jar",
    "target\lib\slf4j-api.jar",
    "target\lib\bridj.jar"
) | ForEach-Object { Join-Path $root $_ }

if (-not (Test-Path $out)) { New-Item -ItemType Directory -Path $out | Out-Null }

$files = Get-ChildItem -Recurse -Path $src -Filter "*.java" | Select-Object -ExpandProperty FullName

Write-Host "[Compile] Found $($files.Count) source files..."

$cpStr = $cp -join ";"

# Invoke javac directly with the file array — PowerShell handles quoting automatically
$result = & javac -encoding UTF-8 -cp $cpStr -d $out $files 2>&1
$exitCode = $LASTEXITCODE

if ($exitCode -eq 0) {
    Write-Host "[OK] Compilation successful! Starting app..." -ForegroundColor Green
    & java -cp "$out;$cpStr" com.inventory.Main
} else {
    $result | ForEach-Object { Write-Host $_ -ForegroundColor Red }
    Write-Host "[FAILED] Compilation failed." -ForegroundColor Red
    Read-Host "Press Enter to exit"
}
