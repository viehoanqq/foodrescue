param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('identity-service','store-service','catalog-inventory-service','order-service','payment-finance-service','api-gateway')]
    [string]$Service,
    [Parameter(Mandatory = $true)]
    [string]$EnvFile
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$settings = @{}
Get-Content -LiteralPath $EnvFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -and !$line.StartsWith('#') -and $line.Contains('=')) {
        $parts = $line.Split('=', 2)
        $settings[$parts[0].Trim()] = $parts[1].Trim().Trim('"').Trim("'")
    }
}

function Set-LocalEnv([string]$name, [string]$value) {
    [Environment]::SetEnvironmentVariable($name, $value, 'Process')
}

Set-LocalEnv 'RABBITMQ_HOST' 'localhost'
Set-LocalEnv 'RABBITMQ_PORT' '5672'
Set-LocalEnv 'RABBITMQ_USER' $settings['RABBITMQ_USER']
Set-LocalEnv 'RABBITMQ_PASSWORD' $settings['RABBITMQ_PASSWORD']
Set-LocalEnv 'INTERNAL_API_KEY' $settings['INTERNAL_API_KEY']
Set-LocalEnv 'JWT_SECRET' $settings['JWT_SECRET']
Set-LocalEnv 'IDENTITY_SERVICE_URL' 'http://localhost:8082'

switch ($Service) {
    'identity-service' {
        Set-LocalEnv 'DB_URL' 'jdbc:mysql://localhost:3307/identity_db?serverTimezone=Asia/Ho_Chi_Minh'
        Set-LocalEnv 'DB_USERNAME' 'identity_app'
        Set-LocalEnv 'DB_PASSWORD' $settings['IDENTITY_DB_PASSWORD']
        @('SEED_ACCOUNTS_ENABLED', 'SEED_ADMIN_EMAIL', 'SEED_ADMIN_PASSWORD',
          'SEED_OWNER_EMAIL', 'SEED_OWNER_PASSWORD', 'GOOGLE_CLIENT_ID') | ForEach-Object {
            Set-LocalEnv $_ $settings[$_]
        }
    }
    'store-service' {
        Set-LocalEnv 'DB_URL' 'jdbc:mysql://localhost:3307/store_db?serverTimezone=Asia/Ho_Chi_Minh'
        Set-LocalEnv 'DB_USERNAME' 'store_app'
        Set-LocalEnv 'DB_PASSWORD' $settings['STORE_DB_PASSWORD']
        Set-LocalEnv 'FINANCE_STORE_API_KEY' $settings['FINANCE_STORE_API_KEY']
    }
    'catalog-inventory-service' {
        Set-LocalEnv 'DB_URL' 'jdbc:postgresql://localhost:5433/catalog_db'
        Set-LocalEnv 'DB_USERNAME' 'catalog_app'
        Set-LocalEnv 'DB_PASSWORD' $settings['CATALOG_DB_PASSWORD']
        Set-LocalEnv 'STORE_SERVICE_URL' 'http://localhost:8083'
    }
    'order-service' {
        Set-LocalEnv 'DB_URL' 'jdbc:mysql://localhost:3307/order_db?serverTimezone=Asia/Ho_Chi_Minh'
        Set-LocalEnv 'DB_USERNAME' 'order_app'
        Set-LocalEnv 'DB_PASSWORD' $settings['ORDER_DB_PASSWORD']
        Set-LocalEnv 'CATALOG_SERVICE_URL' 'http://localhost:8084'
        Set-LocalEnv 'STORE_SERVICE_URL' 'http://localhost:8083'
    }
    'payment-finance-service' {
        Set-LocalEnv 'DB_URL' 'jdbc:postgresql://localhost:5433/finance_db'
        Set-LocalEnv 'DB_USERNAME' 'finance_app'
        Set-LocalEnv 'DB_PASSWORD' $settings['FINANCE_DB_PASSWORD']
        Set-LocalEnv 'FINANCE_STORE_API_KEY' $settings['FINANCE_STORE_API_KEY']
    }
    'api-gateway' {
        Set-LocalEnv 'IDENTITY_SERVICE_URL' 'http://localhost:8082'
        Set-LocalEnv 'STORE_SERVICE_URL' 'http://localhost:8083'
        Set-LocalEnv 'CATALOG_SERVICE_URL' 'http://localhost:8084'
        Set-LocalEnv 'ORDER_SERVICE_URL' 'http://localhost:8085'
        Set-LocalEnv 'FINANCE_SERVICE_URL' 'http://localhost:8086'
    }
}

$jar = Join-Path $root "services/$Service/target/$Service-0.1.0.jar"
if (!(Test-Path -LiteralPath $jar)) {
    throw "Missing $jar. Run scripts/restart-services.ps1 to build first."
}
$Host.UI.RawUI.WindowTitle = "FoodRescue $Service"
Write-Host "Starting $Service"
& java -jar $jar
if ($LASTEXITCODE -ne 0) {
    Write-Error "$Service exited with code $LASTEXITCODE"
}
