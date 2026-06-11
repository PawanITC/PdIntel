# Dependency Reasoning — plany-payment-service

## pom.xml — Dependency Decisions

| Dependency | Why |
|---|---|
| `spring-boot-starter-web` | REST endpoints |
| `spring-boot-starter-actuator` | `/actuator/health`, `/actuator/prometheus` — first BDD scenario tests this |
| `spring-boot-starter-data-jpa` | ORM for all 6 tables |
| `spring-boot-starter-security` | Filter chain scaffold now, swap to Cognito later without code change |
| `spring-boot-starter-validation` | `@Valid` on request bodies |
| `flyway-core` + `flyway-database-postgresql` | Spring Boot 3.x + Flyway 10.x requires both separate artifacts |
| `postgresql` | JDBC driver — runtime scope only (never compile-time) |
| `software.amazon.awssdk:kinesis` | AWS Kinesis producer (PutRecord) and consumer (GetRecords) |
| `software.amazon.awssdk:sts` | Required by DefaultCredentialsProvider for IRSA credential chain |
| `stripe-java:26.7.0` | Official Stripe SDK — latest stable on Maven Central |
| `springdoc-openapi-starter-webmvc-ui` | OpenAPI 3.x + Swagger UI auto-generated from annotations |
| `micrometer-registry-prometheus` | Prometheus scrape endpoint at `/actuator/prometheus` |
| `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp` | Distributed tracing — bridges Micrometer Observation API to OpenTelemetry |
| `cucumber-java` + `cucumber-spring` + `cucumber-junit-platform-engine` | BDD step definitions wired to Spring context, run via JUnit 5 |
| `testcontainers:localstack` | LocalStack container for local Kinesis emulation in integration tests |
| `spring-boot-testcontainers` + `testcontainers:postgresql` | Real Postgres in integration tests via `@ServiceConnection` — no mocks |
| `wiremock` | Mock Stripe HTTP API in unit/integration tests |

## Logging Decision

**No `logback-spring.xml`. No `logstash-logback-encoder` dependency.**

Spring Boot 3.4 ships native structured logging. Two lines in `application.yml`:

```yaml
logging:
  structured:
    format:
      console: logstash
      file: logstash
```

This produces JSON logs compatible with ELK stack. MDC fields (`traceId`, `councilId`, `userId`) are included automatically.

## BDD Wiring — HealthSteps.java and CucumberRunner.java

### HealthSteps.java
The bridge between plain-English Gherkin sentences in `.feature` files and real Java/HTTP code.

| Element | Role |
|---|---|
| `@CucumberContextConfiguration` | Marks this class as the Spring context anchor for all Cucumber step classes — must appear on exactly one class |
| `@SpringBootTest(webEnvironment = RANDOM_PORT)` | Starts a real Spring Boot server on a random free port |
| `@ActiveProfiles("test")` | Activates `application-test.yml` overrides |
| `@Testcontainers` + `@Container` + `@ServiceConnection` | Spins real Postgres container, auto-overrides datasource config |
| `@Import(KinesisMockConfig.class)` | Replaces real KinesisAsyncClient with in-memory stub; captures PutRecord calls for BDD assertions |
| `TestRestTemplate` | Makes real HTTP calls against the running test server — pre-configured to use the random port |
| `{int}` / `{string}` in step annotations | Cucumber type expressions — extract values from feature file sentences and pass as method parameters |

### CucumberRunner.java
The entry point that tells JUnit 5 to discover and run Cucumber tests. Contains zero logic — purely wiring.

| Element | Role |
|---|---|
| `@Suite` | JUnit 5 Platform Suite — groups tests under a named suite |
| `@IncludeEngines("cucumber")` | Tells JUnit Platform to use the Cucumber engine |
| `@ConfigurationParameter(GLUE_PROPERTY_NAME)` | Points Cucumber to the step definitions package |
| `@ConfigurationParameter(FEATURES_PROPERTY_NAME)` | Points Cucumber to the feature files directory |
| `@ConfigurationParameter(PLUGIN_PROPERTY_NAME)` | Configures output — pretty console + JSON report for CI |

