# MyShoppingCart

MyShoppingCart is an online shopping application built on the on-premises shopping platform
architecture: a product catalog, ordering and payment, with sign-in through Keycloak and every
request entering through Kong. It runs in Docker containers and starts with one Docker Compose command.

![MyShoppingCart architecture: Kong routes sign-in to Keycloak and requests to the catalog and order services; order-service publishes OrderCreated to Kafka through an outbox, payment-service records the payment and publishes PaymentCompleted, and every service sends telemetry to Grafana.](docs/architecture.png)

The numbers follow one order from sign-in to `PAID`; the key under the diagram explains each step. To change the
diagram, edit `docs/architecture.pptx` in PowerPoint and export the slide again as `docs/architecture.png`.

## Architecture concepts

| Concept | Where to look |
|---|---|
| Hexagonal layout (domain, application ports, adapters in/out) | every service: `domain`, `application`, `adapter/in`, `adapter/out` |
| Database per service | `infra/postgres/init.sql`, one Flyway migration per service |
| Transactional outbox | Both services: `JdbcOrders` and `OutboxPaymentEvents` write the event with the data, `OutboxRelay` publishes it |
| Event-driven saga (choreography) over Kafka | `OrderCreated` -> payment -> `PaymentCompleted` (order `PAID`) or `PaymentDeclined` (order `PAYMENT_DECLINED`) |
| Saga compensation | Cancelling a paid order, or a payment arriving after cancellation, sends `RefundRequested`; payment-service refunds. `OrderService.cancel` / `paymentCompleted` |
| Idempotent consumer | `payment-service` `PaymentService.payFor` + unique `order_id` in `V1__payment.sql`; `refund` and the order status changes are safe to repeat |
| Optimistic concurrency | `JdbcOrders.transition` only saves if the status is unchanged, so a cancel and a payment arriving together cannot overwrite each other |
| Dead-letter topics | A message that still fails after two retries moves to `<topic>-dlt`; `KafkaConfig` in order and payment |
| Sign-in and JWT resource servers | Keycloak realm `infra/keycloak/shop-realm.json`; `SecurityConfig` in catalog and order |
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
| Keycloak | Included, dev mode, realm imported at start | One realm, one public client, one test user |
| Spring Boot 3 / Java 21 services | Catalog, Order, Payment | Browsing, ordering and payment |
| Apache Kafka | Included, single KRaft node | No replication on one laptop |
| PostgreSQL per service | Included: one database per service on one server | One server and one test user to save memory; no service reads another's database |
| OpenTelemetry + Grafana | Included via the `grafana/otel-lgtm` all-in-one image | Stands in for separate Tempo, Loki, Prometheus and Grafana installs |
| GitHub Actions | Included: `.github/workflows/ci.yml` builds and tests | No image publishing or deployment |
| NGINX, Spring Cloud Gateway | Not included | Kong is the single entry point |
| Search, Cart, Inventory, Customer services | Not yet built | Redis and OpenSearch come with them |
| Recommender (AI) | Not yet built | Needs a model and product data |
| Redis, OpenSearch, Ceph | Not yet built | Needed with the cart, search and product media features |
| HashiCorp Vault | Not included | Test credentials come from a local `.env` file instead |
| OpenShift, Istio | Not included | Docker Compose replaces the cluster; no mesh, no mTLS between services |
| Harbor, Argo CD, Terraform, SonarQube | Not included | Images are built locally; there is nothing to deploy to |

## Current limitations

- An order has one product and a quantity. There are no order lines.
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

The script signs in as the test user and checks three journeys through Kong: an order that is
paid, an order above the card limit that is declined, a paid order that is cancelled and
refunded, and the list of the customer's orders. The main steps by hand:

```bash
source .env
```

```bash
TOKEN=$(curl -s -X POST http://localhost:8000/realms/shop/protocol/openid-connect/token -d grant_type=password -d client_id=shop-cli -d "username=$SHOP_USER" --data-urlencode "password=$SHOP_USER_PASSWORD" | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
```

```bash
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8000/products
```

```bash
curl -s -X POST http://localhost:8000/orders -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"productId":"33333333-3333-3333-3333-333333333333","quantity":2}'
```

The response contains the order `id` with status `PENDING`. A moment later:

```bash
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8000/orders/<id>
```

shows status `PAID`. All of your orders, newest first (`?limit=` takes 1 to 100, default 20):

```bash
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8000/orders
```

To cancel an order, which also refunds its payment:

```bash
curl -s -X POST -H "Authorization: Bearer $TOKEN" http://localhost:8000/orders/<id>/cancel
```

An order of 5 Espresso Machines (`11111111-1111-1111-1111-111111111111`, total 1245.00) ends as
`PAYMENT_DECLINED`. A declined or already cancelled order cannot be cancelled (`409`). Without the
`Authorization` header every call returns `401`.

### See the traces

Open http://localhost:3000 (Grafana, no sign-in needed), go to **Explore**, choose the
**Tempo** data source, switch to **TraceQL** and run one of these. An order shows up as two traces:

- `{name="POST /orders"}` – the HTTP request: order-service, its call to catalog-service, and
  the inserts into `orders` and `outbox`.
- `{name="order-created publish"}` – the asynchronous leg: the outbox relay publishing to Kafka,
  payment-service consuming and paying, and order-service marking the order paid.

They are separate traces because the outbox table does not carry the trace context from the
request to the relay. The **Loki** data source has the logs of all three services and
**Prometheus** has their JVM, HTTP and Kafka metrics.

### Try the circuit breaker

```bash
docker compose stop catalog-service
```

Place orders again (the `curl -X POST` above). The first one returns `503` after a few seconds
of timeouts and retries. That opens the circuit, so for the next ten seconds a `503` comes back
at once, without calling the catalog; after that the breaker lets a few trial calls through.
Start the catalog again and orders work once it is up:

```bash
docker compose start catalog-service
```

### Stop

```bash
docker compose down -v
```

`-v` also removes the database and Kafka data.

## Build and test without the stack

Needs Java 21, Maven and a running Docker (for Testcontainers).

```bash
mvn verify
```

This runs the unit tests and the integration tests against real PostgreSQL and Kafka containers:

- `OrderFlowIntegrationTest`: paid, declined, cancel with refund, payment after cancellation,
  only the owner can cancel, each customer lists only their own orders, unreadable message to
  the dead-letter topic.
- `PaymentFlowIntegrationTest`: a duplicated `OrderCreated` is paid once, a charge above the limit
  is declined, `RefundRequested` refunds, unreadable message to the dead-letter topic.

The two services are tested separately; the full round trip through both is what
`scripts/smoke.sh` checks against the running stack.

## Run it on GitHub

GitHub does not host long-running back ends, so there are two options:

- **Codespaces** – `.devcontainer/devcontainer.json` gives a Codespace Java 21, Maven and
  Docker. Choose a 4-core machine, then run the same `docker compose` and `scripts/smoke.sh`
  commands in the Codespace terminal. Call the API from that terminal: tokens are issued for
  `http://localhost:8000`, so a token fetched through a forwarded browser URL is rejected.
- **Actions** – `.github/workflows/ci.yml` runs `mvn verify` on every push and pull request.
