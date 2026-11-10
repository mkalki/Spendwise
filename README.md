# Spendwise

Spendwise is a policy-driven AI shopping assistant. It helps people discover and compare products while enforcing spending limits in the backend before checkout.

**Core rule:** AI recommends. The policy engine decides. The user approves. PayPal processes a clearly labelled Sandbox payment.

Channel3 discovery links and a PayPal Sandbox payment are separate actions: the Sandbox payment does not buy the product from its retailer.

## Current stack

- Java 21, Spring Boot 3.5, Maven, PostgreSQL
- Channel3 product discovery and PayPal Orders v2 Sandbox integrations
- Gemini intent extraction and ID-only product ranking over actual Channel3 search results

## Local setup

1. Copy `.env.example` to `.env` and set local PostgreSQL credentials plus any provider credentials you plan to use.
2. Start PostgreSQL and the API with `docker compose up -d --build`. Compose starts a PostgreSQL 18 container with a named persistent volume, waits for its health check, then starts the backend against that database.
3. Flyway applies pending migrations from `backend/src/main/resources/db/migration` on API startup; Hibernate validates the mapped schema and does not create tables. Add schema changes as new `V2__...sql`, `V3__...sql` files; do not edit a migration after it has run in a shared database.
4. Check the API at `http://localhost:8080/api/health` and PostgreSQL on `localhost:${POSTGRES_PORT:-5432}`.

### Google sign-in

Create a Google OAuth 2.0 Web application client and add
`http://localhost:8080/login/oauth2/code/google` as an authorized redirect URI.
Set `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` in `.env` before starting the
backend. The frontend origin and post-login destination are configured with
`FRONTEND_ORIGIN` and `AUTH_SUCCESS_URL`. Use HTTPS and set
`SESSION_COOKIE_SECURE=true` outside local development.

Start sign-in from the frontend at `http://localhost:5173`. The browser opens
`/api/auth/google/login` on the frontend origin and Vite proxies the OAuth
requests to Spring. After Google verifies the user, Spring creates a
server-side session and redirects to `AUTH_SUCCESS_URL`.

In production, proxy `/api/*`, `/oauth2/*`, and `/login/oauth2/*` on the public
frontend origin to Spring. Set `FRONTEND_ORIGIN` to that HTTPS origin,
`AUTH_SUCCESS_URL` to the frontend URL users should land on, and
`GOOGLE_REDIRECT_URI` to the public `/login/oauth2/code/google` URL. Register
that exact callback URL in the Google OAuth client.

The frontend can fetch `/api/auth/me` with credentials to read the signed-in
user, and call `POST /api/auth/logout` with the CSRF header/token obtained from
`GET /api/auth/csrf`. The session is the authentication credential; do not put
OAuth access tokens in browser storage.

### Password accounts

`POST /api/auth/signup` accepts `email`, `username`, and `password`, creates the
account, and starts a session. `POST /api/auth/login` accepts `identifier` (email
or username) and `password`. Fetch `GET /api/auth/csrf` first and send its token
in `X-XSRF-TOKEN` with signup/login/logout requests. Passwords are stored as
PBKDF2 hashes. Google login and signup are available at
`/api/auth/google/login` and `/api/auth/google/signup`; first Google sign-in
creates the account when its verified email is unused.

Set `CHANNEL3_API_KEY` to enable live shopping search and `PAYPAL_CLIENT_ID` /
`PAYPAL_CLIENT_SECRET` to enable Sandbox checkout. Set `PAYPAL_WEBHOOK_ID` to
the webhook registration ID created under the same PayPal Sandbox REST app as
the client ID and secret. Do not use a webhook event ID (those start with
`WH-`). Register a public HTTPS URL pointing to `POST /api/webhooks/paypal`;
PayPal cannot deliver to localhost. Configure the corresponding return/cancel
URLs for your frontend. Keep real values in `.env`, which is
ignored by Git. API contracts and examples are in `docs/api-contracts.md`.
Channel3 searches request 10 provider results by default and are limited to 100
requests per minute per signed-in user. Gemini endpoints are limited to 20
requests per minute per signed-in user. External requests use configurable
5-second connect and 20-second response timeouts by default. Read-only Channel3
calls and Gemini calls retry once on transient HTTP statuses; PayPal operations
do not retry uncertain network failures.

