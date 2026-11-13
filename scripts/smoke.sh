#!/usr/bin/env bash
# End-to-end check through Kong against the running stack:
#   1. an order is paid and its stock taken,
#   2. an order above the card limit is declined and its stock put back,
#   3. a paid order is cancelled, its payment refunded and its stock put back,
#   4. the customer's orders are listed, newest first,
#   5. a cart with two products is checked out as one paid order,
#   6. an order sent twice with the same Idempotency-Key is placed once,
#   7. an order for more than is in stock ends as OUT_OF_STOCK,
#   8. an admin restocks a product, and a customer may not,
#   9. an admin adds a product, restocks it, changes its price and takes it off sale,
#  10. the browser storefront is served with its security headers and can start a sign-in,
#  11. the customer was emailed about the order from steps 1 and 3: paid, cancelled, refunded,
#  12. Kong turns away request floods and oversized bodies, and the mailbox needs its password,
#  13. an admin sees every customer's orders and cancels the cart order from step 5; a customer may not.
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

# stock_of <productId>: prints how many are in stock, or nothing if the product has no stock entry yet
stock_of() {
  curl -s -H "Authorization: Bearer $TOKEN" "$GATEWAY/stock/$1" | sed -n 's/.*"available":\([0-9]*\).*/\1/p'
}

