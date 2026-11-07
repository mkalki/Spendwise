# Spendwise

Spendwise is a policy-driven AI shopping assistant. It helps people discover and compare products while enforcing spending limits in the backend before checkout.

**Core rule:** AI recommends. The policy engine decides. The user approves. PayPal processes a clearly labelled Sandbox payment.

Channel3 discovery links and a PayPal Sandbox payment are separate actions: the Sandbox payment does not buy the product from its retailer.

## Current stack

- Java 21, Spring Boot 3.5, Maven, PostgreSQL
- Planned integrations: Channel3 product discovery, Spring AI for intent/recommendations, PayPal Orders v2 Sandbox

## Local setup

1. Copy `.env.example` to `.env` and set a local PostgreSQL password.
2. Start PostgreSQL and the API with `docker compose up -d --build`. Compose gives the backend the same database credentials and waits for PostgreSQL to become healthy.
3. Flyway applies pending migrations from `backend/src/main/resources/db/migration` on API startup; Hibernate validates the mapped schema and does not create tables. Add schema changes as new `V2__...sql`, `V3__...sql` files; do not edit a migration after it has run in a shared database.
4. Check the API at `http://localhost:8080/api/health`.

Integration keys are optional until their corresponding services are implemented. Keep real values in `.env`, which is ignored by Git.

## Project layout

- `backend/` — Spring Boot API
- `backend/Dockerfile` — backend build and runtime image
- `docs/api-contracts.md` — initial frontend/backend response contracts
- `docker-compose.yml` — local PostgreSQL service
- `backend/src/main/resources/db/migration/` — versioned Flyway SQL migrations

The first migration creates `spending_policies`, `payment_transactions`, and `audit_events`, then seeds the demo policy ($60 per item, $100 per order, $150 per week, one order per week, USD/US).

## Current status

Backend-only initial scaffold. External provider integrations and persistence-backed policy APIs are not implemented yet. The frontend is intentionally deferred.
