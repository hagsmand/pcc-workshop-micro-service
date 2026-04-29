# Podman Compose Setup for E-Commerce Microservices

This guide explains how to build and run the e-commerce microservices using Podman Compose.

## Prerequisites

- Podman installed on your system
- Podman Compose support available through `podman compose`
- At least 8GB of RAM available
- Sufficient disk space for building Maven projects and Docker images

## Project Structure

The project includes the following services:
- **common-library**: Shared library used by all services
- **service-registry**: Eureka service registry (port 8761)
- **api-gateway**: API Gateway (port 8080)
- **order-service**: Order management (port 8081)
- **inventory-service**: Inventory management (port 8082)
- **payment-service**: Payment processing (port 8083)
- **notification-service**: Notifications (port 8084)
- **shipping-service**: Shipping management (port 8085)
- **mvp-frontend**: Frontend UI (port 3000)

### Infrastructure Services:
- **Kafka**: Message broker (ports 9092, 29092)
- **Zookeeper**: Kafka coordination (port 2181)
- **PostgreSQL**: Database for order, payment, and shipping services (port 5432)
- **MongoDB**: Database for inventory and notification services (port 27017)
- **Redis**: Cache for notification service (port 6379)

## Building and Running

### 1. Build and Start All Services

```bash
podman compose -f podman-compose.yml up --build
```

This command will:
- Build all Maven projects from source
- Create Docker images for each service
- Start all infrastructure services (Kafka, PostgreSQL, MongoDB, Redis)
- Start all microservices in the correct order

### 2. Start Services in Detached Mode

```bash
podman compose -f podman-compose.yml up --build -d
```

### 3. View Logs

View logs for all services:
```bash
podman compose -f podman-compose.yml logs -f
```

View logs for a specific service:
```bash
podman compose -f podman-compose.yml logs -f order-service
```

### 4. Stop Services

```bash
podman compose -f podman-compose.yml down
```

### 5. Stop and Remove Volumes

```bash
podman compose -f podman-compose.yml down -v
```

## Service Startup Order

The services start in the following order:

1. **Infrastructure**: Zookeeper, Kafka, PostgreSQL, MongoDB, Redis
2. **Common Library**: Built first as a dependency
3. **Service Registry**: Eureka server starts with health checks
4. **Microservices**: All business services wait for service registry to be healthy
5. **API Gateway**: Starts after all microservices are running
6. **Frontend**: Starts after API Gateway is ready

## Accessing Services

- **Frontend**: http://localhost:3000
- **API Gateway**: http://localhost:8080
- **Service Registry (Eureka)**: http://localhost:8761
- **Order Service**: http://localhost:8081
- **Inventory Service**: http://localhost:8082
- **Payment Service**: http://localhost:8083
- **Notification Service**: http://localhost:8084
- **Shipping Service**: http://localhost:8085

## Health Checks

The service registry includes a health check that ensures it's fully started before dependent services begin. You can check the health status:

```bash
curl http://localhost:8761/actuator/health
```

## Troubleshooting

### Services fail to start

1. Check if all required ports are available
2. Ensure you have enough memory (at least 8GB recommended)
3. Check logs for specific service errors:
   ```bash
   podman compose -f podman-compose.yml logs service-name
   ```

### Build failures

1. Ensure you have a stable internet connection for Maven dependencies
2. Clear Maven cache if needed:
   ```bash
   podman compose -f podman-compose.yml down
   podman volume prune
   ```

### Database connection issues

1. Wait for PostgreSQL and MongoDB to fully initialize
2. Check database logs:
   ```bash
   podman compose -f podman-compose.yml logs postgres
   podman compose -f podman-compose.yml logs mongodb
   ```

## Rebuilding Specific Services

To rebuild a specific service without rebuilding everything:

```bash
podman compose -f podman-compose.yml up --build --force-recreate order-service
```

## Development Workflow

### Making Changes to a Service

When you modify code in any service (e.g., inventory-service), follow these steps:

#### 1. **Rebuild and Restart a Single Service**

```bash
# Rebuild only the changed service
podman compose -f podman-compose.yml up --build --force-recreate inventory-service -d

# Or rebuild and view logs in real-time
podman compose -f podman-compose.yml up --build --force-recreate inventory-service
```

This will:
- Rebuild only the inventory-service Docker image
- Run Maven clean package inside the container
- Restart the service with your changes
- Keep all other services running

#### 2. **Run Unit Tests Before Building**

Run unit tests inside the controlled Maven container defined in `podman-compose.test.yml`:

```bash
# Run all unit tests
podman compose -f podman-compose.test.yml run --rm unit-tests

# Run tests for a specific service and required upstream modules
MAVEN_MODULES="-pl inventory-service -am" \
  podman compose -f podman-compose.test.yml run --rm unit-tests

# Run a specific test class or method
MAVEN_MODULES="-pl inventory-service -am" MAVEN_ARGS="-Dtest=InventoryServiceImplTest" \
  podman compose -f podman-compose.test.yml run --rm unit-tests
```

**Note**: The test compose file persists Maven dependencies in the `maven-repo` volume. It also prevents Eureka register/fetch behavior, disables Kafka listener startup, and uses H2 for JPA context-load tests.

#### 3. **Complete Development Cycle for a Service**

```bash
# Step 1: Run unit tests
MAVEN_MODULES="-pl inventory-service -am" \
  podman compose -f podman-compose.test.yml run --rm unit-tests

# Step 2: If tests pass, rebuild and restart the service
podman compose -f podman-compose.yml up --build --force-recreate inventory-service -d

# Step 3: View logs to verify
podman compose -f podman-compose.yml logs -f inventory-service

# Step 4: Test the API
curl http://localhost:8082/actuator/health
```

