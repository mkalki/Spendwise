# SPENDWISE — Product Requirements Document (PRD)

**Version:** 1.0  
**Date:** November 8, 2026  
**Status:** MVP Development  
**Project type:** PayPal AI Hackathon  
**Architecture:** Modular Monolith  
**Backend:** Java 21 + Spring Boot 3.5.x

---

# 1. Product Overview

## 1.1 Product name

**Spendwise — AI Shopping Assistant with Enforced Spending Guardrails**

## 1.2 Product vision

Spendwise is an AI-powered shopping assistant that helps users discover, compare and select products while enforcing predefined spending restrictions.

Unlike conventional AI shopping assistants, Spendwise does not allow the AI model to independently authorize purchases.

All financial restrictions are enforced by a deterministic backend policy engine.

### Core principle

**AI recommends. The policy engine decides. The user approves. PayPal processes the payment.**

## 1.3 Problem statement

AI shopping agents can search products and recommend purchases, but users may hesitate to give them purchasing authority without financial restrictions.

Potential problems include:

- Purchasing products above the intended budget.
- Exceeding weekly spending limits.
- Making too many purchases.
- Selecting products that violate user preferences.
- AI incorrectly interpreting financial instructions.
- Unauthorized or duplicate payment attempts.

Spendwise solves these problems by combining AI-powered product discovery with backend-enforced spending policies and user-controlled PayPal checkout.

---

# 2. Product Goals

The MVP must demonstrate the following:

1. Users can configure personal spending policies.
2. Users can describe shopping requirements using natural language.
3. AI can convert natural-language requests into structured shopping intent.
4. Channel3 can retrieve real product information and merchant offers.
5. The backend can filter products against spending policies.
6. AI can explain and compare eligible products.
7. Users can select products and initiate checkout.
8. PayPal Sandbox can process a clearly labelled demonstration payment.
9. Transactions and policy decisions are recorded.
10. Spending restrictions cannot be bypassed through AI prompts or frontend manipulation.

## 2.1 Out of scope

The following are excluded from the MVP:

- Actual purchases from external retailers.
- Integration with retailer-specific checkout systems.
- Multiple AI agents.
- Microservices.
- Kafka or RabbitMQ.
- Redis.
- Mobile application.
- Complex multi-user authentication.
- Real-money payments.
- Advanced price prediction.
- Multi-item shopping carts.
- Automated purchasing without user approval.

**Important product limitation:** Channel3 retailer links and PayPal Sandbox payments are separate. The PayPal Sandbox transaction is a demonstration payment to the Spendwise demo merchant. It does not purchase the displayed product from its retailer.

---

# 3. Target Users

## Primary user: Budget-conscious online shopper

A user who wants AI to discover products without exceeding personal spending limits.

## Secondary user: Frequent online shopper

A user who wants to compare products, control weekly spending and maintain a purchase history.

## User stories

- As a user, I want to configure a maximum price per product so that expensive products are automatically rejected.
- As a user, I want to define a maximum order amount so that individual purchases cannot exceed my limit.
- As a user, I want to configure a weekly budget so that my total spending remains controlled.
- As a user, I want to limit the number of purchases I can make each week.
- As a user, I want to describe products in natural language instead of using complicated filters.
- As a user, I want to compare eligible products before selecting one.
- As a user, I want to approve payments personally through PayPal.
- As a user, I want to see my previous purchases and rejected purchase attempts.

---

# 4. Functional Requirements

## FR-01: Spending Policy Management

Users must be able to configure and update their spending policies.

### Policy attributes

| Field | Description |
|---|---|
| maxProductPrice | Maximum permitted price for one product |
| maxOrderAmount | Maximum permitted order amount |
| weeklyBudget | Maximum spending allowed per week |
| maxOrdersPerWeek | Maximum successful orders per week |
| countryCode | User's selected shopping country |
| currencyCode | Shopping currency |
| timezone | Time zone used to calculate weekly limits |

### Requirements