### Gemini connectivity

Set `GEMINI_API_KEY` in `.env` to enable the authenticated
`POST /api/v1/ai/generate` endpoint. `GEMINI_MODEL` defaults to
`gemini-3.8-flash`. The backend sends prompts to Gemini using Spring `RestClient`;
the key stays on the server. The generic generation endpoint is separate from
Channel3. Product search uses Gemini to structure the request, calls Channel3
for current offers, and can ask Gemini to rank only returned candidate IDs.
Product titles, prices, merchants, availability, and URLs always come from
Channel3. Customers can select `LOWEST_PRICE`, `BEST_MATCH`, or
`AI_RECOMMENDED` and filter by price, category, retailer, availability, and
sale status. Channel3's documented product response does not expose ratings,
so top-rated sorting is unavailable. Swagger UI is available at
`http://localhost:8080/swagger-ui/index.html`.

PayPal webhooks are received at `POST /api/webhooks/paypal`. The endpoint is
public for PayPal delivery, but verifies the transmission headers and exact
received event body with PayPal before processing. Supported checkout/capture
events reconcile against PayPal order status; refund/reversal events fetch the
capture from PayPal and check its ID, order ID, amount, and currency before
updating the transaction. Unknown event types are acknowledged after signature
verification without changing payment state. Partial refunds keep the original
order amount counted against weekly spend (a conservative limit); full refunds
and reversals no longer count as spend. Capture requests are serialized per
order; a second request while one is in progress receives a conflict.

## Project layout

- `backend/` — Spring Boot API
- `backend/Dockerfile` — backend build and runtime image
- `docs/api-contracts.md` — frontend/backend response contracts
- `docker-compose.yml` — local PostgreSQL service
- `spendwise-postgres-data` — persistent local PostgreSQL data volume
- `backend/src/main/resources/db/migration/` — versioned Flyway SQL migrations
- `load-tests/spendwise-api.js` — k6 load test for an authenticated API route

The first migration creates `spending_policies`, `payment_transactions`, and `audit_events`, then seeds the demo policy ($60 per item, $100 per order, $150 per week, one order per week, USD/US).

## Current status

The backend exposes these authenticated, owner-scoped frontend APIs under
`/api/v1`:

| API | Purpose |
| --- | --- |
| `GET/PUT /policies/active` | Read or update the signed-in user's spending limits. |
| `POST /shopping/search` | Search Channel3 using customer filters and return eligible and rejected offers in the selected ranking order. |
| `GET /shopping/preferences` | Read the signed-in user's saved shopping preferences. |
| `PUT /shopping/preferences` | Save preferred categories, brands, retailers, keywords, and an optional budget. |
| `GET /shopping/search-history` | List the signed-in user's past searches (`page` defaults to 0; `size` defaults to 20 and is capped at 100). |
| `POST /checkout-sessions` | Recheck an offer and policy, reserve weekly budget/order capacity, then create a PayPal Sandbox order. Requires `Idempotency-Key`. |
| `POST /checkout-sessions/{id}/capture` | Capture the user's approved Sandbox order. |
| `POST /checkout-sessions/{id}/cancel` | Cancel checkout and release its reservation only when payment state is confirmed. |
| `POST /api/payments/create` | Create an owner-scoped PayPal Sandbox order from a stored, policy-eligible search candidate. Requires `Idempotency-Key`; returns the PayPal approval URL. |
| `POST /api/payments/{orderId}/capture` | Capture the owner's approved PayPal order and persist the verified result. |
| `GET /api/payments/{orderId}` | Read PayPal status and reconcile it with the owner-scoped payment record. |
| `GET /transactions` | List the signed-in user's transactions. |
| `GET /transactions/summary` | Return weekly spend, active reservations, remaining budget, and order counts. |

Google session authentication, CSRF, and health endpoints remain under
`/api`; see [the API contract](docs/api-contracts.md) for request/response
examples and error codes. Swagger UI is available at
`http://localhost:8080/swagger-ui/index.html`. Its OpenAPI JSON spec is at
`http://localhost:8080/v3/api-docs`; use **Download** or save that JSON response
to import the current API definition into APIMatic API Designer
(**Import > OpenAPI**). For a deployed API, APIMatic can import the spec URL
directly; a locally running `localhost` URL is not accessible to APIMatic's
hosted service. The generated spec is also checked in at
[`openapi/spendwise-openapi.json`](openapi/spendwise-openapi.json). Both tools
therefore use the same generated API definition.

