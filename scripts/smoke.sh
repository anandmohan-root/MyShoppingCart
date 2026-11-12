#!/usr/bin/env bash
# Signs in to Keycloak, places an order through Kong and waits for it to be PAID.
# Run from the repository root after `docker compose up -d --build`.
set -euo pipefail
source .env

GATEWAY=http://localhost:8000
field() { sed -n "s/.*\"$1\":\"\([^\"]*\)\".*/\1/p"; }

echo "Waiting for Keycloak and signing in as $SHOP_USER ..."
TOKEN=""
for _ in $(seq 60); do
  TOKEN=$(curl -s -X POST "$GATEWAY/realms/shop/protocol/openid-connect/token" \
    -d grant_type=password -d client_id=shop-cli \
    -d "username=$SHOP_USER" --data-urlencode "password=$SHOP_USER_PASSWORD" | field access_token) || true
  [ -n "$TOKEN" ] && break
  sleep 5
done
[ -n "$TOKEN" ] || { echo "could not get a token"; exit 1; }

echo "Products:"
curl -s --retry 20 --retry-all-errors --retry-delay 3 --fail \
  -H "Authorization: Bearer $TOKEN" "$GATEWAY/products"
echo

echo "Placing an order for 2 x Coffee Beans 1kg ..."
ORDER=$(curl -s --retry 20 --retry-all-errors --retry-delay 3 --fail -X POST "$GATEWAY/orders" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"productId":"33333333-3333-3333-3333-333333333333","quantity":2}')
echo "$ORDER"
ORDER_ID=$(echo "$ORDER" | field id)

for _ in $(seq 30); do
  ORDER=$(curl -s -H "Authorization: Bearer $TOKEN" "$GATEWAY/orders/$ORDER_ID")
  if [ "$(echo "$ORDER" | field status)" = "PAID" ]; then
    echo "$ORDER"
    echo "OK: order $ORDER_ID is PAID"
    exit 0
  fi
  sleep 1
done
echo "$ORDER"
echo "FAILED: order $ORDER_ID did not reach PAID"
exit 1
