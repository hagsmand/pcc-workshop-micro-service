#!/usr/bin/env bash
set -euo pipefail

DOCKER_CONTEXT="${DOCKER_CONTEXT:-default}"
GATEWAY_CONTAINER="${GATEWAY_CONTAINER:-api-gateway}"
MAX_ATTEMPTS="${MAX_ATTEMPTS:-90}"
SLEEP_SECONDS="${SLEEP_SECONDS:-2}"
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-5}"
EUREKA_TIMEOUT="${EUREKA_TIMEOUT:-8}"

HEALTH_CHECKS=(
  "service-registry|http://service-registry:8761/"
  "api-gateway|http://api-gateway:8080/actuator/health"
  "order-service|http://order-service:8081/actuator/health"
  "inventory-service|http://inventory-service:8082/actuator/health"
  "payment-service|http://payment-service:8083/actuator/health"
  "notification-service|http://notification-service:8084/actuator/health"
  "shipping-service|http://shipping-service:8085/actuator/health"
)

EXPECTED_EUREKA_APPS=(
  "ORDER-SERVICE"
  "INVENTORY-SERVICE"
  "PAYMENT-SERVICE"
  "SHIPPING-SERVICE"
  "NOTIFICATION-SERVICE"
  "API-GATEWAY"
)

TOTAL_HEALTH="${#HEALTH_CHECKS[@]}"
TOTAL_EUREKA="${#EXPECTED_EUREKA_APPS[@]}"

join_list() {
  local IFS=", "
  echo "$*"
}

echo "Waiting for stack readiness..."
echo "context=$DOCKER_CONTEXT gateway_container=$GATEWAY_CONTAINER max_attempts=$MAX_ATTEMPTS sleep=${SLEEP_SECONDS}s"

for ((i = 1; i <= MAX_ATTEMPTS; i++)); do
  pending_health=()
  pending_eureka=()
  eureka_checked=0

  for check in "${HEALTH_CHECKS[@]}"; do
    name="${check%%|*}"
    u="${check#*|}"
    if ! docker --context "$DOCKER_CONTEXT" exec "$GATEWAY_CONTAINER" /bin/sh -lc \
      "curl -fsS --max-time $HEALTH_TIMEOUT '$u' >/dev/null" >/dev/null 2>&1; then
      pending_health+=("$name")
    fi
  done

  health_ready_count=$((TOTAL_HEALTH - ${#pending_health[@]}))

  if [[ ${#pending_health[@]} -eq 0 ]]; then
    eureka_checked=1
    apps="$(docker --context "$DOCKER_CONTEXT" exec "$GATEWAY_CONTAINER" /bin/sh -lc \
      "curl -sS --max-time $EUREKA_TIMEOUT http://service-registry:8761/eureka/apps" 2>/dev/null || true)"

    for app in "${EXPECTED_EUREKA_APPS[@]}"; do
      if ! printf '%s' "$apps" | grep -q "<name>$app</name>"; then
        pending_eureka+=("$app")
      fi
    done
  fi

  eureka_ready_count=$((TOTAL_EUREKA - ${#pending_eureka[@]}))

  if [[ ${#pending_health[@]} -eq 0 && ${#pending_eureka[@]} -eq 0 ]]; then
    echo "[attempt $i/$MAX_ATTEMPTS] all checks passed: health ${health_ready_count}/${TOTAL_HEALTH}, eureka ${eureka_ready_count}/${TOTAL_EUREKA}"
    echo "stack-ready"
    exit 0
  fi

  if [[ ${#pending_health[@]} -gt 0 ]]; then
    echo "[attempt $i/$MAX_ATTEMPTS] waiting: health ${health_ready_count}/${TOTAL_HEALTH} ready; pending health: $(join_list "${pending_health[@]}"); eureka check: deferred"
  elif [[ "$eureka_checked" == "1" ]]; then
    echo "[attempt $i/$MAX_ATTEMPTS] waiting: health ${health_ready_count}/${TOTAL_HEALTH} ready; eureka ${eureka_ready_count}/${TOTAL_EUREKA} ready; missing apps: $(join_list "${pending_eureka[@]}")"
  fi

  /bin/sleep "$SLEEP_SECONDS"
done

echo "Timed out after $MAX_ATTEMPTS attempts."
echo "stack-not-ready"
exit 1
