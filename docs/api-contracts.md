# Spendwise API contracts

Backend origin: `http://localhost:8080`. Versioned product APIs use `/api/v1`;
Google session endpoints and health retain their existing `/api` paths.

## Authentication and browser requests

### Password signup and login

Before a state-changing auth request, fetch `GET /api/auth/csrf`, then include
the returned header and token. The session cookie must be retained by the
browser/client.

`POST /api/auth/signup` creates an account and signs the user in:

```json
{
  "email": "shopper@example.com",
  "username": "shopper1",
  "password": "a-long-password"
}
```

Email and username are normalized to lowercase. Usernames must be 3–32
characters and use letters, numbers, `.`, `_` or `-`. Passwords must contain at
least 12 characters. A successful response is `201 Created` with the profile
and an authenticated session cookie. Duplicate email or username returns
`409 Conflict`.

`POST /api/auth/login` accepts either the registered email or username:

```json
{
  "identifier": "shopper1",
  "password": "a-long-password"
}
```

Successful login returns `200 OK` and starts a session. Invalid credentials
return `401 Unauthorized`.

### Google login and signup

Begin Google OAuth by navigating to
`/api/auth/google/login` or `/api/auth/google/signup` (both redirect to Google).
Google verifies the identity; a verified email creates a Spendwise account on
first sign-in, and later sign-ins use that account. The standard Spring route
`/oauth2/authorization/google` is also available. On success, the server
establishes an HTTP session and redirects to the configured frontend callback.
If that email already belongs to a password account, Google sign-in is rejected;
automatic account linking is not enabled. Send browser API requests with
credentials enabled.

`GET /api/auth/csrf` returns the CSRF header name and token for state-changing
requests:

```json
{"headerName":"X-XSRF-TOKEN","token":"..."}
```

Send the token in the returned header and include the session cookie on
state-changing requests. `GET /api/auth/me` returns the signed-in account
profile and its provider (`PASSWORD` or `GOOGLE`).
`POST /api/auth/logout` ends the session and returns `204`.
Unauthenticated `/api/**` requests return `401`; `/api/health` remains public.

## Gemini text generation

`POST /api/v1/ai/generate` accepts a prompt and sends it to the configured
Gemini model. It requires the signed-in session and a CSRF token. This endpoint
is independent of Channel3; the initial connectivity feature does not yet
interpret shopping requests or rank products.

```json
{"prompt":"Explain how a weekly spending limit works."}
```

Response:

```json
{"model":"gemini-3.8-flash","text":"A weekly spending limit caps the total eligible spending during a defined week."}
```

Configure `GEMINI_API_KEY` in the backend environment. Optionally set
`GEMINI_MODEL`; it defaults to `gemini-3.8-flash`. The API key is sent in the
`x-goog-api-key` header and is never accepted from the browser request.
Validation errors return `400`; missing configuration returns
`503 GEMINI_NOT_CONFIGURED`; provider failures return a stable `GEMINI_*` error.

Money values in responses are decimal strings plus an ISO 4217 currency code.
The server owns the authoritative product price and all policy decisions.
Never send a price, amount, PayPal order ID, or payment status as trusted input.

## Health and API documentation

`GET /api/health`

```json
{"status":"ok","service":"spendwise-api"}
```

OpenAPI JSON is available at `/v3/api-docs`; Swagger UI is available at
`/swagger-ui/index.html`.
The generated OpenAPI spec is also the source to import into APIMatic API
Designer (`Import > OpenAPI`). Download/save the JSON from `/v3/api-docs` when
working locally, or import the deployed spec URL. The OpenAPI document describes
the `JSESSIONID` session cookie and the `X-XSRF-TOKEN` header required for
state-changing requests. Swagger UI requests use the current browser session;
sign in first and fetch `/api/auth/csrf` before trying a state-changing call.

## Spending policy

`GET /api/v1/policies/active` returns the signed-in user's policy. It returns
`404 POLICY_NOT_FOUND` if the user has not configured one yet.

