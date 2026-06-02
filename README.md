# plany-payment-service

Stripe payment integration service for [Plany.co.uk](https://plany.co.uk) — subscription management, webhooks, and billing.

## Tech Stack

| Layer | Technology |
|---|---|
| Runtime | Java 21 / Spring Boot 3.4.x |
| Build | Maven |
| Database | PostgreSQL 16 (AWS RDS Multi-AZ in prod) |
| Migrations | Flyway 10.x |
| Messaging | Apache Kafka 3.9 KRaft (AWS MSK in prod) |
| Payments | Stripe (Billing, Tax, Radar, Billing Portal) |
| Auth | Mock JWT (Phase 1) → AWS Cognito RS256 (later phase) |
| API Docs | springdoc-openapi 2.x (OpenAPI 3.x + Swagger UI) |
| Observability | Spring Boot Actuator, Micrometer, Prometheus, OpenTelemetry |
| Testing | Cucumber BDD, JUnit 5, Testcontainers, WireMock, EmbeddedKafka |

---

## Prerequisites

- Java 21
- Maven 3.9+
- Docker + Docker Compose
- A Stripe test account (get keys from [Stripe Dashboard](https://dashboard.stripe.com/test/apikeys))

---

## Local Development

### 1. Clone and configure secrets

```bash
cp .env.example .env
```

Edit `.env` and fill in your Stripe test keys:

```env
STRIPE_API_KEY=sk_test_your_secret_key_here
STRIPE_WEBHOOK_SECRET=whsec_your_webhook_secret_here
```

> **.env is gitignored — never commit it.**

---

### 2. Start infrastructure (Postgres + Kafka)

```bash
docker-compose up -d
```

This starts:
- **PostgreSQL 16** on `localhost:5432` (database: `plany_dev`, user/pass: `plany/plany`)
- **Apache Kafka 3.9 (KRaft)** on `localhost:29092`

Flyway migrations run automatically on app startup.

To stop:
```bash
docker-compose down
```

To stop and wipe all data (including Postgres volume):
```bash
docker-compose down -v
```

---

### 3. Run the application

From your IDE, run `PlanyPaymentApplication` with:
```
SPRING_PROFILES_ACTIVE=dev
```

Or via Maven:
```bash
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run
```

The app starts on `http://localhost:8080`.

---

### 4. Run the full stack (app + infra in Docker)

```bash
docker-compose --profile app up
```

This builds the app image and starts Postgres + Kafka + app together.

---

## Useful Endpoints

| Endpoint | Description |
|---|---|
| `GET /actuator/health` | Health check |
| `GET /actuator/prometheus` | Prometheus metrics scrape |
| `GET /swagger-ui.html` | Swagger UI — interactive API docs |
| `GET /v3/api-docs` | Raw OpenAPI 3.x spec (JSON) |

---

## Running Tests

```bash
# All tests (unit + integration + BDD)
mvn test

# BDD smoke tests only
mvn test -Dcucumber.filter.tags="@smoke"

# BDD health tests only
mvn test -Dcucumber.filter.tags="@health"
```

Cucumber HTML report is generated at:
```
target/cucumber-reports/cucumber.html
```

---

## Environment Profiles

| Profile | Purpose | How to activate |
|---|---|---|
| `dev` | Local development — defaults to `localhost` Postgres + Kafka | `SPRING_PROFILES_ACTIVE=dev` |
| `staging` | Staging — all config via env vars, no defaults | `SPRING_PROFILES_ACTIVE=staging` |
| `prod` | Production — minimal config, 10% trace sampling, WARN logs | `SPRING_PROFILES_ACTIVE=prod` |
| `test` | Test runs — EmbeddedKafka + Testcontainers Postgres | Auto-activated by `@ActiveProfiles("test")` |

---

## Database Migrations

Migrations live in `src/main/resources/db/migration/` and run automatically on startup via Flyway.

| Migration | Table |
|---|---|
| `V1__create_users.sql` | `users` |
| `V2__create_subscriptions.sql` | `subscriptions` |
| `V3__create_processed_stripe_events.sql` | `processed_stripe_events` |
| `V4__create_stripe_event_outbox.sql` | `stripe_event_outbox` |
| `V5__create_audit_log.sql` | `audit_log` |
| `V6__create_invoices.sql` | `invoices` |
| `V7__create_user_councils.sql` | `user_councils` |

To create a new migration, add a file named `V{n}__{description}.sql` — never modify an existing migration file.

---

## Kafka Topics

| Topic | Purpose | Partition Key |
|---|---|---|
| `plany.stripe.webhook-raw.v1` | Raw Stripe webhook events from outbox relay | `councilId` |

---

## Project Structure

```
src/
├── main/
│   ├── java/uk/co/pdintel/payment/
│   │   ├── PlanyPaymentApplication.java
│   │   └── config/
│   │       └── OpenApiConfig.java
│   └── resources/
│       ├── application.yml
│       ├── application-dev.yml
│       ├── application-staging.yml
│       ├── application-prod.yml
│       └── db/migration/
│           ├── V1__create_users.sql
│           ├── V2__create_subscriptions.sql
│           ├── V3__create_processed_stripe_events.sql
│           ├── V4__create_stripe_event_outbox.sql
│           ├── V5__create_audit_log.sql
│           └── V6__create_invoices.sql
└── test/
    ├── java/uk/co/pdintel/payment/
    │   ├── PlanyPaymentApplicationTests.java
    │   └── bdd/
    │       ├── CucumberRunner.java
    │       └── HealthSteps.java
    └── resources/
        ├── application-test.yml
        └── features/
            └── health.feature
```

---

## Key Design Decisions

- **Webhook handler returns HTTP 200 in <30ms** — never calls downstream services synchronously
- **Card data never enters our servers** — Stripe Payment Element iframe handles all card input (PCI SAQ A)
- **`councilId` is always the Kafka partition key** — guarantees ordering of events per council
- **No PII in Kafka payloads** — only IDs and status values
- **Money stored as `BIGINT` pence** — aligns with Stripe's API format, no floating point rounding
- **Flyway owns the schema** — Hibernate is set to `validate` only, never `update` or `create-drop`
- **All secrets via env vars** — no hardcoded keys in any committed file; prod secrets via AWS Secrets Manager

---

## Stripe Test Keys

Test keys are stored in `.env` (gitignored). See `.env.example` for the required variables.

- Stripe Dashboard: [https://dashboard.stripe.com/test/apikeys](https://dashboard.stripe.com/test/apikeys)
- Webhook secret: obtained from the Stripe CLI or Dashboard after registering the webhook endpoint

---

## Documentation

Full dependency and architecture decisions are recorded in `docs/dependency-reasoning.md`.
