#!/usr/bin/env bash
# Runs the storefront browser tests (e2e/) against the running stack, in Microsoft's Playwright
# container, so no browser needs installing here. The container shares Kong's network, so the
# browser reaches the shop at http://localhost:8000, the one address Keycloak signs in for.
# Run from the repository root after `docker compose up -d --build`.
set -euo pipefail
set -a  # export the test users, so docker run -e passes them on
source .env
set +a

PLAYWRIGHT_VERSION=$(sed -n 's/.*"@playwright\/test": "\([^"]*\)".*/\1/p' e2e/package.json)

# MSYS_NO_PATHCONV stops Git Bash on Windows from rewriting the container paths.
MSYS_NO_PATHCONV=1 docker run --rm \
  --network "container:$(docker compose ps -q kong)" \
  -v "$(pwd)/e2e:/e2e:ro" \
  -e SHOP_USER -e SHOP_USER_PASSWORD -e SHOP_ADMIN -e SHOP_ADMIN_PASSWORD \
  "mcr.microsoft.com/playwright:v$PLAYWRIGHT_VERSION-noble" \
  bash -c "cp -r /e2e /work && cd /work && rm -rf node_modules test-results && npm ci --no-audit --no-fund --silent && npx playwright test --reporter=line"
