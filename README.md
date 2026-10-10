# Payment Orchestrator

A Java 17 / Spring Boot demo of payment orchestration across deterministic provider stubs. The project focuses on safe handling of timeouts and ambiguous provider outcomes, idempotent requests, balanced append-only ledger entries, and durable asynchronous work.

The provider adapters are simulations. This project does not process real card data or connect to real payment service providers.

## Implemented flows

- `POST /api/v1/payments` creates a payment using merchant-scoped `Idempotency-Key` handling.
- Every provider operation is persisted before its charge call. A timeout or ambiguous result becomes `UNKNOWN`; it is resolved by querying the same provider operation. Failover is allowed only after a definitive no-charge result.
- Successful payments write balanced `PROVIDER_CLEARING` and `MERCHANT_BALANCE` ledger entries. Full refunds append the opposite ledger entries and do not call a provider.
- Terminal payment events are staged in the database outbox and relayed to Kafka. Webhook deliveries are durable, asynchronous, and retried separately.
- Settlement CSV reconciliation reports discrepancies without changing payment or ledger records.

See [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md), [progress.md](progress.md), and the versioned documents under [design docs](design%20docs/) for scope and implementation detail.

## Run the infrastructure

The project Compose file starts Redis and Kafka. MySQL is already running separately for this setup on port `3308`:

```powershell
docker compose -f docker-compose-payment-orchestrator.yml up -d
```

Redis is published on `localhost:6380` and Kafka on `localhost:9193`. The Spring properties default to those host ports and can be overridden with `REDIS_HOST`, `REDIS_PORT`, and `KAFKA_BOOTSTRAP_SERVERS`.

The application still needs a JWT issuer at `http://localhost:8080/realms/payment-realm` because protected API requests require a valid bearer token. Configure an issuer that emits the expected `merchant_id` claim. A webhook receiver is optional for payment processing; without one, webhook delivery retries and eventually reaches its dead-letter state.

The checked-in `application.properties` currently has local MySQL credentials for this learning setup. Do not use those credentials outside local development. The test properties may have their own datasource configuration.

## Start the application

Use Java 17 and the Gradle wrapper:

```powershell
./gradlew.bat bootRun
```

The server listens on port `1012`. Flyway applies database migrations at startup. To run the test suite:

```powershell
./gradlew.bat test
```

Database-backed tests need the MySQL service and credentials configured in the test runtime.

## Create a payment

`POST /api/v1/payments` requires a valid bearer token, `Idempotency-Key`, and a JSON body:

```json
{
  "merchantRef": "order-1001",
  "amountMinor": 12500,
  "currency": "INR",
  "paymentToken": "tok_test_123"
}
```

`amountMinor` is an integer in the currency's minor unit. The payment token is a test value, not card data. The response status depends on the outcome: terminal success/failure is returned synchronously, while an unresolved provider outcome is represented as `UNKNOWN` for later status resolution.

## Week 6 load and ledger checks

See [load-test/README.md](load-test/README.md) for the k6 scenario and post-run SQL checks. Set `PROVIDER_A_OUTAGE_AFTER_CHARGES` to a positive number to make Provider A return an outage after that many charge calls. Use a valid JWT in `JWT_TOKEN`. k6 is not bundled with the project, and the load scenario has not yet been executed in this workspace.

## Architecture decisions and flow

- [Architecture decisions](docs/adr/)
- [Runtime flows](docs/architecture.md)
- [Ambiguous outcome write-up](docs/ambiguous-outcomes.md)
