#!/usr/bin/env pwsh
# PowerShell script to manually create PostgreSQL databases
# Use this if the automatic initialization script doesn't work in Podman on Windows

param(
    [string]$ContainerName = "postgres",
    [string]$PostgresUser = "postgres",
    [string[]]$Databases = @("order_db", "payment_db", "shipping_db")
)

Write-Host "Creating PostgreSQL databases in container: $ContainerName"
Write-Host "Databases to create: $($Databases -join ', ')"
Write-Host ""

foreach ($db in $Databases) {
    Write-Host "Creating database: $db"
    
    # Simple approach: check if database exists, create if not
    $checkSql = "SELECT 1 FROM pg_database WHERE datname='$db'"
    $createSql = "CREATE DATABASE $db"
    
    try {
        # Check if database exists
        $exists = podman exec $ContainerName psql -U $PostgresUser -tAc $checkSql 2>$null
        
        if ($exists -eq "1") {
            Write-Host "  Database already exists: $db" -ForegroundColor Yellow
        }
        else {
            # Create database
            $result = podman exec $ContainerName psql -U $PostgresUser -c $createSql 2>&1
            
            if ($LASTEXITCODE -eq 0) {
                Write-Host "  Successfully created database: $db" -ForegroundColor Green
            }
            else {
                Write-Host "  Failed to create database: $db" -ForegroundColor Red
                Write-Host "  Error: $result" -ForegroundColor Red
            }
        }
    }
    catch {
        Write-Host "  Error processing database: $db" -ForegroundColor Red
        Write-Host "  Error: $_" -ForegroundColor Red
    }
    
    Write-Host ""
}

# Verify databases were created
Write-Host "Verifying databases..."
try {
    $listDbResult = podman exec $ContainerName psql -U $PostgresUser -c "\l" 2>&1
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "Current databases:" -ForegroundColor Cyan
        Write-Host $listDbResult
    }
}
catch {
    Write-Host "Could not list databases: $_" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Database initialization complete!" -ForegroundColor Green

# Made with Bob
