#!/bin/bash

# Exit on error, but allow for custom error handling
set -e

# Function to check if a service is ready
check_service() {
  local service_url=$1
  local service_name=$2
  local max_attempts=$3
  local attempt=1

  echo "Waiting for $service_name to be ready..."
  
  # Special handling for PostgreSQL
  if [[ "$service_name" == "PostgreSQL" ]]; then
    while [ $attempt -le $max_attempts ]; do
      # Simply check if we can establish a TCP connection to PostgreSQL port
      if nc -z postgres 5432; then
        echo "$service_name is ready!"
        return 0
      fi
      printf "."
      sleep 3
      ((attempt++))
    done
    echo "ERROR: $service_name not available after $max_attempts attempts"
    return 1
  else
    # HTTP service check
    while ! curl --output /dev/null --silent --head --fail "$service_url"; do
      if [ $attempt -eq $max_attempts ]; then
        echo "ERROR: $service_name not available after $max_attempts attempts"
        return 1
      fi
      printf "."
      sleep 3
      ((attempt++))
    done
    echo "$service_name is ready!"
    return 0
  fi
}

# Function to deploy a connector with retries
deploy_connector() {
  local config_file=$1
  local connector_name=$2
  local max_attempts=5
  local attempt=1
  local http_code

  echo "Deploying $connector_name..."
  
  while [ $attempt -le $max_attempts ]; do
    http_code=$(curl -s -o /tmp/deploy_response.json -w "%{http_code}" -X POST \
      -H "Content-Type: application/json" \
      --data @"$config_file" \
      http://connect:8083/connectors)
    
    if [ "$http_code" -eq 201 ] || [ "$http_code" -eq 200 ]; then
      echo -e "$connector_name deployed successfully!"
      return 0
    elif [ "$http_code" -eq 409 ]; then
      # Connector already exists, check its status
      connector_id=$(jq -r '.name' "$config_file")
      status_code=$(curl -s -o /tmp/status_response.json -w "%{http_code}" \
        http://connect:8083/connectors/$connector_id/status)
      
      if [ "$status_code" -eq 200 ]; then
        state=$(jq -r '.connector.state' /tmp/status_response.json)
        if [ "$state" = "RUNNING" ] || [ "$state" = "PAUSED" ]; then
          echo -e "$connector_name already exists and is in $state state"
          return 0
        else
          echo -e "$connector_name exists but is in $state state. Trying to restart..."
          curl -s -X POST http://connect:8083/connectors/$connector_id/restart
          sleep 5
        fi
      fi
    else
      echo "Attempt $attempt: Failed to deploy $connector_name (HTTP $http_code)"
      cat /tmp/deploy_response.json
      sleep 5
    fi
    
    ((attempt++))
  done
  
  echo "ERROR: Failed to deploy $connector_name after $max_attempts attempts"
  return 1
}

# Verify dependencies
check_service "http://connect:8083/connectors" "Kafka Connect" 20 || exit 1
check_service "http://schema-registry:8081/subjects" "Schema Registry" 10 || exit 1
check_service "none" "PostgreSQL" 20 || exit 1

# Deploy connectors
deploy_connector "rest-source-config.json" "REST connector" || exit 1
deploy_connector "jdbc-sink-config.json" "JDBC sink connector" || exit 1

echo "All connectors deployed successfully!"

# Keep checking connector status periodically
if [ "${MONITOR_CONNECTORS:-false}" = "true" ]; then
  echo "Starting connector monitoring..."
  
  while true; do
    echo "== Connector Status Report: $(date) =="
    curl -s http://connect:8083/connectors | jq -r '.[]' | while read connector; do
      status=$(curl -s http://connect:8083/connectors/$connector/status)
      connector_state=$(echo $status | jq -r '.connector.state')
      echo "Connector: $connector - State: $connector_state"
      
      # Display task states
      echo $status | jq -r '.tasks[] | "  Task \(.id): \(.state)"'
    done
    
    sleep 60
  done
fi