- All monetary values must use Java `BigDecimal`.
- Prices must use the same currency as the active policy.
- Invalid or negative amounts must be rejected.
- Weekly budget and order-count restrictions must be evaluated using persisted transaction and reservation records.
- The policy must survive application restarts.

The user's natural-language request may impose a stricter limit than their saved policy, but must never weaken it.

Example:

Saved policy: Maximum product price = $60.

User request: Find a product under $45.

Effective maximum: $45.

If the user asks for a $100 product, the effective maximum remains $60.

---

## FR-02: Natural-Language Shopping Request

Users must be able to submit requests such as:

"Find wireless mechanical keyboards with good battery life under $60."

The AI must extract structured intent.

### ShoppingIntent

Expected fields:

- normalizedQuery
- category
- keywords
- requiredFeatures
- preferredBrand (optional)
- requestedMaxPrice (optional)
- currency
- countryCode

The result must be mapped into a validated Java DTO.

The AI is responsible for understanding the shopping request, not authorizing a payment or changing user policies.

---

## FR-03: Channel3 Product Discovery

Use the Channel3 REST API to retrieve product data.

### Integration responsibilities

- Search products using natural-language queries.
- Apply available structured product filters.
- Retrieve product details.
- Retrieve merchant offer information.
- Extract prices, currency, images and retailer links.
- Normalize external API responses into internal DTOs.

### API integration

Base URL:

`https://api.trychannel3.com`

Primary endpoint:

`POST /v1/search`

Authentication:

`x-api-key: CHANNEL3_API_KEY`

Product details:

`GET /v1/products/{product_id}`

The API key must only be available to the backend.

Channel3 product data must be mapped into internal Spendwise DTOs. Do not expose raw external API objects throughout the application.

Search requests should use the configured policy country and currency explicitly.

Before checkout, retrieve the latest product/offer information and revalidate the price.

Do not invent unavailable prices, merchants or URLs.

---

## FR-04: Policy Engine

This is the most important business component of Spendwise.

The Policy Engine is responsible for authorizing or rejecting checkout eligibility.

It must be deterministic and independent of AI-generated decisions.

### Product-level validation

Reject a product if:

`productPrice > maxProductPrice`

### Order-level validation

Reject a checkout if:

`orderAmount > maxOrderAmount`

### Weekly budget validation

Calculate:

`remainingBudget = weeklyBudget - capturedWeeklySpend - activeReservations`

Reject a checkout if:

`newReservationAmount > remainingBudget`

### Order-frequency validation

Reject a checkout if:

`completedOrders + activeReservations >= maxOrdersPerWeek`

### Other validation rules

- Reject products without a valid price or currency.
- Reject products without a valid retailer offer.
- Reject unsupported currencies.
- Reject stale product prices.
- Do not allow frontend-provided prices to override backend data.
- Recheck all restrictions before creating a PayPal order.

The weekly period must be calculated using the time zone configured in the user's policy.

### Concurrency requirement

Two simultaneous checkout requests must not reserve the same remaining budget.

Use database transactions and appropriate locking or atomic reservation logic.

A checkout reservation must be created before calling PayPal.

If payment is cancelled or expires, release the reservation.

If the payment result is uncertain due to a network timeout, reconcile its status before releasing the reservation.

---

## FR-05: Product Comparison and Recommendations

Spendwise must compare products that have passed backend policy validation.

Comparison factors:

- Price.
- Product features.
- Brand.
- Merchant.
- Availability information, when provided.
- Match with the user's shopping intent.

AI may generate explanations and rankings.

However:

- AI cannot invent products.
- AI cannot invent prices.
- AI cannot modify merchant information.
- AI cannot introduce products that were excluded by PolicyEngine.
- AI-generated product identifiers must be checked against the actual candidate list.

The original Channel3 product data remains the source of truth for displayed commercial information.

---

## FR-06: Checkout Management

When the user selects an eligible product:

