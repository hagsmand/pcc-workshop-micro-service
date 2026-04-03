# Scripts Directory

This directory contains utility scripts for managing the ecommerce microservices stack.

## Cross-Platform Compatibility

### Line Endings
- **Shell scripts (`.sh`)**: Always use LF (Unix) line endings
- **PowerShell scripts (`.ps1`)**: Use native line endings (CRLF on Windows, LF on Unix)
- Git is configured via `.gitattributes` to handle this automatically

### For Windows Users

If you encounter the error: `/bin/bash^M: bad interpreter: No such file or directory`

This means the shell script has Windows (CRLF) line endings. Fix it with:

**Option 1: Use the PowerShell script instead**
```powershell
.\scripts\init-postgres-dbs.ps1
```

**Option 2: Fix line endings and rebuild**
```powershell
# Fix line endings
(Get-Content scripts/create-multiple-postgres-dbs.sh -Raw) -replace "`r`n", "`n" | Set-Content scripts/create-multiple-postgres-dbs.sh -NoNewline

# Rebuild containers
podman-compose down -v
podman-compose up -d
```

**Option 3: Re-normalize the repository**
```powershell
git add --renormalize .
git commit -m "Normalize line endings"
```

## Available Scripts

### Bash Scripts (Linux/Mac/Docker containers)

#### `create-multiple-postgres-dbs.sh`
Creates multiple PostgreSQL databases during container initialization.
- **Usage**: Automatically executed by PostgreSQL container on first start
- **Environment**: Runs inside PostgreSQL container
- **Databases created**: `order_db`, `payment_db`, `shipping_db`

#### `wait-for-stack-ready.sh`
Waits for all microservices to be healthy and registered with Eureka.
- **Usage**: `./scripts/wait-for-stack-ready.sh`
- **Environment**: Runs on host machine (requires Docker/Podman CLI)
- **Parameters**:
  - `DOCKER_CONTEXT`: Docker context to use (default: "default")
  - `GATEWAY_CONTAINER`: Gateway container name (default: "api-gateway")
  - `MAX_ATTEMPTS`: Maximum retry attempts (default: 90)
  - `SLEEP_SECONDS`: Sleep between attempts (default: 2)

### PowerShell Scripts (Windows/Cross-platform)

#### `init-postgres-dbs.ps1`
Manually creates PostgreSQL databases in a running container.
- **Usage**: `.\scripts\init-postgres-dbs.ps1`
- **When to use**: When automatic database initialization fails on Windows
- **Parameters**:
  - `-ContainerName`: PostgreSQL container name (default: "postgres")
  - `-PostgresUser`: PostgreSQL user (default: "postgres")
  - `-Databases`: Array of database names (default: @("order_db", "payment_db", "shipping_db"))

**Example:**
```powershell
.\scripts\init-postgres-dbs.ps1 -ContainerName "my-postgres" -Databases @("db1", "db2")
```

#### `wait-for-stack-ready.ps1`
PowerShell version of the stack readiness checker.
- **Usage**: `.\scripts\wait-for-stack-ready.ps1`
- **Compatibility**: PowerShell 5.1+ (Windows PowerShell and PowerShell Core)
- **Parameters**:
  - `-DockerContext`: Docker context (default: "default")
  - `-GatewayContainer`: Gateway container name (default: "api-gateway")
  - `-MaxAttempts`: Maximum retry attempts (default: 90)
  - `-SleepSeconds`: Sleep between attempts (default: 2)
  - `-HealthTimeout`: Health check timeout (default: 5)
  - `-EurekaTimeout`: Eureka check timeout (default: 8)

**Example:**
```powershell
.\scripts\wait-for-stack-ready.ps1 -MaxAttempts 120 -SleepSeconds 3
```

## Troubleshooting

### Database Not Found Error
If services fail with "database does not exist":

1. Check PostgreSQL logs:
   ```bash
   podman logs postgres
   ```

2. Look for the line endings error:
   ```
   /bin/bash^M: bad interpreter: No such file or directory
   ```

3. Use the PowerShell script to create databases:
   ```powershell
   .\scripts\init-postgres-dbs.ps1
   ```

4. Restart affected services:
   ```powershell
   podman-compose restart order-service payment-service shipping-service
   ```

### Health Checks Failing
If `wait-for-stack-ready` shows all services as unhealthy:

1. Verify containers are running:
   ```bash
   podman ps
   ```

2. Check if api-gateway has curl:
   ```bash
   podman exec api-gateway which curl
   ```

3. Test connectivity manually:
   ```bash
   podman exec api-gateway curl http://service-registry:8761/
   ```

4. Check service logs:
   ```bash
   podman logs service-registry
   podman logs order-service
   ```

## Best Practices

1. **Always commit with normalized line endings**: The `.gitattributes` file ensures this
2. **Use PowerShell scripts on Windows**: They're more reliable than bash scripts
3. **Check logs first**: Most issues are visible in container logs
4. **Wait for dependencies**: Services need time to start and register with Eureka