`PUT /api/v1/policies/active` creates or updates the policy:

```json
{
  "maxProductPrice": "60.00",
  "maxOrderAmount": "100.00",
  "weeklyBudget": "150.00",
  "maxOrdersPerWeek": 1,
  "countryCode": "US",
  "currencyCode": "USD",
  "timezone": "America/Los_Angeles"
}
```

Amounts must be positive and have at most two decimal places. Limits must
satisfy `maxProductPrice <= maxOrderAmount <= weeklyBudget`. Country/currency
must be supported by Channel3. Weekly spending is evaluated in the configured
time zone.

## Product search

`POST /api/v1/shopping/search`

```json
{
  "message":"Find wireless mechanical keyboards with good battery life",
  "sortBy":"AI_RECOMMENDED",
  "filters":{
    "minPrice":25,
    "maxPrice":45,
    "categoryIds":["keyboards"],
    "websiteIds":["shop.example"],
    "availability":"IN_STOCK",
    "onlyOnSale":false
  }
}
```

The backend asks Gemini to extract a concise product query and optional budget,
then searches Channel3 using that query, the policy country/currency, and the
stricter of user and saved price caps. `sortBy` accepts `LOWEST_PRICE` (the
default), `BEST_MATCH` (preserve Channel3 ordering), or `AI_RECOMMENDED`.
`filters` supports `minPrice`, `maxPrice`, `categoryIds`, `websiteIds`,
`availability` (`IN_STOCK` or `OUT_OF_STOCK`), and `onlyOnSale`. Price values
use the active policy currency. Price and other explicit filters are sent to
Channel3; the minimum price is also enforced on returned offers. A request
budget currency that conflicts with the active policy is rejected.

If Google explicitly rejects the configured Gemini key, the backend searches
Channel3 using the parsed keywords. It applies the literal budget, explicit
filters, and policy checks, skips Gemini ranking, and reports the fallback in
`recommendation`.

On every search, the backend loads the signed-in customer's saved preferences.
A saved `maxPrice` is combined with the request budget and spending-policy cap
using the strictest applicable amount (only when its currency matches the
policy). Saved category, brand, retailer, and keyword preferences are supplied
to Gemini as soft ranking context. Customers can review or change them at any
time with `GET` and `PUT /api/v1/shopping/preferences`.

Gemini compares the eligible Channel3 offers and returns an explanation for
each candidate in `recommendationExplanation`, plus a short overall
`recommendation`. The comparison uses the customer's prompt and saved
preferences against Channel3 title, brand, merchant, and price data. Those
commercial facts remain server-sourced from Channel3; Gemini cannot add/remove
eligible products or affect checkout policy. `AI_RECOMMENDED` orders results
by that comparison. Other sort choices retain their requested ordering while
still returning explanations. The response's `comparisonSummary` explains the
overall match separately from the `recommendation` string that describes the
selected sort. Unknown and duplicate candidate IDs are ignored.
If a comparison call fails after the search succeeds, the API returns the
products without AI explanations and falls back to deterministic ordering.

The backend calls `POST https://api.trychannel3.com/v1/search` with the
`x-api-key` header and JSON content type. The provider request includes the
normalized natural-language query, a default `limit` of `10` (configurable with
`CHANNEL3_SEARCH_LIMIT` from 1 to 100), the user's country/currency, and the
selected structured filters. Each product's image and offers are mapped to
candidate results; offers from different merchants are returned separately.
An empty `products` array returns empty candidate arrays. Provider errors
become stable `PRODUCT_PROVIDER_*` API errors; credentials and upstream
response bodies are not returned to clients.
Channel3-backed searches and checkout offer refreshes are limited to 100
provider requests per minute per signed-in user. Requests over the limit return
`429 Too Many Requests`, code `CHANNEL3_RATE_LIMIT_EXCEEDED`, and a `Retry-After`
header with the number of seconds to wait.

Example provider request:

