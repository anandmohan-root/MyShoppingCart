# MyShoppingCart

MyShoppingCart is an online shopping application built on the on-premises shopping platform
architecture: a product catalog, a shopping cart, ordering, stock and payment, with sign-in through Keycloak and every
request entering through Kong. It runs in Docker containers and starts with one Docker Compose command.

![MyShoppingCart architecture: Kong routes sign-in to Keycloak and requests to the catalog, order and inventory services; order-service keeps carts in Redis and publishes OrderCreated to Kafka through an outbox, inventory-service reserves the stock and publishes StockReserved, payment-service records the payment and publishes PaymentCompleted, and every service sends telemetry to Grafana.](docs/architecture.png)

The numbers follow one order from sign-in to `PAID`; the key under the diagram explains each step. To change the
diagram, edit `docs/architecture.pptx` in PowerPoint and export the slide again as `docs/architecture.png`.

## Architecture concepts

| Concept | Where to look |
|---|---|
| Hexagonal layout (domain, application ports, adapters in/out) | every service: `domain`, `application`, `adapter/in`, `adapter/out` |
| Database per service | `infra/postgres/init.sql`, one Flyway migration per service |
| Transactional outbox | Every service: `JdbcProducts`, `JdbcOrders`, `OutboxInventoryEvents` and `OutboxPaymentEvents` write the event with the data, `OutboxRelay` publishes it |
| Keeping services in step with events | A product added to the catalog publishes `ProductCreated`; inventory opens its stock at 0 (`ProductCreatedListener`), without either service calling the other |
| Event-driven saga (choreography) over Kafka | `OrderCreated` -> inventory -> `StockReserved` -> payment -> `PaymentCompleted` (order `PAID`) or `PaymentDeclined` (order `PAYMENT_DECLINED`); or `StockRejected` (order `OUT_OF_STOCK`, nothing charged) |
| Saga compensation | A declined payment or a cancellation (`OrderCancelled`) puts the stock back (`InventoryService.release`). Cancelling a paid order, or a payment arriving after cancellation, also sends `RefundRequested`; payment-service refunds. `OrderService.cancel` / `paymentCompleted` |
| Idempotent consumer | `PaymentService.payFor` + unique `order_id` in `V1__payment.sql`; one `reservation` row per order in inventory, so events about an order are safe to repeat or arrive in any order (a cancellation before the reservation leaves a marker, and nothing is reserved later); `refund` and the order status changes are safe to repeat |
| Pessimistic locking | `JdbcStock.lockAvailable` locks the stock rows (`FOR UPDATE`, in product id order to avoid deadlocks), so two orders cannot both take the last item |
| Optimistic concurrency | `JdbcOrders.transition` only saves if the status is unchanged, so a cancel and a payment arriving together cannot overwrite each other |
| Dead-letter topics | A message that still fails after two retries moves to `<topic>-dlt`; `KafkaConfig` in order, inventory and payment |
| Sign-in and JWT resource servers | Keycloak realm `infra/keycloak/shop-realm.json`; `SecurityConfig` in catalog, order and inventory |
| Role-based access | Keycloak realm role `admin`, read from the token's `realm_access.roles` by `KeycloakRealmRoles`; only admins may add or change products (`SecurityConfig` in catalog) and restock (`SecurityConfig` in inventory) |
| Idempotent order placement | An order stores an idempotency key, unique per customer (`V5__order_idempotency_key.sql`): the client's `Idempotency-Key` header on `POST /orders`, or the cart's id at checkout. Repeating a request returns the order already placed; `OrderService.place`, `CartService.checkout` |
| Cache-style store for short-lived data | The cart lives in Redis (`RedisCarts`, one hash per customer, removed after 30 days unchanged); orders stay in PostgreSQL |
| Browser sign-in (OpenID Connect, PKCE) | `storefront/auth.js`: authorization code flow with PKCE for the public Keycloak client `shop-web`, written with the browser's Web Crypto API (no library). Keycloak only returns to `http://localhost:8000/` |
| Single-page app behind the gateway | `storefront/` is served by nginx through Kong on the same origin as the API, so no CORS rules are needed; nginx sends a strict Content-Security-Policy (`infra/nginx/storefront.conf`) and the pages are built without `innerHTML` |
| Token relay between services | `order-service` `CatalogClient` |
| API gateway as single entry point | `infra/kong/kong.yml` |
| Timeout, retry, circuit breaker | `order-service` `CatalogClient` + `resilience4j` / `spring.http.client` in its `application.yml` |
| Observability (traces, metrics, logs) | OpenTelemetry Java agent in `Dockerfile`, Grafana LGTM container |
| Unit tests and Testcontainers integration tests | `*Test` and `*IntegrationTest` in each service |