# expect_stock <productId> <count>: waits up to 30 s for the stock level
expect_stock() {
  local now=""
  for _ in $(seq 30); do
    now=$(stock_of "$1")
    [ "$now" = "$2" ] && { echo "  stock of $1: $now"; return 0; }
    sleep 1
  done
  echo "FAILED: stock of $1 is $now, not $2"
  exit 1
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

curl -s --retry 20 --retry-all-errors --retry-delay 3 --fail -o /dev/null -H "Authorization: Bearer $TOKEN" "$GATEWAY/stock"
BEANS_STOCK=$(stock_of "$COFFEE_BEANS")
MACHINE_STOCK=$(stock_of "$ESPRESSO_MACHINE")
echo "In stock: $BEANS_STOCK x Coffee Beans 1kg, $MACHINE_STOCK x Espresso Machine"

echo "1. Paying for 2 x Coffee Beans 1kg (37.50) ..."
PAID=$(place "$COFFEE_BEANS" 2)
expect_status "$PAID" PAID
expect_stock "$COFFEE_BEANS" $((BEANS_STOCK - 2))

echo "2. Ordering 5 x Espresso Machine (1245.00, above the 1000.00 card limit) ..."
DECLINED=$(place "$ESPRESSO_MACHINE" 5)
expect_status "$DECLINED" PAYMENT_DECLINED
expect_stock "$ESPRESSO_MACHINE" "$MACHINE_STOCK"

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
expect_stock "$COFFEE_BEANS" "$BEANS_STOCK"

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

echo "7. Ordering one more Espresso Machine than is in stock ($((MACHINE_STOCK + 1))) ..."
TOO_MANY=$(place "$ESPRESSO_MACHINE" $((MACHINE_STOCK + 1)))
expect_status "$TOO_MANY" OUT_OF_STOCK
expect_stock "$ESPRESSO_MACHINE" "$MACHINE_STOCK"

echo "8. Restocking 5 Espresso Machines as $SHOP_ADMIN, then trying as $SHOP_USER ..."
ADMIN_TOKEN=$(curl -s -X POST "$GATEWAY/realms/shop/protocol/openid-connect/token" \
  -d grant_type=password -d client_id=shop-cli \
  -d "username=$SHOP_ADMIN" --data-urlencode "password=$SHOP_ADMIN_PASSWORD" | field access_token)
[ -n "$ADMIN_TOKEN" ] || { echo "FAILED: could not sign in as $SHOP_ADMIN"; exit 1; }
curl -s --fail -X POST "$GATEWAY/stock/$ESPRESSO_MACHINE/restock" -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" -d '{"quantity":5}' > /dev/null
expect_stock "$ESPRESSO_MACHINE" $((MACHINE_STOCK + 5))
CODE=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$GATEWAY/stock/$ESPRESSO_MACHINE/restock" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"quantity":5}')
[ "$CODE" = "403" ] || { echo "FAILED: a customer restock returned $CODE, not 403"; exit 1; }
echo "  $SHOP_USER is refused with 403"
expect_stock "$ESPRESSO_MACHINE" $((MACHINE_STOCK + 5))

echo "9. Adding a product as $SHOP_ADMIN, selling it, then taking it off sale ..."
CODE=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$GATEWAY/products" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"name":"Milk Frother","price":39.90}')
[ "$CODE" = "403" ] || { echo "FAILED: a customer adding a product got $CODE, not 403"; exit 1; }
echo "  $SHOP_USER is refused with 403"
FROTHER=$(curl -s --fail -X POST "$GATEWAY/products" -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" -d '{"name":"Milk Frother","price":39.90}' | field id)
echo "  added Milk Frother $FROTHER"
expect_stock "$FROTHER" 0
NO_STOCK=$(place "$FROTHER" 1)
expect_status "$NO_STOCK" OUT_OF_STOCK
curl -s --fail -X POST "$GATEWAY/stock/$FROTHER/restock" -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" -d '{"quantity":3}' > /dev/null
expect_stock "$FROTHER" 3
curl -s --fail -X PATCH "$GATEWAY/products/$FROTHER" -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" -d '{"price":34.90}' > /dev/null
SOLD=$(place "$FROTHER" 1)
expect_status "$SOLD" PAID
curl -s -H "Authorization: Bearer $TOKEN" "$GATEWAY/orders/$SOLD" | grep -q '"unitPrice":34.90' \
  || { echo "FAILED: order $SOLD was not charged the new price 34.90"; exit 1; }
echo "  sold at the new price 34.90"
expect_stock "$FROTHER" 2
curl -s --fail -X PATCH "$GATEWAY/products/$FROTHER" -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" -d '{"onSale":false}' > /dev/null
curl -s --fail -H "Authorization: Bearer $TOKEN" "$GATEWAY/products" | grep -q "$FROTHER" \
  && { echo "FAILED: the off-sale product is still listed"; exit 1; }
CODE=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$GATEWAY/orders" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d "{\"items\":[{\"productId\":\"$FROTHER\",\"quantity\":1}]}")
[ "$CODE" = "400" ] || { echo "FAILED: ordering an off-sale product returned $CODE, not 400"; exit 1; }
echo "  off sale: no longer listed, and ordering it returns 400"

echo "10. Checking the browser storefront ..."
PAGE=$(curl -s -D - --fail "$GATEWAY/")
echo "$PAGE" | grep -qi "^content-security-policy: default-src 'self'" || { echo "FAILED: the storefront has no Content-Security-Policy"; exit 1; }
echo "$PAGE" | grep -q "<title>MyShoppingCart</title>" || { echo "FAILED: the storefront page did not load"; exit 1; }
CHALLENGE=$(printf 'a%.0s' $(seq 43))
SIGN_IN="$GATEWAY/realms/shop/protocol/openid-connect/auth?client_id=shop-web&response_type=code&scope=openid&code_challenge=$CHALLENGE&code_challenge_method=S256"
CODE=$(curl -s -o /dev/null -w '%{http_code}' "$SIGN_IN&redirect_uri=http%3A%2F%2Flocalhost%3A8000%2F")
[ "$CODE" = "200" ] || { echo "FAILED: the storefront sign-in page returned $CODE"; exit 1; }
CODE=$(curl -s -o /dev/null -w '%{http_code}' "$SIGN_IN&redirect_uri=http%3A%2F%2Fevil.example%2F")
[ "$CODE" = "400" ] || { echo "FAILED: Keycloak accepted a foreign redirect ($CODE)"; exit 1; }
echo "  served with a Content-Security-Policy; sign-in starts, and only returns to this site"

echo "11. Checking the emails about order ${PAID:0:8} in the test mailbox ..."
SUBJECTS=""
for _ in $(seq 30); do
  SUBJECTS=$(curl -s -u "$MAILBOX_USER:$MAILBOX_PASSWORD" "$GATEWAY/mail/api/v1/search?query=${PAID:0:8}" | grep -o '"Subject":"[^"]*"' | sort | tr '\n' ' ')
  [ "$(echo "$SUBJECTS" | grep -o Subject | wc -l)" -ge 3 ] && break
  sleep 1
done
for subject in "Your order is confirmed" "Your order is cancelled" "Your refund is on its way"; do
  echo "$SUBJECTS" | grep -q "$subject" || { echo "FAILED: no '$subject' email for order ${PAID:0:8} (found: $SUBJECTS)"; exit 1; }
done
[ "$(echo "$SUBJECTS" | grep -o Subject | wc -l)" -eq 3 ] || { echo "FAILED: expected 3 emails for order ${PAID:0:8}, found: $SUBJECTS"; exit 1; }
echo "  3 emails: confirmed, cancelled, refund on its way"

echo "12. Checking the gateway's protections ..."
CODE=$(curl -s -o /dev/null -w '%{http_code}' "$GATEWAY/mail/api/v1/messages")
[ "$CODE" = "401" ] || { echo "FAILED: the mailbox answered $CODE without its password, not 401"; exit 1; }
echo "  the mailbox needs its password (401 without)"
# "Expect:" stops curl asking first (Kong would answer 417 before the body is sent).
CODE=$(head -c 2000000 /dev/zero | tr '\0' 'x' | curl -s -o /dev/null -w '%{http_code}' -X POST "$GATEWAY/orders" \
  -H "Expect:" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" --data-binary @-)
[ "$CODE" = "413" ] || { echo "FAILED: a 2 MB request body got $CODE, not 413"; exit 1; }
echo "  a 2 MB request body is refused (413)"
# One curl sends all 120 at once; separate curl processes start too slowly to exceed 50 a second.
BURST=()
for i in $(seq 120); do BURST+=(-o /dev/null "$GATEWAY/products?burst=$i"); done
LIMITED=$(curl -s --parallel --parallel-immediate --parallel-max 120 -w '%{http_code}\n' \
  -H "Authorization: Bearer $TOKEN" "${BURST[@]}" | grep -c 429 || true)
[ "$LIMITED" -gt 0 ] || { echo "FAILED: 120 requests at once were all let through"; exit 1; }
echo "  $LIMITED of 120 requests sent at once got 429 Too Many Requests"
sleep 2  # let the per-second limit reset before anything else calls the gateway

echo "13. Cancelling the cart order from step 5 as $SHOP_ADMIN ..."
CODE=$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $TOKEN" "$GATEWAY/admin/orders")
[ "$CODE" = "403" ] || { echo "FAILED: a customer listing all orders got $CODE, not 403"; exit 1; }
echo "  $SHOP_USER is refused with 403"
curl -s --fail -H "Authorization: Bearer $ADMIN_TOKEN" "$GATEWAY/admin/orders?status=PAID" | grep -q "\"id\":\"$CHECKED_OUT\"" \
  || { echo "FAILED: order $CHECKED_OUT is not among the paid orders the admin sees"; exit 1; }
curl -s --fail -X POST -H "Authorization: Bearer $ADMIN_TOKEN" "$GATEWAY/admin/orders/$CHECKED_OUT/cancel" > /dev/null
expect_status "$CHECKED_OUT" CANCELLED

echo "OK: paid, declined, cancelled with refund, listed, checked out from the cart, placed once per key, out of stock, restocked by an admin only, a product added, repriced and taken off sale, the storefront served, the customer emailed, the gateway protected, and an order cancelled by an admin"