1. Backend retrieves the stored product candidate.
2. Backend refreshes the Channel3 product/offer details.
3. Backend verifies the current price and currency.
4. Backend reevaluates all spending policies.
5. Backend reserves the required budget.
6. Backend creates a PayPal Sandbox order.
7. Frontend receives the approval URL.
8. User approves the payment through PayPal.
9. Frontend returns to Spendwise.
10. Backend captures and verifies the payment.
11. Transaction is stored in PostgreSQL.

The frontend must never determine whether checkout is approved.

---

## FR-07: Payment Integration

Use the PayPal Orders v2 REST API.

### Authentication

OAuth 2.0 Client Credentials.

Required environment variables:

- PAYPAL_BASE_URL
- PAYPAL_CLIENT_ID
- PAYPAL_CLIENT_SECRET

Sandbox base URL:

`https://api-m.sandbox.paypal.com`

Obtain access tokens through:

`POST /v1/oauth2/token`

### Create order

Endpoint:

`POST /v2/checkout/orders`

Use:

- Intent: CAPTURE
- Amount: computed from the backend checkout session
- Currency: selected policy currency
- Return URL: Spendwise frontend checkout return route
- Cancel URL: Spendwise frontend cancellation route

The backend must extract the approval URL from the returned PayPal links.

### Capture payment

Endpoint:

`POST /v2/checkout/orders/{orderId}/capture`

Capture only after the user approves the order.

### Payment verification

Verify:

- PayPal order ID.
- Capture ID.
- Capture status.
- Amount.
- Currency.
- Merchant/payee information where applicable.

Only a verified completed capture can transition a checkout into the successful payment state.

### Idempotency

Use `PayPal-Request-Id` for applicable PayPal POST operations.

Maintain application-level idempotency as well.

Repeated capture requests must not create duplicate transactions.

### Important

PayPal credentials must never be exposed to React.

All PayPal API operations must be performed by Spring Boot.

---

## FR-08: Transaction Management

Store:

- Internal checkout session ID.
- Channel3 product ID.
- Selected offer information.
- Product price snapshot.
- PayPal order ID.
- PayPal capture ID.
- Amount.
- Currency.
- Payment status.
- Created and updated timestamps.

Display transaction history to the user.

Failed or cancelled transactions must not be counted as completed spending.

Active reservations must count against the user's available budget until completed, cancelled or expired.

---

## FR-09: Audit Trail

Record important business decisions.

Examples:

- POLICY_CREATED
- POLICY_UPDATED
- SHOPPING_SEARCH_STARTED
- PRODUCT_REJECTED
- PRODUCT_APPROVED
- CHECKOUT_RESERVED
- PAYPAL_ORDER_CREATED
- PAYMENT_CAPTURED
- PAYMENT_CANCELLED
- PAYMENT_FAILED

Each event should include:

- Event ID.
- Owner ID.
- Event type.
- Related entity ID.
- Timestamp.
- Relevant reason or metadata.

Never store API secrets or payment credentials in audit events.

---

# 5. System Architecture

## 5.1 Architecture style

Modular Monolith.

Characteristics:

- One Spring Boot application.
- One deployment unit.
- One Supabase PostgreSQL database.
- Feature-oriented modules.
- Clear separation between domain logic and external API integrations.
- Communication between internal modules through application services and interfaces.

No microservices are required.

## 5.2 Backend module structure

Recommended package organization:

```text
com.spendwise
│
├── SpendwiseApplication.java
│
├── common/
│   ├── config/
│   ├── exception/
│   └── response/
│
├── policy/
│   ├── controller/
│   ├── service/
│   ├── entity/
│   ├── repository/
│   └── engine/
│
├── product/
│   ├── controller/
│   ├── service/
│   ├── dto/
│   └── integration/
│
├── ai/
│   ├── controller/
│   ├── service/
│   ├── dto/
│   └── config/
│
├── payment/
│   ├── controller/
│   ├── service/
│   ├── dto/
│   └── integration/
│
├── checkout/
│   ├── controller/
│   ├── service/
│   ├── entity/
│   └── repository/
│
└── transaction/
    ├── controller/
    ├── service/
    ├── entity/
    └── repository/
```