## Mapping to the reference architecture

The full target architecture, with every layer and one leading tool per layer, is drawn for two
deployments: [`docs/target-architecture-on-prem.pptx`](docs/target-architecture-on-prem.pptx)
(self-hosted, which this repository follows) and
[`docs/target-architecture-aws.pptx`](docs/target-architecture-aws.pptx) (on AWS). The table
shows which parts of it MyShoppingCart runs today.

| Reference architecture | In MyShoppingCart | Notes |
|---|---|---|
| Kong Gateway | Included, DB-less, routing only | Rate limiting, plugins and gateway-side JWT checks are not configured; services validate JWTs themselves |
| Keycloak | Included, dev mode, realm imported at start | One realm, one public client, a test customer and a test admin |
| Spring Boot 3 / Java 21 services | Catalog, Order, Inventory, Payment | Browsing, ordering, stock and payment |
| Apache Kafka | Included, single KRaft node | No replication on one laptop |
| PostgreSQL per service | Included: one database per service on one server | One server and one test user to save memory; no service reads another's database |
| Redis | Included, single node, no persistence settings | Holds carts only; the cart is part of order-service rather than its own service |
| OpenTelemetry + Grafana | Included via the `grafana/otel-lgtm` all-in-one image | Stands in for separate Tempo, Loki, Prometheus and Grafana installs |
| GitHub Actions | Included: `.github/workflows/ci.yml` builds and tests | No image publishing or deployment |
| NGINX, Spring Cloud Gateway | Not included | Kong is the single entry point |
| Search, Customer services | Not yet built | OpenSearch comes with search |
| Recommender (AI) | Not yet built | Needs a model and product data |
| OpenSearch, Ceph | Not yet built | Needed with the search and product media features |
| HashiCorp Vault | Not included | Test credentials come from a local `.env` file instead |
| OpenShift, Istio | Not included | Docker Compose replaces the cluster; no mesh, no mTLS between services |
| Harbor, Argo CD, Terraform, SonarQube | Not included | Images are built locally; there is nothing to deploy to |

## Current limitations

- An order holds up to 20 products. Prices are looked up one product at a time at checkout.
- Stock is reserved when the order is placed, before payment.
- A product taken off sale stays in carts until checkout, which then fails with `400` for that
  product. Off-sale products keep their stock; nothing is sold from it.
- Catalog and inventory check roles; order and payment accept any signed-in user.
- The storefront keeps its tokens in the tab's `sessionStorage`; closing the tab signs you out of
  the shop but not of Keycloak. It shows order status by asking again every 2 seconds while an
  order is processing, not by push.
- The storefront only works at `http://localhost:8000/`, the one address Keycloak returns to.
- Orders belong to a Keycloak user id. The test users have fixed ids in the realm file, so their
  orders survive restarts; orders placed before the ids were fixed no longer show up.
- If you started the stack before inventory was added, its database volume has no `inventory`
  database. Run `docker compose down -v` once. If your `.env` predates the admin user, copy the
  `SHOP_ADMIN` lines from `.env.example` into it.
- Checkout places the order, then empties the cart. If Redis fails between the two, the order
  stands and the cart is emptied the next time the customer views, changes or checks out the
  cart; checking out again returns the same order.
- The `Idempotency-Key` header on `POST /orders` is optional. Without it, repeating the request
  places another order. Keys never expire.
- There is no payment provider. A charge above `payment.decline-above` (1000.00, in payment-service's
  `application.yml`) is declined; anything else succeeds. A refund only changes the payment's status.
- Nothing reads the dead-letter topics yet; inspect them with Kafka's console consumer.
- The outbox is polled every 500 ms. Production designs often use change data capture (Debezium) instead.
- Delivery is at least once on every topic. Each consumer is idempotent, so a repeated event changes nothing.
- Service-to-service traffic inside the Docker network is plain HTTP.
- All credentials are generated test values. Do not reuse them anywhere.

## Run locally

Needs Docker Desktop (the stack uses about 4 GB of memory) and a shell with `curl` — Git Bash on Windows works.

```bash
cp .env.example .env
```

```bash
docker compose up -d --build
```

