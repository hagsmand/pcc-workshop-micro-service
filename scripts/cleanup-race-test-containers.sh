#!/bin/bash

echo "=== Cleaning up race condition test containers ==="

# Force remove specific containers
echo "Removing race test containers..."
podman rm -f mongodb-race-test redis-race-test kafka-race-test zookeeper-race-test race-condition-test-runner 2>/dev/null || true

# Check for any containers still using the ports
echo ""
echo "=== Checking for containers using test ports ==="
echo "Port 27017 (MongoDB):"
podman ps -a --format "{{.Names}} {{.Ports}}" | grep 27017 || echo "  No containers found"

echo "Port 6379 (Redis):"
podman ps -a --format "{{.Names}} {{.Ports}}" | grep 6379 || echo "  No containers found"

echo "Port 9092 (Kafka):"
podman ps -a --format "{{.Names}} {{.Ports}}" | grep 9092 || echo "  No containers found"

# Remove any dangling networks
echo ""
echo "=== Cleaning up networks ==="
podman network rm race-test-network 2>/dev/null || echo "  Network already removed or doesn't exist"

# Show remaining containers
echo ""
echo "=== Remaining containers ==="
podman ps -a

echo ""
echo "=== Cleanup complete! ==="
echo "You can now run: podman-compose -f podman-compose.race-condition-test.yml up"

# Made with Bob