Without `CucumberRunner.java`, `mvn test` finds no Cucumber tests to run.

## Money Storage Decision

**Database:** `BIGINT` pence (not `NUMERIC`, not `FLOAT`, not PostgreSQL `money`)
**Java:** `Long` for storage/Stripe API calls, `BigDecimal` for mid-calculation arithmetic only (VAT, display formatting)

| Layer | Type | Example |
|---|---|---|
| PostgreSQL column | `BIGINT` | `1599` (= £15.99) |
| Java entity field | `Long` | `1599L` |
| Stripe API | `Long` pence | `1599` — Stripe native format, zero conversion |
| Display / VAT calc | `BigDecimal` | `BigDecimal.valueOf(1599).movePointLeft(2)` → `"15.99"` |

**Why not `NUMERIC(19,2)`:** Requires converting to/from pence on every Stripe API call. Stripe speaks pence natively — storing as pence means zero conversion.
**Why not `BigDecimal` in DB:** `BigDecimal` is a Java type, not a DB type. It maps to `NUMERIC` in Postgres. Same conversion overhead problem as above.
**Why not `FLOAT`/`DOUBLE`:** Floating point rounding errors in financial data — never acceptable.
**Why not PostgreSQL `money`:** Locale-dependent, poor portability, Postgres docs advise against it.
**VAT caveat:** VAT intermediate calculations (e.g. 20% of £7 = £1.40) may produce fractions. Rule: do `BIGINT` arithmetic in pence, round **once** at the final step using `BigDecimal.setScale(0, RoundingMode.HALF_UP)`.

## Kinesis — AWS SDK v2, LocalStack for local dev

**Stream:** `pd-payment-kinesis-euw2` (eu-west-2, single shard for non-prod)

| Decision | Why |
|---|---|
| AWS SDK v2 (`KinesisAsyncClient`) | Non-blocking I/O; `putRecord().join()` gives synchronous confirmation when needed |
| `DefaultCredentialsProvider` | Picks up IRSA pod credentials automatically in EKS; falls back to env vars / `~/.aws/credentials` locally |
| `KINESIS_ENDPOINT` env var override | When set, used as endpoint override — points to LocalStack in local/docker dev, empty for real AWS |
| LocalStack in docker-compose (replaces Kafka) | Emulates Kinesis locally without a real AWS account; stream auto-created on container start |
| Single-shard consumer in `KinesisConsumerService` | Sufficient for current volume; can be split to per-shard threads when shards > 1 |
| Partition key = `councilId` | Preserves per-council ordering on the shard (same guarantee Kafka gave via partition key) |
| `KinesisMockConfig` in tests | Pure in-memory stub for `KinesisAsyncClient` — no LocalStack or Docker required during `mvn test` |

## Configuration Decision — YAML over .properties, no Ansible

**Use `application.yml` (not `.properties`).**

- Hierarchical structure is more readable for nested Spring config
- Multi-document YAML (`---`) supports profile blocks in one file if needed
- Native Spring Boot support — no extra dependency

**File structure per environment:**

| File | Purpose |
|---|---|
| `application.yml` | Shared defaults across all environments |
| `application-dev.yml` | Local dev overrides (local Postgres defaults) |
| `application-staging.yml` | Staging overrides |
| `application-prod.yml` | Prod — mostly empty, secrets supplied by AWS Secrets Manager via env vars |
| `application-test.yml` | Test profile (mock Kinesis client, Testcontainers Postgres) |

Active profile set via: `SPRING_PROFILES_ACTIVE=prod`

**No Ansible.** Deployment is ECS (Fargate) — env vars injected at ECS task definition level via AWS Secrets Manager. GitHub Actions handles CI/CD. Ansible adds no value for containerised ECS deploys.