Do not create additional modules without a business requirement.

## 5.3 Technology stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.5.x |
| Build | Maven |
| Database | Supabase PostgreSQL |
| ORM | Spring Data JPA |
| Migrations | Flyway |
| AI integration | Spring AI 1.1.x |
| Product discovery | Channel3 REST API |
| Payments | PayPal Orders v2 |
| HTTP communication | Spring RestClient |
| Frontend | React + Vite |
| Styling | Tailwind CSS |
| Containers | Docker + Docker Compose |
| Testing | JUnit 5 + Mockito |
| API specification | OpenAPI |
| API developer documentation | APIMatic |
| API testing | Postman |

Keep Spring Boot and Spring AI versions compatible. Do not perform unrelated framework upgrades during MVP development.

---

# 6. Database Design

Use Supabase PostgreSQL as the primary database.

Required domain entities:

## 6.1 SpendingPolicy

Suggested fields:

- id
- ownerId
- maxProductPrice
- maxOrderAmount
- weeklyBudget
- maxOrdersPerWeek
- countryCode
- currencyCode
- timezone
- active
- createdAt
- updatedAt

## 6.2 SearchSession

Suggested fields:

- id
- ownerId
- originalQuery
- normalizedQuery
- parsedIntent (JSONB)
- createdAt

## 6.3 ProductCandidate

Suggested fields:

- id
- searchSessionId
- channel3ProductId
- productTitle
- brand
- imageUrl
- merchantName
- offerUrl
- listedPrice
- currencyCode
- eligible
- rejectionReason
- retrievedAt

A candidate must be associated with its search session so the backend can validate the selected product without trusting frontend data.

## 6.4 CheckoutSession

Suggested fields:

- id
- ownerId
- productCandidateId
- amount
- currencyCode
- paypalOrderId
- status
- reservationExpiresAt
- idempotencyKey
- createdAt
- updatedAt

Suggested statuses:

- RESERVED
- APPROVAL_PENDING
- CAPTURED
- CANCELLED
- EXPIRED
- FAILED
- REQUIRES_RECONCILIATION

## 6.5 Transaction

Suggested fields:

- id
- checkoutSessionId
- paypalOrderId
- paypalCaptureId
- amount
- currencyCode
- status
- createdAt

Use uniqueness constraints for PayPal order and capture identifiers where appropriate.

## 6.6 AuditEvent

Suggested fields:

- id
- ownerId
- eventType
- entityType
- entityId
- metadata (JSONB)
- createdAt

### Database rules

- Use Flyway for all schema changes.
- Store migrations in `backend/src/main/resources/db/migration/`.
- Never edit an already-applied migration.
- Use new versioned migration files.
- Keep Hibernate in `validate` mode.
- Use appropriate indexes and unique constraints.
- Use `BigDecimal` for monetary values.
- Use UTC timestamps for persisted events and the configured policy time zone for weekly calculations.

Preserve the existing Supabase connection configuration.

---

# 7. REST API Design

Use `/api/v1` as the API base path.

## 7.1 Spending policies

`GET /api/v1/policies/active`

Retrieve the active spending policy.

`PUT /api/v1/policies/active`

Create or update the spending policy.

## 7.2 Shopping search

`POST /api/v1/shopping/search`

Request:

```json
{
  "message": "Find wireless mechanical keyboards under $60 with good battery life"
}
```

Backend responsibilities:

1. Parse the shopping intent using AI.
2. Validate the intent.
3. Retrieve the active spending policy.
4. Call Channel3.
5. Filter candidates through PolicyEngine.
6. Generate recommendations for eligible products.
7. Return search results.

Response should include:

- Search session ID.
- Normalized shopping intent.
- Eligible product candidates.
- Rejected candidates and reasons, where appropriate.
- AI recommendation and explanation.

## 7.3 Checkout creation

`POST /api/v1/checkout-sessions`

Request:

```json
{
  "searchSessionId": "SEARCH-101",
  "candidateId": "CANDIDATE-101"
}
```