```json
{
  "query": "Find wireless headphones under $45",
  "limit": 10,
  "config": {"country": "US", "currency": "USD"},
  "filters": {
    "price": {"min_price": 25, "max_price": 45},
    "category_ids": ["headphones"],
    "website_ids": ["shop.example"],
    "availability": ["InStock"],
    "sale": "on_sale"
  }
}
```

```json
{
  "searchSessionId": "c1be5d61-887d-4c72-9067-13222cfe624b",
  "intent": {
    "normalizedQuery": "Find wireless mechanical keyboards under $45 with good battery life",
    "keywords": "Find wireless mechanical keyboards with good battery life",
    "requestedMaxPrice": "45",
    "currencyCode": "USD",
    "countryCode": "US"
  },
  "eligibleCandidates": [
    {
      "candidateId": "769812c6-9805-42ec-af81-14ad731a28cb",
      "channel3ProductId": "provider-product-id",
      "productTitle": "Example Wireless Keyboard",
      "brand": "Example",
      "imageUrl": "https://example.test/image.jpg",
      "merchantName": "example.test",
      "offerUrl": "https://example.test/product",
      "price": "40.00",
      "currencyCode": "USD",
      "eligible": true,
      "recommendationExplanation": "Matches your preferred brand and is within your stated budget.",
      "rating": {"value": 4.6, "count": 120, "sourceCount": 3}
    }
  ],
  "rejectedCandidates": [],
  "recommendation": "This offer best matches your requested features and saved preferences.",
  "comparisonSummary": "It matches your selected brand and keywords while meeting your requested price range.",
  "sortBy": "AI_RECOMMENDED",
  "aiRanked": true
}
```

Gemini interprets the natural-language shopping request. Deterministic budget
parsing reinforces explicit amounts and the policy engine remains the final
authority. `LOWEST_PRICE` is sorted by the actual Channel3 offer prices.
`BEST_MATCH` follows Channel3's returned order. For `AI_RECOMMENDED`, Gemini
can only select and order IDs from eligible Channel3 results; all product facts
remain server-mapped from Channel3. `GEMINI_API_KEY` and `CHANNEL3_API_KEY` must
be configured for live search; otherwise the relevant provider configuration
error is returned (AI ranking falls back to Channel3 ordering).

When Channel3 supplies a rating rollup, the API includes its 0–5 value, review
count, and number of contributing rating sources. Missing ratings are returned
as null. The Products page can sort the returned offers by rating. Individual
reviews are available through the retailer link when the retailer lists them.

## Cart and checkout details

These routes use the signed-in account ID. Cart and checkout details are shared
across independent sessions on the same deployment. Writes require CSRF.

| Method and path | Behavior |
| --- | --- |
| `GET /api/v1/cart` | List saved offers with candidate and search session IDs, merchant, image, and price. Captured offers leave the cart. |
| `PUT /api/v1/cart/items/{candidateId}` | Add an eligible candidate from the account's searches. Repeating the same request returns 204 without adding a duplicate. |
| `DELETE /api/v1/cart/items/{candidateId}` | Remove an offer from the signed-in account's cart. Returns 204. |
| `GET /api/v1/cart/details` | Retrieve saved delivery and billing addresses. |
| `PUT /api/v1/cart/details` | Save delivery and billing addresses. Returns the saved details. |

Both addresses use `fullName`, `line1`, `line2`, `city`, `region`, `postalCode`,
and a two-letter uppercase `countryCode`. `shipping` is required on save.
`billing` may be null when it matches delivery. PayPal handles payment credentials.

## Checkout and payment

The PayPal-oriented aliases below use the same authenticated checkout service,
saved product candidates, policy checks, budget reservations, and
`payment_transactions` records as the versioned checkout APIs. They do not
accept a client-supplied amount or price.

`POST /api/payments/create` requires `Idempotency-Key` and a CSRF token:

```json
{
  "searchSessionId": "c1be5d61-887d-4c72-9067-13222cfe624b",
  "candidateId": "769812c6-9805-42ec-af81-14ad731a28cb"
}
```

