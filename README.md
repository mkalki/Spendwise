# Spendwise

Spendwise is a policy-driven AI shopping assistant. It helps people discover and compare products while enforcing spending limits in the backend before checkout.

**Core rule:** AI recommends. The policy engine decides. The user approves. PayPal processes a clearly labelled Sandbox payment.

Channel3 discovery links and a PayPal Sandbox payment are separate actions: the Sandbox payment does not buy the product from its retailer.

## Current stack

- Java 21, Spring Boot 3.5, Maven, PostgreSQL
- Channel3 product discovery and PayPal Orders v2 Sandbox integrations
- Spring AI for richer intent extraction and recommendations remains planned

## Local setup

1. Copy `.env.example` to `.env` and set a local PostgreSQL password.
2. Start PostgreSQL and the API with `docker compose up -d --build`. Compose gives the backend the same database credentials and waits for PostgreSQL to become healthy.
3. Flyway applies pending migrations from `backend/src/main/resources/db/migration` on API startup; Hibernate validates the mapped schema and does not create tables. Add schema changes as new `V2__...sql`, `V3__...sql` files; do not edit a migration after it has run in a shared database.
4. Check the API at `http://localhost:8080/api/health`.

### Google sign-in

Create a Google OAuth 2.0 Web application client and add
`http://localhost:8080/login/oauth2/code/google` as an authorized redirect URI.
Set `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` in `.env` before starting the
backend. The frontend origin and post-login destination are configured with
`FRONTEND_ORIGIN` and `AUTH_SUCCESS_URL`. Use HTTPS and set
`SESSION_COOKIE_SECURE=true` outside local development.

Start sign-in by navigating the browser to
`http://localhost:8080/oauth2/authorization/google`. After Google verifies the
user, Spring creates a server-side session and redirects to `AUTH_SUCCESS_URL`.
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
`PAYPAL_CLIENT_SECRET` to enable Sandbox checkout. Configure the corresponding
return/cancel URLs for your frontend. Keep real values in `.env`, which is
ignored by Git. API contracts and examples are in `docs/api-contracts.md`.

## Project layout

- `backend/` — Spring Boot API
- `backend/Dockerfile` — backend build and runtime image
- `docs/api-contracts.md` — frontend/backend response contracts
- `docker-compose.yml` — local PostgreSQL service
- `backend/src/main/resources/db/migration/` — versioned Flyway SQL migrations

The first migration creates `spending_policies`, `payment_transactions`, and `audit_events`, then seeds the demo policy ($60 per item, $100 per order, $150 per week, one order per week, USD/US).

## Current status

The backend exposes these authenticated, owner-scoped frontend APIs under
`/api/v1`:

| API | Purpose |
| --- | --- |
| `GET/PUT /policies/active` | Read or update the signed-in user's spending limits. |
| `POST /shopping/search` | Search Channel3 using the user's policy and return eligible and rejected offers. |
| `POST /checkout-sessions` | Recheck an offer and policy, reserve weekly budget/order capacity, then create a PayPal Sandbox order. Requires `Idempotency-Key`. |
| `POST /checkout-sessions/{id}/capture` | Capture the user's approved Sandbox order. |
| `POST /checkout-sessions/{id}/cancel` | Cancel checkout and release its reservation only when payment state is confirmed. |
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
state-changing request. Natural-language price-limit extraction and
recommendations are deterministic for now; Spring AI integration and the
frontend remain to be implemented.

Schema additions are in `V2__add_shopping_and_checkout_api.sql`,
`V3__retain_search_price_limits.sql`, `V4__create_user_accounts.sql`, and
`V5__repair_payment_candidate_reference.sql`. The repair migration restores the
optional candidate foreign key if a database drifted after V2. Earlier
migrations are not edited.

## Testing

Run the backend unit and HTTP/security API integration tests with:

```powershell
mvn -f backend\pom.xml test
```

The test suite covers policy limits and intent parsing, request validation and
serialization, authenticated-user propagation, unauthenticated access and CSRF,
and provider-host restrictions. API integration tests use mocked services;
they do not apply migrations to PostgreSQL or make live Channel3/PayPal calls.
Database and provider end-to-end flows still need verification against an
isolated PostgreSQL database and configured provider Sandbox credentials.
