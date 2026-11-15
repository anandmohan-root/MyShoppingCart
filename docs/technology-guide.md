# Technology guide

What each tool in MyShoppingCart does, where it is wired in, why it was chosen, and what to
change when you take the idea to a real project.

## How one purchase flows through the stack

```
Browser (React)  ──HTTPS──▶  Kong (gateway)  ──▶  order-service ──▶ PostgreSQL (order + outbox row, one transaction)
   ▲   sign-in via Keycloak (PKCE)                      │
   │                                                    ▼  outbox relay publishes
   │                                        Kafka: order-created
   │                                                    ▼
   │                                inventory-service reserves stock ─▶ stock-reserved
   │                                                    ▼
   │                                payment-service charges ─▶ payment-completed / declined
   │                                                    ▼
   └──── live status (SSE) ◀── order-service updates the order; notification-service emails (Mailpit)
```

Every service sends traces, metrics and logs to Grafana through OpenTelemetry.

## 1. Front end

| Technology | Where | Why | In a real project |
|---|---|---|---|
| **React 19** | `storefront/src/` (`App.jsx`, `pages/*.jsx`) | Components and state for a single-page shop; React writes data as text, so product names cannot inject script | Add a router (React Router) and a data-fetching library (TanStack Query) once pages grow |
| **Vite** | `storefront/package.json`, `npm run build` | Fast build into small static files (about 75 KB gzipped) | Same; add TypeScript and ESLint |
| **Inline SVG icons** | `storefront/src/icons.jsx` | About 40 icons with no library or extra request | Fine to keep, or use `lucide-react` |
| **OIDC + PKCE in the browser** | `storefront/src/auth.js` | Sign-in with Keycloak without a client secret, using the Web Crypto API | Use a maintained library (`oidc-client-ts`) and consider a backend-for-frontend so tokens never reach the browser |
| **Server-sent events** | `api.js` `followOrders`, order-service `OrderStatusStream` | The server pushes order status changes; simpler than WebSockets for one-way updates | Use a shared broker (Redis pub/sub) when there is more than one order-service instance |
| **nginx** | `storefront/Dockerfile`, `infra/nginx/storefront.conf` | Serves the built files with a strict Content-Security-Policy | Or a CDN (CloudFront) in front of object storage |
| **Playwright** | `e2e/tests/storefront.spec.js`, `scripts/browser-tests.sh` | Drives a real browser through the whole stack: buy, cancel, admin actions, live updates | Keep these few journey tests; run them on every merge |

## 2. Edge and security

| Technology | Where | Why | In a real project |
|---|---|---|---|
| **Kong 3.9** (DB-less) | `infra/kong/kong.yml` | One entry point at port 8000: routes `/products`, `/orders`, `/cart`, `/stock`, `/realms`, `/mail`, `/`; rate limit (50/s, 1000/min) and 1 MB body limit | Run with a database or Kong Konnect, add TLS, WAF and a shared rate-limit store (Redis) |
| **Keycloak 26** | `infra/keycloak/shop-realm.json` | Users, the `admin` role, clients `shop-web` (browser, PKCE) and `shop-cli` (tests); brute-force lockout | Production mode with a database, HA, real signing keys, social or corporate login |
| **Spring Security resource server** | each service's `SecurityConfig`, `KeycloakRealmRoles` | Services validate the JWT themselves and map `realm_access.roles` to `ROLE_admin` | Same pattern; never trust the gateway alone |

## 3. Services

| Technology | Where | Why | In a real project |
|---|---|---|---|
| **Java 21 + Spring Boot 3.5** | `*-service/` | Mature, well-known stack for business services | Same |
| **Hexagonal layout** | `domain/`, `application/` (ports), `adapter/in`, `adapter/out` in each service | Business rules do not depend on web, database or Kafka code, so they are easy to test and change | Keep it; it pays off as services grow |
| **Spring JDBC (`JdbcClient`)** | `adapter/out/persistence/Jdbc*.java` | Plain, visible SQL; no ORM surprises | JPA is fine for CRUD-heavy services |
| **Flyway** | `src/main/resources/db/migration/V*.sql` | Versioned schema changes, applied at start-up | Same; run migrations as a separate deploy step |
| **Resilience4j** (timeout, retry, circuit breaker) | order-service `CatalogClient`, its `application.yml` | Checkout fails fast with a clear error when catalog is down, instead of hanging | Apply the same to every remote call |
| **Idempotency keys** | `POST /orders` `Idempotency-Key`, cart checkout | A repeated request (double click, retry) places one order, not two | Expire keys after a set time |