The response contains the PayPal `orderId`, `approvalUrl`, local checkout
session ID, amount, currency, and status. Redirect the buyer to `approvalUrl`.
After PayPal returns the buyer to the configured return URL, call capture.

```json
{
  "checkoutSessionId": "101",
  "orderId": "PAYPAL_ORDER_ID",
  "status": "APPROVAL_PENDING",
  "amount": "40.00",
  "currency": "USD",
  "approvalUrl": "https://www.sandbox.paypal.com/checkoutnow?token=PAYPAL_ORDER_ID",
  "captureId": null
}
```

`POST /api/payments/{orderId}/capture` captures the authenticated owner's
approved order. It requires a CSRF token and returns the local capture status,
capture ID, amount, and currency. PayPal capture details are checked against
the persisted reservation before the transaction is marked complete.

`GET /api/payments/{orderId}` retrieves the PayPal order status and reconciles
the owner-scoped local record. Completed status is persisted only after the
PayPal reference, capture ID, amount, and currency match the local checkout.
Provider timeouts or mismatches leave the reservation held for reconciliation.

The existing checkout cancellation operation remains available at
`POST /api/v1/checkout-sessions/{checkoutSessionId}/cancel`. A reservation is
released only after PayPal confirms that no capture has completed.

PayPal calls use OAuth client credentials (`POST /v1/oauth2/token`), Orders
v2 (`POST /v2/checkout/orders`, `GET /v2/checkout/orders/{id}`, and
`POST /v2/checkout/orders/{id}/capture`) against the official Sandbox host.
PayPal credentials must be provided through `PAYPAL_CLIENT_ID` and
`PAYPAL_CLIENT_SECRET`; never put them in request bodies or frontend code.

`POST /api/v1/checkout-sessions` requires an `Idempotency-Key` header and a
candidate from the signed-in user's search session:

```json
{
  "searchSessionId": "c1be5d61-887d-4c72-9067-13222cfe624b",
  "candidateId": "769812c6-9805-42ec-af81-14ad731a28cb"
}
```

The backend refreshes the Channel3 offer, checks its current price and
availability, reapplies the saved and request-specific limits, reserves weekly
budget/order capacity, then creates a PayPal Sandbox order. Frontend-provided
prices are never accepted.

```json
{
  "checkoutSessionId": "101",
  "status": "APPROVAL_PENDING",
  "amount": "40.00",
  "currency": "USD",
  "approvalUrl": "https://www.sandbox.paypal.com/checkoutnow?token=...",
  "policyResult": {
    "eligible": true,
    "message": "Checkout is allowed by the active spending policy."
  }
}
```

Capture after PayPal returns the user to Spendwise:
`POST /api/v1/checkout-sessions/{id}/capture`.
Cancel a checkout with `POST /api/v1/checkout-sessions/{id}/cancel`; reservation
release is allowed only after PayPal confirms there is no completed capture.
Reservations that expire are excluded from available-budget calculations.
Unconfirmed provider results retain their reservation and are returned as
`REQUIRES_RECONCILIATION`.

Configure `CHANNEL3_API_KEY`, `PAYPAL_CLIENT_ID`, and `PAYPAL_CLIENT_SECRET`.
The default PayPal base URL is Sandbox. Optional `PAYPAL_RETURN_URL` and
`PAYPAL_CANCEL_URL` point to the frontend's checkout routes. A PayPal Sandbox
payment is a demonstration payment to Spendwise; it does not purchase the
displayed item from its retailer.

## Transaction dashboard

`GET /api/v1/transactions` returns the authenticated user's transactions,
newest first.

`GET /api/v1/transactions/summary` returns completed weekly spend, active
reservations, remaining budget, completed orders, and the order limit for the
week calculated in the policy time zone.

## Shopping profile and search history

`GET /api/v1/shopping/preferences` returns the signed-in user's saved profile.
`PUT /api/v1/shopping/preferences` replaces it with `preferredCategories`,
`preferredBrands`, `preferredRetailers`, `keywords`, and an optional `maxPrice`
and `currencyCode` pair. Lists default to empty when omitted. A budget and its
currency must be supplied together. These values are user-owned profile data;
the spending policy remains authoritative at search and checkout.

