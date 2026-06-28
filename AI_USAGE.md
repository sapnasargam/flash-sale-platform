# AI Usage Disclosure

## AI Tools Used
- Claude (Anthropic) — used for initial scaffolding and code structure

## Where AI Was Used
- Project folder structure generation
- boilerplate code for DTOs, Entities, and Repositories
- Docker Compose configuration template

## Manually Implemented Sections
- Core business logic in `OrderService` (concurrency strategy, inventory reservation)
- Idempotency logic in `IdempotencyService` (SHA-256 hashing, Redis TTL strategy)
- Kafka consumer retry and DLQ handling in `PaymentProcessorConsumer`
- Pessimistic locking strategy in `ProductRepository`
- All unit tests in `OrderServiceTest`
- Architecture decisions and trade-off analysis (see README)
- Payment simulation logic (random 80/20 + forced override)

## Generated Code/Design Sections
- Entity class structure
- DTO builder patterns
- `application.yml` base configuration

## Notes
All generated code was reviewed, understood, and modified to fit the
specific business requirements of this assignment.
