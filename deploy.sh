#!/bin/bash
set -e

# --- 1. Maven Build ---
echo "--- 1. Performing CLEAN MAVEN BUILD for all modules ---"
# This runs from the root and builds all sub-modules
mvn clean package -DskipTests

# --- 2. Docker Compose (Build Images & Start Services) ---
echo "--- 2. Building Images and Starting Infrastructure/Applications ---"
docker compose -f infra/docker-compose.yml up -d --build --force-recreate

echo "--- DEPLOYMENT COMPLETE ---"
docker compose -f infra/docker-compose.yml ps