Do not accept trusted price, amount or PayPal order ID from the frontend.

Response:

```json
{
  "checkoutSessionId": "CHECKOUT-101",
  "status": "APPROVAL_PENDING",
  "amount": "45.00",
  "currency": "USD",
  "approvalUrl": "https://www.sandbox.paypal.com/checkoutnow?token=..."
}
```

## 7.4 Capture payment

`POST /api/v1/checkout-sessions/{id}/capture`

The backend retrieves the associated PayPal order and captures it after approval.

## 7.5 Cancel checkout

`POST /api/v1/checkout-sessions/{id}/cancel`

Release the reservation when cancellation is verified.

## 7.6 Transaction history

`GET /api/v1/transactions`

`GET /api/v1/transactions/summary`

## 7.7 Health

Preserve the existing health endpoint:

`GET /api/health`

---

# 8. AI Integration Requirements

Use Spring AI with a configurable model provider.

AI capabilities:

1. Shopping intent extraction.
2. Product recommendation.
3. Product comparison.
4. Natural-language explanations.

## AI architecture

Create dedicated services:

- ShoppingIntentService
- ProductRecommendationService
- ProductComparisonService

Use typed Java DTOs for structured model outputs.

The model should return structured intent rather than arbitrary text that downstream services must interpret.

Validate all model outputs before using them.

Prompt design must explicitly prevent the model from:

- Changing user spending policies.
- Approving purchases.
- Initiating PayPal payments.
- Inventing product identifiers.
- Inventing product prices.
- Overriding backend policy decisions.

The AI must receive only the information necessary for its task.

---

# 9. Channel3 Integration Requirements

Create a dedicated `Channel3Client` adapter.

Use Spring RestClient for HTTP requests.

Responsibilities:

- API authentication using the `x-api-key` header.
- Product search.
- Product detail retrieval.
- Response mapping.
- Timeout handling.
- Error handling.
- Limited retry logic for transient failures.

Never send the Channel3 API key to the browser.

Use official Channel3 documentation to confirm request and response schemas. Do not guess JSON field names.

---

# 10. PayPal Integration Requirements

Create a `PayPalClient` adapter and a `PaymentGateway` interface.

Suggested responsibilities:

- PayPalAuthService
- PayPalOrderService
- PayPalCaptureService
- PayPalPaymentGateway

The checkout application service should interact with the `PaymentGateway` abstraction rather than directly calling PayPal-specific HTTP code.

This enables testing with mocked payment responses.

Use PayPal Sandbox credentials stored in environment variables.

The application must maintain its own checkout state independently of PayPal's response.

---

# 11. Frontend Requirements

Build the following React screens.

## Screen 1: Spending Policy

- Maximum price per product.
- Maximum order amount.
- Weekly budget.
- Maximum orders per week.
- Country and currency.
- Save policy.

## Screen 2: AI Shopping Assistant

- Natural-language shopping input.
- AI interpretation of the request.
- Search button.
- Loading and error states.

## Screen 3: Product Discovery

- Product cards.
- Images.
- Merchant information.
- Price.
- Eligible/ineligible status.
- AI recommendation.
- Product comparison.

## Screen 4: Checkout

- Selected product.
- Listed price.
- Active policy.
- Policy validation result.
- Explicit demo payment warning.
- Continue to PayPal Sandbox.

## Screen 5: Transaction Dashboard

- Weekly spending.
- Remaining budget.
- Completed orders.
- Blocked purchase attempts.
- Transaction history.

The user must be able to understand why a purchase is rejected.

---

# 12. Security and Reliability

Mandatory requirements:

1. Store secrets in `.env` or deployment environment variables.
2. Keep `.env` excluded from Git.
3. Maintain `.env.example` with placeholder values.
4. Never expose PayPal or Channel3 credentials to React.
5. Never trust amounts received from frontend requests.
6. Never treat AI output as an authorization decision.
7. Verify PayPal captures on the backend.
8. Apply idempotency to checkout and payment operations.
9. Prevent concurrent budget overspending.
10. Add input validation and global exception handling.
11. Avoid logging credentials, tokens or unnecessary personal information.
12. Use HTTPS/TLS for external service and database communication.

