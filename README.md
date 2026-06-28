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

## API Flow
1. POST /api/v1/products → Create product
2. POST /api/v1/orders → Place order
3. POST /api/v1/payments → Initiate payment
4. GET /api/v1/orders/{id} → Check status