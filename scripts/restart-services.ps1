param([string]$EnvFile)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if (!$EnvFile) {
    $localEnv = Join-Path $root '.env'
    $EnvFile = if (Test-Path -LiteralPath $localEnv) { $localEnv } else { Join-Path $root '.env.example' }
}
$EnvFile = (Resolve-Path -LiteralPath $EnvFile).Path
$compose = Join-Path $root 'docker-compose.yml'
$runtime = Join-Path $PSScriptRoot '.runtime'
$pidsFile = Join-Path $runtime 'service-pids.json'
$names = @('identity-service','store-service','catalog-inventory-service','order-service','payment-finance-service','api-gateway','frontend')

if (!(Get-Command docker.exe -ErrorAction SilentlyContinue)) { throw 'Docker Desktop is not installed or docker.exe is not in PATH.' }
if (!(Get-Command mvn -ErrorAction SilentlyContinue)) { throw 'Maven is not in PATH.' }
if (!(Get-Command npm.cmd -ErrorAction SilentlyContinue)) { throw 'Node.js/npm is not in PATH.' }

# Start Docker Desktop only when its engine is not already available.
& docker info --format '{{.ServerVersion}}' *> $null
if ($LASTEXITCODE -ne 0) {
    $dockerDesktop = Join-Path $env:ProgramFiles 'Docker/Docker/Docker Desktop.exe'
    if (!(Test-Path -LiteralPath $dockerDesktop)) { throw 'Docker engine is unavailable and Docker Desktop was not found.' }
    Write-Host 'Starting Docker Desktop...'
    Start-Process -FilePath $dockerDesktop -WindowStyle Hidden | Out-Null
    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        Start-Sleep -Seconds 2
        & docker info --format '{{.ServerVersion}}' *> $null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
    }
    if (!$ready) { throw 'Docker did not become ready within 120 seconds.' }
}

# Stop only terminal process trees recorded by this script.
if (Test-Path -LiteralPath $pidsFile) {
    $previous = @(Get-Content -LiteralPath $pidsFile -Raw | ConvertFrom-Json)
    foreach ($entry in $previous) {
        $proc = Get-CimInstance Win32_Process -Filter "ProcessId = $($entry.pid)" -ErrorAction SilentlyContinue
        if ($proc -and ($proc.CommandLine -like '*run-service.ps1*' -or $proc.CommandLine -like '*run-frontend.ps1*')) {
            & taskkill.exe /PID $entry.pid /T /F | Out-Null
        }
    }
    Remove-Item -LiteralPath $pidsFile
}

# Java launchers can spawn an extra JVM on Windows; stop only JARs from this workspace.
$serviceJarPath = Join-Path $root 'services'
Get-CimInstance Win32_Process -Filter "name = 'java.exe'" | Where-Object {
    $_.CommandLine -and $_.CommandLine.Contains($serviceJarPath) -and
    $_.CommandLine.Contains('-jar') -and $_.CommandLine.Contains('target')
} | ForEach-Object {
    Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
}
Start-Sleep -Milliseconds 500

# Stop the Compose copies of the six applications; keep the databases and broker.
& docker compose --env-file $EnvFile -f $compose --profile services --profile gateway stop gateway identity-service store-service catalog-inventory-service order-service payment-finance-service
if ($LASTEXITCODE -ne 0) { throw 'Could not stop Docker application containers.' }

& docker compose --env-file $EnvFile -f $compose --profile services --profile gateway up -d --wait --no-recreate mysql postgres rabbitmq
if ($LASTEXITCODE -ne 0) { throw 'Could not start Docker infrastructure.' }

& mvn -q -f (Join-Path $root 'services/pom.xml') -DskipTests package
if ($LASTEXITCODE -ne 0) { throw 'Maven build failed.' }

$shell = (Get-Command pwsh.exe -ErrorAction SilentlyContinue)
if (!$shell) { $shell = Get-Command powershell.exe }
$serviceRunner = Join-Path $PSScriptRoot 'run-service.ps1'
$frontendRunner = Join-Path $PSScriptRoot 'run-frontend.ps1'
New-Item -ItemType Directory -Path $runtime -Force | Out-Null
$started = @()
foreach ($name in $names) {
    $runner = if ($name -eq 'frontend') { $frontendRunner } else { $serviceRunner }
    $args = @('-NoExit', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', ('"' + $runner + '"'))
    if ($name -ne 'frontend') { $args += @('-Service', $name, '-EnvFile', ('"' + $EnvFile + '"')) }
    $proc = Start-Process -FilePath $shell.Source -ArgumentList $args -WorkingDirectory $root -WindowStyle Normal -PassThru
    $started += [pscustomobject]@{ name = $name; pid = $proc.Id }
    Write-Host "Opened $name in terminal PID $($proc.Id)"
}
$started | ConvertTo-Json | Set-Content -LiteralPath $pidsFile -Encoding UTF8
Write-Host 'Gateway: http://localhost:8080/api/health'
Write-Host 'Frontend: http://localhost:5173'