Preserve the working Docker and Supabase configuration. Do not replace the cloud database with a local database without an explicit reason.

---

# 13. Testing Requirements

## Policy Engine unit tests

Test:

- Product within cap.
- Product above cap.
- Order above cap.
- Weekly budget exhausted.
- Weekly order limit reached.
- Natural-language requested limit stricter than saved policy.
- Wrong currency.
- Missing price.
- Concurrent checkout reservation.

## Channel3 integration tests

Test:

- Successful search.
- Empty search result.
- Missing offer.
- API failure.
- Timeout.
- Product detail refresh.
- Updated product price.

## AI tests

Test:

- Correct shopping intent extraction.
- Missing optional fields.
- Invalid structured output.
- Unsupported currency.
- Prompt injection attempting to bypass spending rules.
- AI recommendation containing an unknown product ID.

## PayPal tests

Test:

- Successful OAuth token acquisition.
- Successful order creation.
- Missing approval link.
- User cancellation.
- Successful payment capture.
- Failed capture.
- Duplicate capture.
- Mismatched amount or currency.
- Timeout during capture.

Use JUnit 5 and Mockito for unit testing.

Use Postman for external API verification.

Prioritize a complete end-to-end Sandbox payment test before submission.

---

# 14. Team Responsibilities

## Member 1 — Backend Lead

- Spring Boot architecture.
- Database configuration.
- JPA entities.
- Spending Policy module.
- PolicyEngine.
- Docker and Docker Compose.
- Checkout orchestration.
- Backend integration.

## Member 2 — Frontend

- React application.
- Policy configuration screens.
- Shopping assistant.
- Product cards.
- Checkout interface.
- Transaction dashboard.
- Backend API integration.

## Member 3 — AI and Product Discovery

- Channel3 API exploration and integration.
- Shopping intent extraction.
- Spring AI integration.
- Product recommendation.
- Product comparison.
- Response validation.

## Member 4 — PayPal and Quality Assurance

- PayPal Sandbox configuration.
- OAuth 2.0.
- Orders API integration.
- Order creation.
- Approval flow.
- Payment capture and verification.
- Idempotency.
- Postman collections.
- Payment testing.

All team members must follow agreed REST API contracts and use separate Git branches.

---

# 15. Development Milestones

## Milestone 1 — Foundation

- Existing Spring Boot setup retained.
- Supabase connection verified.
- Docker configuration verified.
- Channel3 API access verified.
- PayPal Sandbox access verified.
- AI provider verified.

## Milestone 2 — Product Discovery

- Shopping intent DTO implemented.
- Channel3 product search working.
- Product candidates stored or associated with a search session.
- Product filtering working.

## Milestone 3 — Financial Guardrails

- Policy CRUD working.
- PolicyEngine implemented.
- Weekly budget calculation working.
- Order frequency checks working.
- Reservations handled safely.

## Milestone 4 — AI and Recommendations

- AI extraction integrated.
- AI product comparison integrated.
- Recommendations restricted to real eligible candidates.

## Milestone 5 — PayPal Checkout

- Order creation working.
- Approval redirect working.
- Capture working.
- Payment verification working.
- Transactions persisted.

## Milestone 6 — End-to-End Application

- React integrated.
- Policy dashboard completed.
- Shopping journey completed.
- Transaction history completed.
- Testing and bug fixing completed.

## Milestone 7 — Submission

- README finalized.
- Architecture diagram added.
- Public repository verified.
- Open-source licence included.
- Demo video recorded.
- Devpost submission completed.

---

# 16. Documentation and Developer Experience

Maintain OpenAPI as the source of truth for the backend API contract.

Use Springdoc OpenAPI to generate the OpenAPI specification.

Expose:

`/v3/api-docs`

Export and retain the generated specification in:

