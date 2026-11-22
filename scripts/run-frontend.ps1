$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$frontend = Join-Path $root 'frontend'
$Host.UI.RawUI.WindowTitle = 'FoodRescue frontend'

Set-Location -LiteralPath $frontend
if (!(Test-Path -LiteralPath (Join-Path $frontend 'node_modules'))) {
    Write-Host 'Installing frontend dependencies...'
    & npm.cmd install
    if ($LASTEXITCODE -ne 0) { throw 'npm install failed.' }
}

Write-Host 'Frontend: http://localhost:5173'
& npm.cmd run dev -- --host 127.0.0.1 --strictPort
if ($LASTEXITCODE -ne 0) { throw "Frontend exited with code $LASTEXITCODE" }
