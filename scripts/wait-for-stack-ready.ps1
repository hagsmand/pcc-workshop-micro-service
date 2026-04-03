#!/usr/bin/env pwsh
# PowerShell script to wait for Docker stack readiness
# Converted from wait-for-stack-ready.sh

param(
    [string]$DockerContext,
    [string]$GatewayContainer,
    [int]$MaxAttempts,
    [int]$SleepSeconds,
    [int]$HealthTimeout,
    [int]$EurekaTimeout
)

# Set defaults if not provided
if (-not $DockerContext) { $DockerContext = if ($env:DOCKER_CONTEXT) { $env:DOCKER_CONTEXT } else { "default" } }
if (-not $GatewayContainer) { $GatewayContainer = if ($env:GATEWAY_CONTAINER) { $env:GATEWAY_CONTAINER } else { "api-gateway" } }
if (-not $MaxAttempts) { $MaxAttempts = if ($env:MAX_ATTEMPTS) { [int]$env:MAX_ATTEMPTS } else { 90 } }
if (-not $SleepSeconds) { $SleepSeconds = if ($env:SLEEP_SECONDS) { [int]$env:SLEEP_SECONDS } else { 2 } }
if (-not $HealthTimeout) { $HealthTimeout = if ($env:HEALTH_TIMEOUT) { [int]$env:HEALTH_TIMEOUT } else { 5 } }
if (-not $EurekaTimeout) { $EurekaTimeout = if ($env:EUREKA_TIMEOUT) { [int]$env:EUREKA_TIMEOUT } else { 8 } }

$ErrorActionPreference = "Stop"

$HealthChecks = @(
    @{Name = "service-registry"; Url = "http://service-registry:8761/" }
    @{Name = "api-gateway"; Url = "http://api-gateway:8080/actuator/health" }
    @{Name = "order-service"; Url = "http://order-service:8081/actuator/health" }
    @{Name = "inventory-service"; Url = "http://inventory-service:8082/actuator/health" }
    @{Name = "payment-service"; Url = "http://payment-service:8083/actuator/health" }
    @{Name = "notification-service"; Url = "http://notification-service:8084/actuator/health" }
    @{Name = "shipping-service"; Url = "http://shipping-service:8085/actuator/health" }
)

$ExpectedEurekaApps = @(
    "ORDER-SERVICE",
    "INVENTORY-SERVICE",
    "PAYMENT-SERVICE",
    "SHIPPING-SERVICE",
    "NOTIFICATION-SERVICE",
    "API-GATEWAY"
)

$TotalHealth = $HealthChecks.Count
$TotalEureka = $ExpectedEurekaApps.Count

function Join-List {
    param([array]$Items)
    return $Items -join ", "
}

Write-Host "Waiting for stack readiness..."
Write-Host "context=$DockerContext gateway_container=$GatewayContainer max_attempts=$MaxAttempts sleep=${SleepSeconds}s"

for ($i = 1; $i -le $MaxAttempts; $i++) {
    $pendingHealth = @()
    $pendingEureka = @()
    $eurekaChecked = $false

    # Check health endpoints
    foreach ($check in $HealthChecks) {
        $name = $check.Name
        $url = $check.Url
        
        try {
            $result = docker --context $DockerContext exec $GatewayContainer /bin/sh -lc "curl -fsS --max-time $HealthTimeout '$url' >/dev/null" 2>$null
            if ($LASTEXITCODE -ne 0) {
                $pendingHealth += $name
            }
        }
        catch {
            $pendingHealth += $name
        }
    }

    $healthReadyCount = $TotalHealth - $pendingHealth.Count

    # Check Eureka registration if all health checks pass
    if ($pendingHealth.Count -eq 0) {
        $eurekaChecked = $true
        
        try {
            $apps = docker --context $DockerContext exec $GatewayContainer /bin/sh -lc "curl -sS --max-time $EurekaTimeout http://service-registry:8761/eureka/apps" 2>$null
            
            if ($LASTEXITCODE -eq 0 -and $apps) {
                foreach ($app in $ExpectedEurekaApps) {
                    if ($apps -notmatch "<name>$app</name>") {
                        $pendingEureka += $app
                    }
                }
            }
            else {
                # If curl failed, all apps are pending
                $pendingEureka = $ExpectedEurekaApps
            }
        }
        catch {
            $pendingEureka = $ExpectedEurekaApps
        }
    }

    $eurekaReadyCount = $TotalEureka - $pendingEureka.Count

    # Check if all services are ready
    if ($pendingHealth.Count -eq 0 -and $pendingEureka.Count -eq 0) {
        Write-Host "[attempt $i/$MaxAttempts] all checks passed: health ${healthReadyCount}/${TotalHealth}, eureka ${eurekaReadyCount}/${TotalEureka}"
        Write-Host "stack-ready"
        exit 0
    }

    # Display status
    if ($pendingHealth.Count -gt 0) {
        $pendingHealthList = Join-List -Items $pendingHealth
        Write-Host "[attempt $i/$MaxAttempts] waiting: health ${healthReadyCount}/${TotalHealth} ready; pending health: $pendingHealthList; eureka check: deferred"
    }
    elseif ($eurekaChecked) {
        $pendingEurekaList = Join-List -Items $pendingEureka
        Write-Host "[attempt $i/$MaxAttempts] waiting: health ${healthReadyCount}/${TotalHealth} ready; eureka ${eurekaReadyCount}/${TotalEureka} ready; missing apps: $pendingEurekaList"
    }

    Start-Sleep -Seconds $SleepSeconds
}

Write-Host "Timed out after $MaxAttempts attempts."
Write-Host "stack-not-ready"
exit 1

# Made with Bob