`openapi/spendwise-openapi.json`

Use APIMatic for the published API documentation portal, API testing experience and optional SDK generation.

APIMatic is a development/documentation tool, not a runtime dependency.

---

# 17. Definition of Done

A feature is complete only when:

- Its business logic is implemented.
- Input validation exists.
- Error cases are handled.
- Unit tests pass.
- Integration with related modules works.
- The API contract is documented.
- Changes are committed to GitHub.
- Existing application functionality still works.

The MVP is considered complete when a user can:

1. Configure spending policies.
2. Enter a natural-language shopping request.
3. Receive real Channel3 products.
4. See products filtered by backend spending policies.
5. Compare eligible recommendations.
6. Select an eligible product.
7. Pass a fresh backend policy and price check.
8. Complete a PayPal Sandbox demonstration payment.
9. See the transaction reflected in the dashboard.
10. Verify that an attempted overspending request is rejected.

---

# 18. Instructions for AI Coding Agents

This section is mandatory for Codex, Qwen Code and other coding assistants working on Spendwise.

Treat this PRD as the product source of truth.

## Before writing code

1. Inspect the existing repository.
2. Read `README.md`, `AGENTS.md`, `QWEN.md`, `pom.xml` and Docker configuration where present.
3. Inspect the existing Supabase configuration.
4. Inspect current Flyway migrations.
5. Understand the existing health endpoint and application configuration.
6. Do not recreate or overwrite existing project setup.

## Implementation rules

- Use Java 21.
- Use the existing Spring Boot 3.5.x setup.
- Follow modular monolith architecture.
- Organize packages by feature.
- Use constructor injection.
- Use DTOs for API request and response models.
- Use Jakarta Bean Validation.
- Use `BigDecimal` for monetary calculations.
- Use Spring Data JPA.
- Use Flyway for schema changes.
- Use Spring RestClient for external REST integrations.
- Keep external API calls behind dedicated adapters.
- Use interfaces where external services must be replaceable or mocked.
- Write unit tests for business logic.
- Add integration tests for important workflows.
- Preserve existing Docker configuration.
- Preserve the working Supabase connection.
- Do not expose API credentials.
- Do not let AI decide whether a payment is permitted.
- Do not trust prices or payment status sent by the frontend.
- Do not create microservices.
- Do not introduce unnecessary dependencies.
- Do not implement features outside the MVP without approval.

## Database rules

- Never modify an already-applied Flyway migration.
- Create a new versioned migration for every schema change.
- Keep Hibernate schema management in validation mode.
- Do not manually create production tables outside Flyway.

## Coding workflow

For every feature:

1. Inspect existing code.
2. Explain the proposed design briefly.
3. Implement one meaningful feature.
4. Explain important concepts.
5. Write tests.
6. Run tests.
7. Review the changes.
8. Commit the implementation.

Do not generate the entire application in one step.

Prefer small, independently reviewable commits.

## Project principle

Always preserve this separation:

**AI recommends. PolicyEngine authorizes checkout eligibility. User approves payment. PayPal processes the Sandbox payment.**

Any implementation that violates this principle is incorrect.

---

# 19. Official Technical References

- PayPal REST API: https://developer.paypal.com/api/rest/
- PayPal Orders v2: https://developer.paypal.com/api/rest/integration/orders-api/
- Channel3 developer documentation: https://www.trychannel3.com/developers
- Channel3 API reference: https://docs.trychannel3.com/
- Spring AI: https://docs.spring.io/spring-ai/reference/1.1/
- Supabase Spring Boot integration: https://supabase.com/docs/guides/getting-started/quickstarts/spring-boot
- Springdoc OpenAPI: https://springdoc.org/
- APIMatic: https://docs.apimatic.io/

---

**END OF PRODUCT REQUIREMENTS DOCUMENT**

**Final engineering objective:** Deliver a complete, functional policy-driven AI shopping experience with genuine product discovery, independently enforced financial guardrails, explicit user approval and a verified PayPal Sandbox payment flow.