The spec documents the session cookie and CSRF header security requirements.
To try secured endpoints in Swagger UI, sign in to the API in that browser
first; obtain a CSRF token from `GET /api/auth/csrf` before trying a
state-changing request. Shopping search uses Gemini to structure the request
before Channel3 search. AI ranking can only reorder eligible Channel3 result
IDs; policy enforcement and all product facts remain server-controlled.
Spring AI framework adoption and the frontend remain future work.

Schema additions are in `V2__add_shopping_and_checkout_api.sql`,
`V3__retain_search_price_limits.sql`, `V4__create_user_accounts.sql`, and
`V5__repair_payment_candidate_reference.sql`, `V6__add_shopping_preferences.sql`,
and `V7__track_paypal_refunds.sql`. The repair migration restores the
optional candidate foreign key if a database drifted after V2. Earlier
migrations are not edited.

## Testing

Run the backend unit and HTTP/security API integration tests with:

```powershell
mvn -f backend\pom.xml test
```

The suite covers policy limits, intent parsing, request validation, authenticated
owner propagation, API error handling, Gemini/Channel3 client failures, AI
ranking, PayPal capture state, webhook signature verification, retries, and
Swagger. A Testcontainers PostgreSQL test applies Flyway migrations and checks
owner-scoped persistence when Docker is available. Provider mocks are used in CI.

For opt-in live provider connectivity tests, set the provider environment
variables and run:

```powershell
$env:SPENDWISE_EXTERNAL_INTEGRATION_TESTS = "true"
mvn -f backend\pom.xml verify -DskipProviderITs=false
```

This checks Gemini generation, Channel3 search, and PayPal Sandbox OAuth. A full
PayPal buyer approval/capture/webhook round trip still requires a registered
Sandbox webhook and a Sandbox buyer account.

For database-backed load testing, install k6, sign in to get a session cookie,
then run:

```powershell
$env:SPENDWISE_BASE_URL = "http://localhost:8080"
$env:SPENDWISE_SESSION_COOKIE = "JSESSIONID=your-session-cookie"
k6 run load-tests/spendwise-api.js
```

## Implemented features and remaining work

| Area | Status |
| --- | --- |
| Google OAuth and password sessions | Implemented; live Google sign-in requires configured OAuth credentials. |
| Gemini integration, product comparison, and explanations | Implemented; explanations compare eligible Channel3 candidates with the customer's prompt and saved preferences. Gemini cannot provide commercial product facts or affect policy eligibility. Calls have bounded timeouts, transient retries, and a 20-request/minute user limit. |
| Channel3 product search | Implemented with filters, 100 requests/minute per user, provider timeouts, and retry handling. |
| PayPal Sandbox checkout | Implemented with owner-scoped idempotency, serialized captures, and PayPal status verification before marking paid. |
| PayPal webhook verification | Implemented at `POST /api/webhooks/paypal`; requires `PAYPAL_WEBHOOK_ID` from the registered PayPal webhook. |
| Shopping preferences and search history | Preferences load automatically for searches; saved maximum price tightens the search cap and other preferences guide AI comparison. Customers can view and update them through the authenticated GET/PUT preferences APIs. |
| Audit trail | Implemented for policy changes, search starts/results, candidate decisions, preference updates, checkout reservations, PayPal order creation and payment state changes, and verified webhook deliveries. Audit metadata omits raw search text and credentials. |
| Error handling, validation, and Swagger | Implemented with stable JSON API errors and documented routes. |
| Unit, controller, and database tests | Implemented; PostgreSQL Testcontainers coverage is skipped when Docker is unavailable. |
| Load testing | k6 script added for authenticated, database-backed API requests. |
| Frontend | Not implemented; the repository currently contains the backend API. |
| Live Sandbox lifecycle test | Still needs a configured webhook, Sandbox buyer approval, and end-to-end confirmation in the developer account. |
