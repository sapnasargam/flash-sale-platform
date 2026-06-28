# Flash Sale & Payment Processing Platform

A production-grade backend system built with Java 17, Spring Boot, PostgreSQL, Redis, and Kafka.

## Tech Stack
- Java 17, Spring Boot 3.2
- PostgreSQL (Database)
- Redis (Idempotency + Caching)
- Apache Kafka (Async Payment Processing)
- Docker Compose

## Setup (One Command)
```bash
docker-compose up --build
```
App starts at: http://localhost:8080
Swagger UI: http://localhost:8080/swagger-ui/index.html

## Architecture
- REST APIs for Product, Order, Payment
- Pessimistic DB Locking for concurrency
- Idempotency via Redis (SHA-256 hashing)
- Async payment via Kafka topics
- DLQ for failed events
- Inventory expiry scheduler (2 min)
  

  ## Architecture Diagram

Client
  |
  v (REST API)
Spring Boot App (:8080)
ProductController | OrderController | PaymentController
  |
  v
Service Layer
ProductService | OrderService | PaymentService | IdempotencyService
  |                |                  |
  v                v                  v
PostgreSQL       Redis            Apache Kafka
(JPA/Hibernate)  (Idempotency     payment.requested
                  24h TTL)              |
                                        v
                               Payment Processor
                               80% success / 20% fail
                               forceStatus override
                                        |
                               payment.success / payment.failed
                                        |
                               Order Updated + Inventory Confirmed

Concurrency Strategy:
  1. Pessimistic Lock (SELECT FOR UPDATE)
  2. Atomic UPDATE WHERE availableStock >= qty
  3. Redis Idempotency Key


Order Lifecycle

PENDING → PAYMENT_PENDING → CONFIRMED
                          → PAYMENT_FAILED
       → EXPIRED (2 min timeout)
       → FAILED (DLQ exhaustion)

## API Flow
1. POST /api/v1/products → Create product
2. POST /api/v1/orders → Place order
3. POST /api/v1/payments → Initiate payment
4. GET /api/v1/orders/{id} → Check status

## Assumptions
- One user can purchase a product only once per sale
- Payment amount must exactly match order total
- Payment processor runs as internal module (Option B)
- Sale window enforced server-side

## Trade-offs
- Pessimistic locking used for consistency over throughput
- Scheduler-based expiry (every 30s) instead of Redis TTL
- Monolith architecture for simpler deployment