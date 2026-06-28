# Flash Sale & Payment Processing Platform

A production-grade Spring Boot backend system for handling high-concurrency flash sales with async payment processing via Kafka.

## Architecture

```
Client
  │
  ▼
[Spring Boot REST API]
  │
  ├── ProductController   → ProductService  → PostgreSQL
  ├── OrderController     → OrderService   → PostgreSQL + Redis (idempotency)
  └── PaymentController   → PaymentService → PostgreSQL + Kafka
                                                    │
                                          [Kafka: payment.requested]
                                                    │
                                          [PaymentProcessorConsumer]
                                           (simulates gateway)
                                                    │
                                    ┌───────────────┴───────────────┐
                               payment.success              payment.failed
                                    │                               │
                          Confirm inventory            Release inventory
                          Order → CONFIRMED            Order → PAYMENT_FAILED
```

## Tech Stack

| Layer       | Technology                    |
|-------------|-------------------------------|
| Language    | Java 17                       |
| Framework   | Spring Boot 3.2               |
| Database    | PostgreSQL (JPA + Hibernate)  |
| Cache       | Redis (idempotency + locking) |
| Messaging   | Apache Kafka                  |
| API Docs    | Swagger / OpenAPI 3           |
| Testing     | JUnit 5 + Mockito             |

## Setup Instructions

### Prerequisites
- Java 17+
- Docker & Docker Compose

### Run with Docker Compose (Recommended)
```bash
git clone <repo-url>
cd flash-sale-platform
docker-compose up --build
```

App available at: `http://localhost:8080`  
Swagger UI: `http://localhost:8080/swagger-ui.html`

### Run Locally
```bash
# Start dependencies
docker-compose up postgres redis kafka -d

# Copy env
cp .env.example .env

# Run app
./mvnw spring-boot:run
```

## API Overview

### Products
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/products` | Create product |
| GET | `/api/v1/products/{id}` | Get product |

### Orders
| Method | Endpoint | Header | Description |
|--------|----------|--------|-------------|
| POST | `/api/v1/orders` | `Idempotency-Key` | Create order |
| GET | `/api/v1/orders/{orderId}` | — | Get order |

### Payments
| Method | Endpoint | Header | Description |
|--------|----------|--------|-------------|
| POST | `/api/v1/payments` | `Idempotency-Key` | Initiate payment |
| GET | `/api/v1/payments/{paymentId}` | — | Get payment |

## Design Decisions

### 1. Concurrency — 3-Layer Defense
**Problem:** 50 users buy product with stock 10 → must not oversell.

**Solution:**
1. **Pessimistic DB Lock** (`SELECT ... FOR UPDATE`) on Product row → serializes access
2. **Atomic UPDATE** with `WHERE availableStock >= qty` → DB-level guard
3. **Redis Idempotency** → prevents duplicate API calls before hitting DB

### 2. Idempotency
- SHA-256 hash of request payload stored in Redis with 24h TTL
- Same key + same payload → return existing response (safe replay)
- Same key + different payload → 422 Unprocessable Entity (conflict)

### 3. Async Payment via Kafka
- Payment initiation is non-blocking (returns immediately with PENDING status)
- `PaymentProcessorConsumer` simulates the gateway (80% success, 20% failure)
- Results published to `payment.success` or `payment.failed` topics
- Separate consumers update order status and inventory accordingly

### 4. Dead Letter Queue (DLQ)
- `@RetryableTopic` with 3 retries + exponential backoff (1s, 2s, 4s)
- After exhaustion → DLQ topic → manual investigation
- DLQ handler releases inventory and marks order as FAILED

### 5. Inventory Expiry
- `@Scheduled` every 30 seconds scans for PENDING orders past `reservationExpiresAt`
- 2-minute expiry window → inventory released, order marked EXPIRED

## Assumptions
- One user can purchase a given product only once per sale
- Payment amount must exactly match order total
- Sale window is enforced server-side based on `saleStartTime` / `saleEndTime`
- Payment processor runs in the same application (Option B — monolith module)

## Trade-offs

| Decision | Trade-off |
|----------|-----------|
| Pessimistic locking | Higher throughput consistency vs. lower concurrency under extreme load |
| Monolith vs microservices | Simpler deployment vs. independent scalability |
| Scheduler-based expiry | Simple implementation vs. Redis TTL-based approach (more precise) |
| In-process payment processor | Easier testing vs. true gateway isolation |

## Running Tests
```bash
./mvnw test
```

Test coverage includes:
- Successful order creation
- Idempotency replay
- Sale not active
- Duplicate order
- Insufficient inventory
- Concurrent race condition simulation
- Payment initiation
- Payment forced status validation
- Duplicate payment event protection
- Redis idempotency replay/conflict validation

## Verification Checklist

Use this checklist before submitting the assignment:

```bash
mvn test
docker compose config
docker compose up --build
```

After startup, verify:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Create product with an active sale window.
- Create order with `Idempotency-Key`.
- Repeat the same order request with the same key and payload: existing order should be returned.
- Repeat with the same key and different payload: request should be rejected with 422.
- Initiate payment with `forceStatus: SUCCESS`: order should become `CONFIRMED`.
- Initiate payment with `forceStatus: FAILED`: inventory should be released and order should become `PAYMENT_FAILED`.
- Leave a `PENDING` or `PAYMENT_PENDING` order unpaid for the configured expiry window: inventory should be released and order should become `EXPIRED`.

## Postman Collection

Import `FlashSale.postman_collection.json` into Postman. It contains product, order, and payment examples with idempotency headers and forced success/failure payment payloads.