`GET /api/v1/shopping/search-history?page=0&size=20` returns the signed-in user's
search sessions newest first. `size` is limited to 1–100. Each entry includes
the submitted and normalized query, effective maximum price, and timestamp.
Search history is scoped to the authenticated account.

Example preferences request:

```json
{
  "preferredCategories": ["laptops"],
  "preferredBrands": ["Lenovo", "Dell"],
  "preferredRetailers": ["store.example"],
  "keywords": ["lightweight", "long battery life"],
  "maxPrice": 1200.00,
  "currencyCode": "USD"
}
```

## Errors

Errors use a stable envelope:

```json
{
  "code": "POLICY_LIMIT_EXCEEDED",
  "message": "The order exceeds the maximum order amount.",
  "details": {"limit":"100.00","currency":"USD"}
}
```

Common codes include `VALIDATION_FAILED`, `POLICY_NOT_FOUND`,
`POLICY_LIMIT_EXCEEDED`, `ORDER_FREQUENCY_LIMIT_EXCEEDED`,
`PRODUCT_PROVIDER_NOT_CONFIGURED`, `PRODUCT_PROVIDER_TIMEOUT`,
`PRODUCT_PROVIDER_ERROR`, `CHANNEL3_RATE_LIMIT_EXCEEDED`,
`GEMINI_NOT_CONFIGURED`, `GEMINI_TIMEOUT`, `GEMINI_PROVIDER_ERROR`,
`GEMINI_INVALID_INTENT`,
`PAYMENT_PROVIDER_NOT_CONFIGURED`, and `PAYMENT_CAPTURE_UNCONFIRMED`.

Authentication, authorization, and application errors use the same `{code,
message, details}` JSON envelope. Rate limits include `Retry-After`. Gemini calls
are limited to 20 requests per minute per signed-in user; Channel3 calls are
limited to 100 per minute. Provider connect/read timeouts default to 5/20
seconds and can be changed with `SPENDWISE_HTTP_CONNECT_TIMEOUT_SECONDS` and
`SPENDWISE_HTTP_READ_TIMEOUT_SECONDS`. Channel3 searches and lookups, Gemini
generation, PayPal authentication, and webhook verification retry at most once
on HTTP 429 or 5xx transient responses. Uncertain network failures are not
retried for Gemini or PayPal payment operations.

## PayPal webhook

Register `POST /api/webhooks/paypal` in the PayPal Developer Dashboard and set
the resulting webhook ID as `PAYPAL_WEBHOOK_ID`. The listener verifies the
PayPal transmission headers and complete event through PayPal's
`/v1/notifications/verify-webhook-signature` API. Invalid signatures return
`401 PAYPAL_WEBHOOK_INVALID`; verification outages return a retryable 5xx
response. Verified checkout/capture events trigger an authenticated PayPal order
status lookup. Spendwise updates payment state only after the live status,
reference, amount, currency, and capture ID match its stored checkout. Duplicate
events only repeat the idempotent reconciliation lookup.

## Test and load workflows

- `mvn -f backend/pom.xml test` runs unit, controller, mocked provider, and
  PostgreSQL Testcontainers tests. The PostgreSQL test is skipped automatically
  when Docker is unavailable.
- `mvn -f backend/pom.xml verify -DskipProviderITs=false` also discovers the
  separate provider connectivity test. Set `SPENDWISE_EXTERNAL_INTEGRATION_TESTS=true`
  and provide Gemini, Channel3, and PayPal Sandbox credentials to run it.
- `k6 run load-tests/spendwise-api.js` exercises the authenticated search-history
  endpoint. Set `SPENDWISE_BASE_URL` and `SPENDWISE_SESSION_COOKIE` first; use
  `SPENDWISE_LOAD_PATH` to target another authenticated read endpoint.
