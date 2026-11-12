#!/usr/bin/env bash
# End-to-end check through Kong against the running stack:
#   1. an order is paid,
#   2. an order above the card limit is declined,
#   3. a paid order is cancelled and its payment refunded,
#   4. the customer's orders are listed, newest first,
#   5. a cart with two products is checked out as one paid order,
#   6. an order sent twice with the same Idempotency-Key is placed once.
# Run from the repository root after `docker compose up -d --build`.
set -euo pipefail
source .env

GATEWAY=http://localhost:8000
COFFEE_BEANS=33333333-3333-3333-3333-333333333333     # 18.75
ESPRESSO_MACHINE=11111111-1111-1111-1111-111111111111 # 249.00
GRINDER=22222222-2222-2222-2222-222222222222          # 89.50
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

# place <productId> <quantity>: prints the new order's id
place() {
  curl -s --retry 20 --retry-all-errors --retry-delay 3 --fail -X POST "$GATEWAY/orders" \
    -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
    -d "{\"items\":[{\"productId\":\"$1\",\"quantity\":$2}]}" | field id
}

# expect_status <orderId> <status>: waits up to 30 s for the order to reach the status
expect_status() {
  local order
  for _ in $(seq 30); do
    order=$(curl -s -H "Authorization: Bearer $TOKEN" "$GATEWAY/orders/$1")
    if [ "$(echo "$order" | field status)" = "$2" ]; then
      echo "  $order"
      return 0
    fi
    sleep 1
  done
  echo "  $order"
  echo "FAILED: order $1 did not reach $2"
  exit 1
}

echo "1. Paying for 2 x Coffee Beans 1kg (37.50) ..."
PAID=$(place "$COFFEE_BEANS" 2)
expect_status "$PAID" PAID

echo "2. Ordering 5 x Espresso Machine (1245.00, above the 1000.00 card limit) ..."
DECLINED=$(place "$ESPRESSO_MACHINE" 5)
expect_status "$DECLINED" PAYMENT_DECLINED

echo "3. Cancelling the paid order from step 1 ..."
curl -s --fail -X POST -H "Authorization: Bearer $TOKEN" "$GATEWAY/orders/$PAID/cancel" > /dev/null
expect_status "$PAID" CANCELLED
REFUND=""
for _ in $(seq 30); do
  REFUND=$(docker compose exec -T postgres psql -U shop -d payments -tAc \
    "SELECT status FROM payment WHERE order_id = '$PAID'")
  [ "$REFUND" = "REFUNDED" ] && break
  sleep 1
done
[ "$REFUND" = "REFUNDED" ] || { echo "FAILED: payment for $PAID is '$REFUND', not REFUNDED"; exit 1; }
echo "  payment for order $PAID is REFUNDED"

echo "4. Listing my orders (newest first) ..."
MINE=$(curl -s --fail -H "Authorization: Bearer $TOKEN" "$GATEWAY/orders")
echo "$MINE" | grep -o '"total":[^,]*,"status":"[^"]*"' | sed 's/^/  /' | head -5
for id in "$DECLINED" "$PAID"; do
  echo "$MINE" | grep -q "\"id\":\"$id\"" || { echo "FAILED: order $id is missing from GET /orders"; exit 1; }
done
NEWEST=$(echo "$MINE" | sed -n 's/^\[{"id":"\([^"]*\)".*/\1/p')
[ "$NEWEST" = "$DECLINED" ] || { echo "FAILED: the newest order ($DECLINED) is not listed first"; exit 1; }

echo "5. Putting 1 x Coffee Beans and 1 x Grinder in the cart and checking out (108.25) ..."
curl -s --fail -X DELETE -H "Authorization: Bearer $TOKEN" "$GATEWAY/cart"
for product in "$COFFEE_BEANS" "$GRINDER"; do
  curl -s --fail -X PUT "$GATEWAY/cart/items/$product" \
    -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"quantity":1}' > /dev/null
done
echo "  cart: $(curl -s --fail -H "Authorization: Bearer $TOKEN" "$GATEWAY/cart")"
CHECKED_OUT=$(curl -s --fail -X POST -H "Authorization: Bearer $TOKEN" "$GATEWAY/cart/checkout" | field id)
expect_status "$CHECKED_OUT" PAID
LINES=$(curl -s -H "Authorization: Bearer $TOKEN" "$GATEWAY/orders/$CHECKED_OUT" | grep -o '"unitPrice"' | wc -l)
[ "$LINES" -eq 2 ] || { echo "FAILED: order $CHECKED_OUT has $LINES lines, not 2"; exit 1; }
CART=$(curl -s --fail -H "Authorization: Bearer $TOKEN" "$GATEWAY/cart")
[ "$CART" = '{"items":[]}' ] || { echo "FAILED: the cart is not empty after checkout: $CART"; exit 1; }
echo "  the cart is empty again"

echo "6. Sending the same order twice with one Idempotency-Key ..."
KEY="smoke-$(date +%s)-$RANDOM"
order_with_key() {
  curl -s --fail -X POST "$GATEWAY/orders" -H "Authorization: Bearer $TOKEN" \
    -H "Idempotency-Key: $KEY" -H "Content-Type: application/json" \
    -d "{\"items\":[{\"productId\":\"$COFFEE_BEANS\",\"quantity\":1}]}" | field id
}
FIRST=$(order_with_key)
SECOND=$(order_with_key)
[ "$FIRST" = "$SECOND" ] || { echo "FAILED: the repeated request placed a second order ($FIRST, $SECOND)"; exit 1; }
echo "  both requests returned order $FIRST"
expect_status "$FIRST" PAID

echo "OK: paid, declined, cancelled with refund, listed, checked out from the cart, and placed once per key"
