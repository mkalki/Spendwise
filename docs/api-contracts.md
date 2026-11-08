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
{"message":"Find wireless mechanical keyboards under $45 with good battery life"}
```

The backend sends the policy country/currency and the stricter of the requested
maximum and saved product-price cap to Channel3. Search results and offer
prices are stored against an owner-scoped search session. The response includes
only Channel3 data; rejected offers include a reason.

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
      "eligible": true
    }
  ],
  "rejectedCandidates": [],
  "recommendation": "Lowest-priced eligible offer: Example Wireless Keyboard from example.test."
}
```

The current intent parser extracts common textual price limits; it is not an
LLM integration. The recommendation is a deterministic lowest-price label, not
AI-generated advice. `CHANNEL3_API_KEY` must be configured for live search;
otherwise the endpoint returns `503 PRODUCT_PROVIDER_NOT_CONFIGURED`.

## Checkout and payment

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
`PAYMENT_PROVIDER_NOT_CONFIGURED`, and `PAYMENT_CAPTURE_UNCONFIRMED`.
