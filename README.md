# Spendwise

Spendwise is a policy-aware AI shopping assistant built for the hackathon. It helps people find and compare products while enforcing personal spending limits before checkout. Gemini interprets shopping requests, Channel3 supplies product listings, and the backend policy engine determines which offers are eligible. Users review the options and approve any payment through PayPal Sandbox.

**AI recommends. Spendwise enforces the policy. The user approves.** A PayPal Sandbox payment is a payment test; it does not purchase the product from its retailer.

## Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Tech stack](#tech-stack)
- [Key features](#key-features)
- [System design](#system-design)
- [Integrations](#integrations)
- [Getting started](#getting-started)
- [API documentation](#api-documentation)
- [Testing](#testing)
- [CI/CD](#cicd)
- [Render and Vercel deployment](#render-and-vercel-deployment)
- [Project structure](#project-structure)
- [Future enhancements](#future-enhancements)

## Overview

Spendwise combines product discovery with explicit spending controls. Each account has a spending policy that can limit per-item price, order amount, weekly budget, and weekly order count. Before checkout, the backend rechecks the selected offer and reserves the required budget and order capacity.

Product data comes from Channel3. Gemini can structure the request and rank the returned candidates, but it cannot invent product listings or override policy decisions. Payments use PayPal Orders v2 in Sandbox mode.

### User flow

1. Sign in with Google.
2. Set spending limits and describe a product to find.
3. Review Channel3 results and the policy eligibility of each offer.
4. Add an eligible offer to the cart and approve a PayPal Sandbox checkout.
5. Spendwise verifies the payment state and updates the transaction record.

## Architecture

```text
Browser (React / Vercel)
       │ same-origin /api, OAuth, and session requests
       ▼
Spring Boot API (Render)
  ├── Google OAuth and server-side session
  ├── Gemini ── request interpretation and candidate ranking
  ├── Channel3 ── product listings and offers
  ├── Policy engine ── eligibility and checkout limits
  └── PayPal Sandbox ── order, capture, and verified webhooks
       │
       ▼
PostgreSQL (Render or local Docker Compose)
  ├── accounts and policies
  ├── searches, candidates, and cart
  ├── checkout and transaction state
  └── audit events
```

The backend is organized into feature modules with controller, service, repository, and integration layers. Flyway owns database schema changes; Hibernate validates the mapped schema at startup.

## Tech stack

| Area | Technology |
| --- | --- |
| Backend | Java 21, Spring Boot 3.5, Spring Security, Maven |
| Frontend | React, Vite |
| Database | PostgreSQL, Spring Data JPA, Hibernate |
| Migrations | Flyway |
| AI | Gemini API |
| Product search | Channel3 API |
| Payments | PayPal Orders v2 Sandbox and webhooks |
| API docs | OpenAPI and Swagger UI |
| Containers | Docker and Docker Compose |
| Hosting | Render API and PostgreSQL; Vercel frontend |
| CI | GitHub Actions |

## Key features

- Google OAuth sign-in with a server-side session and CSRF protection.
- Per-account spending policies for item price, order amount, weekly budget, and weekly order count.
- Gemini-based shopping intent extraction and optional ranking of actual provider results.
- Channel3 search with filters, saved preferences, and search history.
- Backend eligibility checks for offers, with rejected offers and policy reasons retained for review.
- Cart and checkout flow with idempotency keys, budget reservations, and serialized capture handling.
- PayPal Sandbox order creation, capture, status reconciliation, and signed webhook verification.
- Transaction summaries and audit events for searches, policy decisions, and payment state changes.
- OpenAPI documentation and automated backend/frontend checks.

## System design

### Shopping request flow

```text
Signed-in user
  → Spring Security session and request validation
  → Gemini extracts shopping intent
  → Channel3 searches for real products and offers
  → Policy engine marks offers eligible or rejected
  → Search, candidates, and audit events are stored in PostgreSQL
  → Frontend displays the result and policy decision
```

### Checkout flow

```text
User selects an eligible offer
  → Backend rechecks the offer and spending policy
  → Budget and order capacity are reserved
  → PayPal Sandbox order is created with an idempotency key
  → User approves on PayPal
  → Backend captures and verifies the order
  → Verified webhooks reconcile payment, refund, and reversal events
```

Policy decisions and payment state changes are enforced on the server. The browser cannot mark an offer eligible or a payment complete.

## Integrations

### Channel3 product discovery

Channel3 returns the current product listings and offers used by the shopping flow. Spendwise requests up to 10 results by default (configurable up to 100). Product titles, prices, merchants, availability, and URLs come from Channel3. Gemini may organize or rank these candidates but cannot add candidates or change their facts.

Set `CHANNEL3_API_KEY` to enable live product search. Search requests are limited per signed-in user; provider limits also apply.

### Gemini

Gemini structures shopping prompts and may rank eligible Channel3 candidates. The deployed backend uses Gemini Flash-Lite for lower-latency prompt interpretation. Set `GEMINI_API_KEY` to enable Gemini features. The key stays on the backend and is never sent to the browser.

Gemini does not decide whether an offer is eligible, perform checkout, or supply product facts. If Gemini is slow or unavailable, the request can fail or use the supported keyword fallback paths.

### PayPal Sandbox

Checkout is configured for PayPal Sandbox. A Sandbox approval and capture are for testing the payment flow; they do not purchase the product from the retailer. Actual product purchases happen on the retailer's site.

Set `PAYPAL_CLIENT_ID` and `PAYPAL_CLIENT_SECRET` from the same Sandbox REST app. To enable webhooks, register the public HTTPS endpoint `https://<render-api-host>/api/webhooks/paypal` and set `PAYPAL_WEBHOOK_ID` to the webhook registration ID. Do not use an individual webhook event ID. The webhook handler verifies each event with PayPal before applying it.

## Getting started

### Prerequisites

- Java 21
- Docker Desktop with Docker Compose
- Node.js and npm
- Git
- Provider credentials for live Google sign-in, Gemini, Channel3, or PayPal Sandbox features

### Local setup

Clone the repository and enter the project directory:

```sh
git clone https://github.com/mkalki/Spendwise.git
cd Spendwise
```

Copy the sample environment file:

```sh
cp .env.example .env
```

On Windows PowerShell, use `Copy-Item .env.example .env` instead. Edit `.env` and set local database values plus any provider credentials you need. Keep real credentials in `.env`; never commit it.

Start PostgreSQL and the backend:

```sh
docker compose up -d --build
```

Install frontend dependencies and start Vite in a second terminal:

```sh
npm --prefix frontend ci
npm --prefix frontend run dev
```

Open [http://localhost:5173](http://localhost:5173). The API health endpoint is [http://localhost:8080/api/health](http://localhost:8080/api/health). Docker Compose waits for PostgreSQL to become healthy before starting the API; Flyway applies database migrations during API startup.

Live Google sign-in requires a Google OAuth client. Add `http://localhost:8080/login/oauth2/code/google` as its authorized redirect URI and set `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` in `.env`. Live product search requires `CHANNEL3_API_KEY` and `GEMINI_API_KEY`; PayPal Sandbox checkout additionally requires Sandbox client credentials. The app can start without these provider keys, but the corresponding live features will be unavailable.

### Google sign-in setup

Create a Google OAuth 2.0 Web application client and authorize this local callback URI:

```text
http://localhost:8080/login/oauth2/code/google
```

Set `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` in `.env`. Start sign-in from the frontend so Vite can proxy `/api`, `/oauth2`, and `/login/oauth2` to the backend.

## API documentation

When the backend is running, use:

- Swagger UI: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
- OpenAPI JSON: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)
- API request/response examples: [`docs/api-contracts.md`](docs/api-contracts.md)

The authenticated shopping, policy, cart, and payment APIs are under `/api/v1`. Google auth, health, and webhook routes are documented in the OpenAPI spec.

## Testing

Run backend tests:

```sh
mvn -f backend/pom.xml test
```

Run frontend tests and lint:

```sh
npm --prefix frontend test
npm --prefix frontend run lint
```

Build the frontend:

```sh
npm --prefix frontend run build
```

Backend tests cover policies, authentication boundaries, request validation, Gemini and Channel3 clients, candidate ranking, checkout state, webhook verification, and API documentation. PostgreSQL Testcontainers coverage runs when Docker is available. Provider connectivity tests are opt-in and require configured credentials.

## CI/CD

GitHub Actions workflows are in [`.github/workflows/`](.github/workflows/):

- `ci.yml` builds and verifies the backend, runs frontend tests and lint, builds the frontend, validates the OpenAPI document and Docker Compose configuration, and builds the backend Docker image.
- `cd.yml` checks the deployed Spendwise frontend and API health after a Render deployment; it can also be run manually. Render and Vercel handle the actual deployments from their connected repositories.

The deployment check verifies availability. It does not run a live PayPal transaction or a live Gemini/Channel3 search.

## Render and Vercel deployment

The project deploys the frontend to Vercel and the Dockerized API plus PostgreSQL database to Render. The Render Blueprint is [`render.yaml`](render.yaml); Vercel's rewrite configuration is [`frontend/vercel.json`](frontend/vercel.json). Vercel proxies `/api`, `/oauth2`, and `/login/oauth2` to Render so the browser uses the frontend origin for session cookies and CSRF.

Configure these production URLs using the Vercel domain:

```text
FRONTEND_ORIGIN=https://<your-vercel-domain>
AUTH_SUCCESS_URL=https://<your-vercel-domain>/
GOOGLE_REDIRECT_URI=https://<your-vercel-domain>/login/oauth2/code/google
PAYPAL_RETURN_URL=https://<your-vercel-domain>/checkout/return
PAYPAL_CANCEL_URL=https://<your-vercel-domain>/checkout/cancel
PAYPAL_VAULT_RETURN_URL=https://<your-vercel-domain>/paypal/vault/return
PAYPAL_VAULT_CANCEL_URL=https://<your-vercel-domain>/paypal/vault/cancel
```

Add the exact `GOOGLE_REDIRECT_URI` to the Google OAuth client. Set provider secrets in Render; do not commit them or place them in frontend variables. PayPal client credentials must come from a Sandbox REST app. Register the PayPal webhook against the public Render API URL and set its registration ID as `PAYPAL_WEBHOOK_ID`.

Render's free web services sleep after 15 minutes without inbound traffic, so the first request after inactivity can take about a minute to wake the service. A free Render PostgreSQL database expires 30 days after creation and has no backups; use a paid database for data that must persist.

## Project structure

```text
.
├── backend/                 Spring Boot API and Dockerfile
├── frontend/                React/Vite application and Vercel config
├── docs/                    API contracts and project documentation
├── load-tests/              k6 API load test
├── openapi/                 Checked-in generated OpenAPI document
├── render.yaml              Render API and PostgreSQL Blueprint
└── docker-compose.yml        Local PostgreSQL and backend services
```

## Future enhancements

- PayPal Sandbox transactions are test transactions; live payments require a separate production PayPal app and launch review.
- Channel3 availability, product coverage, pricing, and latency depend on the external provider.
- Render free services can sleep, and the free PostgreSQL instance expires after 30 days.
- A full Sandbox webhook lifecycle check requires a Sandbox buyer, successful checkout approval, and delivery to the registered public webhook.
- Add durable sessions across API restarts and richer provider latency metrics.
- Move to production hosting with a persistent database plan and backups before relying on stored user data.