## 4. Data and messaging

| Technology | Where | Why | In a real project |
|---|---|---|---|
| **PostgreSQL 17** | one database per service (`infra/postgres/init.sql`) | Each service owns its data; no shared tables | Managed service (RDS / Aurora), backups, read replicas |
| **Redis 7.4** | order-service cart (`cart:<customerId>` hash, 30-day expiry) | Fast, short-lived data that may be lost without harm | Managed Redis with replication |
| **Apache Kafka 3.9** (KRaft) | topics `order-created`, `stock-reserved`, `payment-*`, `order-cancelled`, `refund-requested`, `product-created` | Services react to events instead of calling each other, so one can be down without stopping the others | Managed Kafka (MSK / Confluent), schema registry (Avro / Protobuf), 3+ brokers |
| **Transactional outbox** | `outbox` table + `OutboxRelay` in catalog, order, inventory, payment | The database change and the event are saved in one transaction, so no event is lost or sent for a rolled-back change | Use change data capture (Debezium) instead of polling |
| **Saga (choreography)** | order → inventory → payment, with compensations (release stock, refund) | A purchase spans services with no distributed transaction | Add an orchestrator (Temporal, Camunda) when flows get long |
| **Dead-letter topics** | `<topic>-dlt`, `DefaultErrorHandler` with back-off | A bad message is parked after retries instead of blocking the queue | Alert on DLT traffic and add a replay tool |
| **Idempotent consumers** | unique keys per order and event kind | Kafka delivers at least once; repeats change nothing | Same |

## 5. Notifications

| Technology | Where | Why | In a real project |
|---|---|---|---|
| **Spring Mail + Mailpit** | notification-service `SmtpMailer`; Mailpit at `/mail/` | Emails for every order step, caught by a test inbox | Amazon SES / SendGrid, templates, unsubscribe handling |

## 6. Observability

| Technology | Where | Why | In a real project |
|---|---|---|---|
| **OpenTelemetry Java agent** | root `Dockerfile` (`-javaagent`), `OTEL_*` in `docker-compose.yml` | Traces, metrics and logs with no code changes; one trace follows a request across services over HTTP (the outbox does not carry the trace on, so the Kafka leg is a separate trace) | Same agent; carry the trace id in outbox events; sample traces in production |
| **Grafana LGTM** (Loki, Grafana, Tempo, Mimir) | `lgtm` container, http://localhost:3000 | One place to search logs, follow traces and graph metrics | Separate managed backends, dashboards and alerts (SLOs) |

## 7. Build, test and delivery

| Technology | Where | Why | In a real project |
|---|---|---|---|
| **Maven** (multi-module) | root `pom.xml` | One build for all services | Same, or Gradle |
| **JUnit 5 + Testcontainers** | `src/test` in each service | Integration tests against real PostgreSQL, Kafka, Redis and Mailpit in containers | Same; add contract tests (Pact) between services |
| **Docker + Docker Compose** | root `Dockerfile`, `docker-compose.yml` | The whole stack starts with one command; same images locally and in CI | Kubernetes / OpenShift with Helm or Kustomize for deployment |
| **Smoke script** | `scripts/smoke.sh` | 13 end-to-end API journeys with `curl`, including gateway limits | Run after every deploy |
| **GitHub Actions** | `.github/workflows/ci.yml` | Builds and tests every push, then runs the full stack with smoke and browser tests | Add image scanning, SonarQube, image push to a registry and Argo CD deploys |

## What this sample leaves out

Secrets are generated test values in `.env` (use Vault or a cloud secret manager). There is no
TLS inside the network, no search engine, no real payment provider and no Kubernetes. The
"Reference architecture mapping" table in the [README](../README.md) lists each of these.
