# MyShoppingCart

MyShoppingCart is a small, runnable slice of the on-premises online shopping platform architecture. It runs
entirely in Docker containers on one laptop and exists to try the main concepts end to end,
not to be a complete shop.

```
            curl / browser
                  |
            Kong :8000  (single entry point, DB-less)
        /realms   /products     /orders
           |          |            |
       Keycloak   catalog-     order-service --REST (timeout, retry, circuit breaker)--> catalog-service
                  service          |
                     |          orders DB (order + outbox row, one transaction)
                 catalog DB        |
                              outbox relay
                                   |  OrderCreated
                                 Kafka
                                   |
                            payment-service (idempotent consumer) -- payments DB
                                   |  PaymentCompleted
                                 Kafka
                                   |
                            order-service  -> order becomes PAID

   All three services export traces, metrics and logs over OpenTelemetry to Grafana :3000
```

## Concepts demonstrated

| Concept | Where to look |
|---|---|
| Hexagonal layout (domain, application ports, adapters in/out) | every service: `domain`, `application`, `adapter/in`, `adapter/out` |
| Database per service | `infra/postgres/init.sql`, one Flyway migration per service |
| Transactional outbox | `order-service` `JdbcOrders.create` (writes order + event) and `OutboxRelay` (publishes) |
| Event-driven saga (choreography) over Kafka | `OrderCreated` -> payment -> `PaymentCompleted` -> order `PAID` |
| Idempotent consumer | `payment-service` `PaymentService.payFor` + unique `order_id` in `V1__payment.sql` |
| Sign-in and JWT resource servers | Keycloak realm `infra/keycloak/shop-realm.json`; `SecurityConfig` in catalog and order |
| Token relay between services | `order-service` `CatalogClient` |
| API gateway as single entry point | `infra/kong/kong.yml` |
| Timeout, retry, circuit breaker | `order-service` `CatalogClient` + `resilience4j` / `spring.http.client` in its `application.yml` |
| Observability (traces, metrics, logs) | OpenTelemetry Java agent in `Dockerfile`, Grafana LGTM container |
| Unit tests and Testcontainers integration tests | `*Test` and `*IntegrationTest` in each service |

## Mapping to the architecture slide

| On the slide | In MyShoppingCart | Why |
|---|---|---|
| Kong Gateway | Included, DB-less, routing only | Rate limiting, plugins and gateway-side JWT checks are left out; services validate JWTs themselves |
| Keycloak | Included, dev mode, realm imported at start | One realm, one public client, one test user |
| Spring Boot 3 / Java 21 services | Catalog, Order, Payment | Enough to show a synchronous call and an asynchronous flow |
| Apache Kafka | Included, single KRaft node | No replication on one laptop |
| PostgreSQL per service | Included: one database per service on one server | One server and one test user to save memory; no service reads another's database |
| OpenTelemetry + Grafana | Included via the `grafana/otel-lgtm` all-in-one image | Stands in for separate Tempo, Loki, Prometheus and Grafana installs |
| GitHub Actions | Included: `.github/workflows/ci.yml` builds and tests | No image publishing or deployment |
| NGINX, Spring Cloud Gateway | Left out | One gateway is enough to show the entry-point idea |
| Search, Cart, Inventory, Customer services | Left out | They repeat the same patterns; Redis and OpenSearch go with them |
| Recommender (AI) | Left out | Needs a model and data; unrelated to the flow shown here |
| Redis, OpenSearch, Ceph | Left out | No cart, search or object storage in this slice |
| HashiCorp Vault | Left out | Test credentials come from a local `.env` file instead |
| OpenShift, Istio | Left out | Docker Compose replaces the cluster; no mesh, no mTLS between services |
| Harbor, Argo CD, Terraform, SonarQube | Left out | Images are built locally; there is nothing to deploy to |

## Simplifications to be aware of

- An order has one product and a quantity. There are no order lines.
- Payment always succeeds; there is no payment provider and so no failure or compensation path in the saga.
- The outbox is polled every 500 ms. Production designs often use change data capture (Debezium) instead.
- Delivery is at least once on both topics. `payment-service` de-duplicates by order id; marking an order paid twice is harmless.
- Service-to-service traffic inside the Docker network is plain HTTP.
- All credentials are generated test values. Do not reuse them anywhere.

## Run it on your laptop

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

The script waits for Keycloak, signs in as the test user, lists products, places an order
through Kong and polls until the order is `PAID`. The same steps by hand:

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

shows status `PAID`. Without the `Authorization` header every call returns `401`.

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

This runs the unit tests and two integration tests against real PostgreSQL and Kafka
containers: `OrderFlowIntegrationTest` (order placed, `OrderCreated` published through the
outbox, order `PAID` after `PaymentCompleted`) and `PaymentFlowIntegrationTest` (a duplicated
`OrderCreated` is paid exactly once). The two services are tested separately; the full
round trip through both is what `scripts/smoke.sh` checks against the running stack.

## Run it on GitHub

GitHub does not host long-running back ends, so there are two options:

- **Codespaces** – `.devcontainer/devcontainer.json` gives a Codespace Java 21, Maven and
  Docker. Choose a 4-core machine, then run the same `docker compose` and `scripts/smoke.sh`
  commands in the Codespace terminal. Call the API from that terminal: tokens are issued for
  `http://localhost:8000`, so a token fetched through a forwarded browser URL is rejected.
- **Actions** – `.github/workflows/ci.yml` runs `mvn verify` on every push and pull request.