The first run downloads images and builds the services, which takes several minutes. Then:

```bash
bash scripts/smoke.sh
```

The script signs in as the test user and checks these journeys through Kong:

- an order that is paid, one above the card limit that is declined, and a paid order that is
  cancelled and refunded, checking after each that the stock went down or came back;
- the list of the customer's orders, a two-product cart checked out as one order, and an order
  sent twice with one `Idempotency-Key`;
- an order for more than is in stock;
- as the admin: a restock the customer is refused, and a product that is added, restocked,
  repriced and taken off sale;
- the storefront: served with its Content-Security-Policy, and Keycloak only sending a sign-in
  back to it.

### Shop in the browser

Open http://localhost:8000/ and sign in with `SHOP_USER` and `SHOP_USER_PASSWORD` from `.env`.
Browse the products and their stock, add some to the cart, check out, and watch the order move
from Processing to Paid on **My orders**, where you can also cancel it. Sign in as `SHOP_ADMIN`
instead to get the **Admin** page: add products, change names and prices, restock, and take
products off sale or put them back.

### Use the API directly

The main steps by hand:

```bash
source .env
```

```bash
TOKEN=$(curl -s -X POST http://localhost:8000/realms/shop/protocol/openid-connect/token -d grant_type=password -d client_id=shop-cli -d "username=$SHOP_USER" --data-urlencode "password=$SHOP_USER_PASSWORD" | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
```

```bash
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8000/products
```

How many of each product are left (`/stock/<productId>` for one):

```bash
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8000/stock
```

Put products in your cart. `PUT` sets the quantity (1 to 99), so sending it again changes the
quantity instead of adding a second line:

```bash
curl -s -X PUT http://localhost:8000/cart/items/33333333-3333-3333-3333-333333333333 -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"quantity":2}'
```

```bash
curl -s -X PUT http://localhost:8000/cart/items/22222222-2222-2222-2222-222222222222 -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"quantity":1}'
```

`GET /cart` shows the cart, `DELETE /cart/items/<productId>` removes one product and
`DELETE /cart` empties it. Check out, which places one order for the whole cart and empties it:

```bash
curl -s -X POST -H "Authorization: Bearer $TOKEN" http://localhost:8000/cart/checkout
```

An order can also be placed directly, without the cart. Send a new `Idempotency-Key` (any
unique text up to 100 characters) with each order: if the request times out, sending it again
with the same key returns the order already placed instead of placing a second one. The same key
with different items returns `422`.

```bash
curl -s -X POST http://localhost:8000/orders -H "Authorization: Bearer $TOKEN" -H "Idempotency-Key: $(uuidgen 2>/dev/null || date +%s%N)" -H "Content-Type: application/json" -d '{"items":[{"productId":"33333333-3333-3333-3333-333333333333","quantity":2}]}'
```

Either way the response contains the order `id`, its `lines` with the price of each product,
the `total` and status `PENDING`. Inventory then reserves the stock and payment charges the
order. A moment later:

```bash
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8000/orders/<id>
```

shows status `PAID`. All of your orders, newest first (`?limit=` takes 1 to 100, default 20):

```bash
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8000/orders
```

To cancel an order, which puts its stock back and refunds its payment:

```bash
curl -s -X POST -H "Authorization: Bearer $TOKEN" http://localhost:8000/orders/<id>/cancel
```

An order of 5 Espresso Machines (`11111111-1111-1111-1111-111111111111`, total 1245.00) ends as
`PAYMENT_DECLINED`, and the machines go back in stock. An order for more Espresso Machines than
`/stock` shows (25 at the start) ends as `OUT_OF_STOCK` without being charged. A declined, out of
stock or already cancelled order cannot be cancelled (`409`). Without the `Authorization` header
every call returns `401`.

### Manage products as an admin

The test admin (`SHOP_ADMIN` in `.env`) has the Keycloak realm role `admin`. Sign in as the admin:

```bash
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8000/realms/shop/protocol/openid-connect/token -d grant_type=password -d client_id=shop-cli -d "username=$SHOP_ADMIN" --data-urlencode "password=$SHOP_ADMIN_PASSWORD" | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
```

Add a product (a name up to 100 characters, a price above 0 with at most 2 decimals). The
response contains its `id`; a moment later `/stock` lists it with 0 in stock:

```bash
curl -s -X POST http://localhost:8000/products -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" -d '{"name":"Milk Frother","price":39.90}'
```

Restock it (1 to 10000 at a time); the response shows the new stock level:

