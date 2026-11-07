# Initial API contracts

Base URL: `http://localhost:8080/api`

Money values are decimal strings plus an ISO 4217 currency code. The server owns the authoritative product price and policy decision. The AI and browser may suggest or select a product, but neither may authorize checkout.

## Health

`GET /health`

```json
{"status":"ok","service":"spendwise-api"}
```

## Spending policy

`GET /policies/current` returns the active demo policy.

`PUT /policies/current` saves a policy:

```json
{
  "maxPerProduct": "60.00",
  "maxPerOrder": "100.00",
  "weeklyLimit": "150.00",
  "maxOrdersPerWeek": 1,
  "currency": "USD",
  "country": "US"
}
```

## Product search

`POST /shopping/search`

```json
{"query":"wireless mechanical keyboard under $60"}
```

Response shape:

```json
{
  "interpretedQuery": {"keywords":"wireless mechanical keyboard","maxPrice":"60.00","currency":"USD"},
  "products": [
    {
      "id":"provider-product-id",
      "name":"Example Wireless Keyboard",
      "merchant":"Example Store",
      "price":"45.00",
      "currency":"USD",
      "imageUrl":"https://example.test/image.jpg",
      "offerUrl":"https://example.test/product",
      "eligible":true,
      "reasons":[]
    }
  ]
}
```

## Checkout

`POST /checkout/sessions` accepts only a product candidate id and an idempotency key. The backend must refresh the offer and re-run policy checks before creating a PayPal Sandbox order. The browser must not supply an authoritative price.

```json
{"productId":"provider-product-id"}
```

The response should include a checkout session id, the verified amount/currency, policy result, and PayPal approval URL. A successful Sandbox capture is recorded as a demo payment; it does not claim to purchase the item from the retailer.

## Ownership and error shape

Channel3 owns product discovery. AI parses intent and explains comparisons. The policy engine owns eligibility. PayPal owns the Sandbox approval and capture. Use a stable error envelope for rejected actions:

```json
{"code":"POLICY_LIMIT_EXCEEDED","message":"The item exceeds the per-product limit.","details":{"limit":"60.00","currency":"USD"}}
```