#### 4. **Run Coverage**

```bash
# Run all tests and generate JaCoCo reports
podman compose -f podman-compose.test.yml run --rm coverage

# Run coverage for a specific service
MAVEN_MODULES="-pl inventory-service -am" \
  podman compose -f podman-compose.test.yml run --rm coverage
```

Coverage reports are written to each module's `target/site/jacoco/index.html`.

#### 5. **Debug a Service**

```bash
# Stop the service
podman compose -f podman-compose.yml stop inventory-service

# Run the service with debug port exposed
podman compose -f podman-compose.yml run --rm -p 5005:5005 inventory-service \
  java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 -jar /app/app.jar

# Connect your IDE debugger to localhost:5005
```

#### 6. **Quick Iteration Workflow**

For rapid development, use this workflow:

```bash
# Terminal 1: Watch logs
podman compose -f podman-compose.yml logs -f inventory-service

# Terminal 2: Make changes, then rebuild
# After making code changes:
podman compose -f podman-compose.yml up --build --force-recreate inventory-service -d

# The service will rebuild and restart automatically
```

### Development Best Practices

#### Local Development with Hot Reload

For faster development, you can use Spring Boot DevTools:

1. Add DevTools to your service's `pom.xml`:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-devtools</artifactId>
    <optional>true</optional>
</dependency>
```

2. Mount your source code as a volume (create `podman-compose.dev.yml`):
```yaml
services:
  inventory-service:
    volumes:
      - ./inventory-service/src:/app/src:ro
```

3. Run with the dev override:
```bash
podman compose -f podman-compose.yml -f podman-compose.dev.yml up inventory-service
```

#### Rerunning Tests During Development

```bash
# Run a service test set repeatedly as you make changes
MAVEN_MODULES="-pl inventory-service -am" \
  podman compose -f podman-compose.test.yml run --rm unit-tests

# Run a specific test class
MAVEN_MODULES="-pl inventory-service -am" MAVEN_ARGS="-Dtest=InventoryServiceImplTest" \
  podman compose -f podman-compose.test.yml run --rm unit-tests
```

#### Code Quality Checks

```bash
# Run code quality checks
podman compose -f podman-compose.test.yml run --rm unit-tests \
  mvn -B -ntp -pl inventory-service -am checkstyle:check

# Run static analysis
podman compose -f podman-compose.test.yml run --rm unit-tests \
  mvn -B -ntp -pl inventory-service -am spotbugs:check
```

### Test Cache

The test compose workflow creates and reuses the `maven-repo` volume automatically:

```bash
# Reuse cached Maven dependencies
podman compose -f podman-compose.test.yml run --rm unit-tests

# Remove the cache when you want a completely fresh dependency download
podman compose -f podman-compose.test.yml down -v
```

### Common Development Commands

```bash
# Rebuild all services (after major changes)
podman compose -f podman-compose.yml up --build -d

# Rebuild specific service
podman compose -f podman-compose.yml up --build --force-recreate inventory-service -d

# View logs for specific service
podman compose -f podman-compose.yml logs -f inventory-service

# Execute command in running container
podman compose -f podman-compose.yml exec inventory-service bash

# Check service health
podman compose -f podman-compose.yml ps
curl http://localhost:8082/actuator/health

# Restart service without rebuilding
podman compose -f podman-compose.yml restart inventory-service

# Stop specific service
podman compose -f podman-compose.yml stop inventory-service

# Start specific service
podman compose -f podman-compose.yml start inventory-service
```

### Testing Changes End-to-End

After making changes to a service:

```bash
# 1. Rebuild the service
podman compose -f podman-compose.yml up --build --force-recreate inventory-service -d

# 2. Wait for service to be healthy
sleep 10

# 3. Test the API
curl -X POST http://localhost:8080/api/inventory \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test Product",
    "description": "Test Description",
    "quantity": 100
  }'

# 4. Check the response
curl http://localhost:8080/api/inventory

# 5. Check logs if there are issues
podman compose -f podman-compose.yml logs -f inventory-service
```

## Differences from docker-compose.yml

The `podman-compose.yml` file differs from `docker-compose.yml` in the following ways:

1. **Builds from source**: Uses Dockerfiles to build Maven projects instead of mounting pre-built JARs
2. **Multi-stage builds**: Each service uses a multi-stage build for smaller final images
3. **Dependency management**: Proper build order with `depends_on` conditions
4. **Health checks**: Service registry includes health checks for better startup coordination
5. **Environment variables**: Services are configured with Docker-specific profiles

## Clean Up

To completely remove all containers, images, and volumes:

```bash
# Stop and remove containers
podman compose -f podman-compose.yml down -v

# Remove all built images
podman rmi ecommerce/common-library:latest
podman rmi ecommerce/service-registry:latest
podman rmi ecommerce/order-service:latest
podman rmi ecommerce/inventory-service:latest
podman rmi ecommerce/payment-service:latest
podman rmi ecommerce/notification-service:latest
podman rmi ecommerce/shipping-service:latest
podman rmi ecommerce/api-gateway:latest

# Prune unused resources
podman system prune -a
```

## Notes

- First build will take longer as Maven downloads all dependencies
- Subsequent builds will be faster due to Docker layer caching
- The common-library is built first and cached for other services
- All services use Alpine-based images for smaller size
- Java 17 is used for all services