```bash
curl -s -X POST http://localhost:8000/stock/<id>/restock -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" -d '{"quantity":10}'
```

Change its name or price, or take it off sale, with only the fields you want to change. A new
price applies to orders placed from then on; an off-sale product disappears from `/products`
and ordering it returns `400`. `{"onSale":true}` puts it back.

```bash
curl -s -X PATCH http://localhost:8000/products/<id> -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" -d '{"price":34.90}'
```

```bash
curl -s -X PATCH http://localhost:8000/products/<id> -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" -d '{"onSale":false}'
```

The same calls with the customer's `$TOKEN` return `403`; an unknown product returns `404`.

### See the traces

Open http://localhost:3000 (Grafana, no sign-in needed), go to **Explore**, choose the
**Tempo** data source, switch to **TraceQL** and run one of these. An order shows up as two traces:

- `{name="POST /orders"}` – the HTTP request: order-service, its call to catalog-service, and
  the inserts into `orders` and `outbox`.
- `{name="order-created publish"}` – the asynchronous leg: the outbox relay publishing to Kafka
  and inventory-service reserving the stock. Inventory and payment publish through their own
  outboxes, so payment and the order becoming `PAID` appear as further traces
  (`{name="stock-reserved publish"}`, `{name="payment-completed publish"}`).

They are separate traces because the outbox table does not carry the trace context from the
request to the relay. The **Loki** data source has the logs of all four services and
**Prometheus** has their JVM, HTTP and Kafka metrics.

### Try the circuit breaker

```bash
docker compose stop catalog-service
```

Place orders again (the `curl -X POST .../orders` above). The first one returns `503` after a few seconds
of timeouts and retries. That opens the circuit, so for the next ten seconds a `503` comes back
at once, without calling the catalog; after that the breaker lets a few trial calls through.
Start the catalog again and orders work once it is up:

```bash
docker compose start catalog-service
```

### Stop

```bash
docker compose down
```

Orders, payments and Kafka's topics are kept in the Docker volumes `postgres-data` and
`kafka-data`, so the next `docker compose up -d` carries on where you stopped. Carts live in Redis
without a volume and are emptied by `down`. To start again from nothing:

```bash
docker compose down -v
```

## Build and test without the stack

Needs Java 21, Maven and a running Docker (for Testcontainers).

```bash
mvn verify
```

This runs the unit tests and the integration tests against real PostgreSQL, Kafka and Redis containers:

- `CatalogIntegrationTest`: an admin adds a product that is announced on `product-created` and
  listed, changes its price, takes it off sale and back; bad names and prices and unknown products
  are refused. `ProductControllerTest` checks that customers cannot add or change products.
- `OrderFlowIntegrationTest`: paid, declined, out of stock, cancel with refund and the
  cancellation sent to inventory, payment after cancellation,
  only the owner can cancel, each customer lists only their own orders, a two-product cart checked
  out as one order, a cart that could not be emptied not being ordered twice, a repeated
  `Idempotency-Key` placing one order and refusing different items, the cart refusing
  unknown products and bad quantities, unreadable message to
  the dead-letter topic.
- `InventoryFlowIntegrationTest`: a duplicated `OrderCreated` reserves stock once and is passed on
  to payment, an order for more than is in stock reserves nothing, a declined payment puts the
  stock back once, a cancellation arriving before the order means nothing is reserved, `/stock`
  needs a token, an admin restocks while a customer gets `403`, bad quantities and unknown
  products are refused, a new product starts at 0 and a repeated `ProductCreated` keeps its
  stock, unreadable message to the dead-letter topic.
- `PaymentFlowIntegrationTest`: a duplicated `StockReserved` is paid once, a charge above the limit
  is declined, `RefundRequested` refunds, unreadable message to the dead-letter topic.

Each service is tested on its own; the full round trip through all of them is what
`scripts/smoke.sh` checks against the running stack.

## Run it on GitHub

GitHub does not host long-running back ends, so there are two options:

- **Codespaces** – `.devcontainer/devcontainer.json` gives a Codespace Java 21, Maven and
  Docker. Choose a 4-core machine, then run the same `docker compose` and `scripts/smoke.sh`
  commands in the Codespace terminal. Call the API from that terminal: tokens are issued for
  `http://localhost:8000`, so a token fetched through a forwarded browser URL is rejected.
- **Actions** – `.github/workflows/ci.yml` runs `mvn verify` on every push and pull request.
