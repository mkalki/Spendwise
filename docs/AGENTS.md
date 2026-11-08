# Spendwise --- Codex Project Instructions

## Project context

Spendwise is a collaborative cloud-connected finance and
expense-management application.

This repository currently uses: - Java 21 - Spring Boot - PostgreSQL
through standard JDBC configuration - Spring Data JPA / Hibernate - Flyway for database
migrations - Docker and Docker Compose - GitHub for version control and
pull-request collaboration

The backend is built as a Spring Boot application and connects to
PostgreSQL through environment variables. Docker Compose provides a local
PostgreSQL service; deployments can use any compatible PostgreSQL host.

Treat the existing codebase, README, migrations, and configuration as
the source of truth. Do not assume a feature exists merely because it is
common in a finance application.

## How to work in this repository

1.  Inspect the relevant code and project structure before proposing or
    making changes.
2.  Follow existing package structure, naming, formatting, and
    architectural patterns unless there is a clear reason to improve
    them.
3.  Prefer small, focused changes over broad rewrites.
4.  Do not introduce new libraries, frameworks, services, or
    infrastructure without explaining why they are needed.
5.  Do not duplicate existing functionality. Search the codebase before
    adding a new class, utility, endpoint, or configuration.
6.  If requirements are ambiguous or a decision affects architecture,
    data integrity, security, or team workflow, ask one focused question
    before proceeding.
7.  Explain important design choices briefly. Keep explanations
    practical and concise.
8.  Do not claim a change works unless you have run the relevant checks
    or clearly state that it has not been verified.

## Java and Spring Boot conventions

-   Use Java 21-compatible language features and APIs.
-   Follow Spring Boot conventions and the patterns already established
    in the project.
-   Keep controllers focused on HTTP concerns; place business logic in
    services.
-   Use DTOs for API request and response boundaries rather than
    exposing persistence entities directly.
-   Use constructor injection; avoid field injection.
-   Validate incoming request data at the API boundary using the
    project's established validation approach.
-   Use clear, domain-oriented names for classes, methods, fields, and
    endpoints.
-   Keep methods cohesive and avoid unnecessary abstractions.
-   Handle errors consistently using the project's existing
    exception-handling approach.
-   Avoid leaking internal exception details, credentials, or sensitive
    financial data in API responses and logs.
-   Use transactions where a business operation requires atomic database
    changes.
-   Do not add Lombok or another code-generation dependency unless the
    project already uses it or the change is explicitly approved.

## Database and persistence rules

-   Local development uses the PostgreSQL service in Docker Compose. Deployed environments may use any compatible PostgreSQL server.
-   Read the existing Flyway migrations and entity mappings before
    changing the schema.
-   Treat Flyway migrations as the source of truth for schema evolution.
-   For schema changes, create a new versioned migration; do not edit a
    migration that may already have been applied to a shared database.
-   Keep Hibernate schema validation enabled in normal application
    configuration. Do not use `ddl-auto=update` or `create` as a
    substitute for migrations.
-   Never run destructive database operations such as dropping tables,
    truncating data, or running Flyway clean against the shared database
    unless the user explicitly requests it and confirms the impact.
-   Consider foreign keys, unique constraints, nullability, indexes, and
    transaction boundaries when designing persistence changes.
-   Do not assume local PostgreSQL is available. Check the current
    Docker Compose and application configuration.

## Secrets and configuration

-   Never hardcode database passwords, API keys, JWT secrets, OAuth
    credentials, or other secrets.
-   Read secrets from environment variables or the repository's
    established secret-management mechanism.
-   Keep the real `.env` file out of Git. Do not print its contents or
    copy secrets into source code, logs, documentation, tests, or
    command output.
-   Update `.env.example` with placeholder values when adding a new
    required environment variable.
-   Do not overwrite or expose a user's existing local configuration
    without checking first.
-   Keep PostgreSQL connection settings in environment variables and preserve compatibility with standard PostgreSQL JDBC URLs.

## Docker and runtime

-   Inspect the existing `Dockerfile` and `docker-compose.yml` before
    changing container configuration.
-   Preserve the current environment-variable-based configuration.
-   Do not add a local PostgreSQL container unless the user asks for one
    or the architecture is intentionally being changed.
-   Keep containers and images as small and reproducible as practical.
-   When changing runtime configuration, explain how to rebuild and
    restart the affected service.
-   Verify startup and health using the health endpoint actually
    implemented by the application; do not assume an endpoint exists.

## Testing and verification

-   Inspect existing tests and follow their conventions.
-   Add or update tests for changed behavior, especially business rules,
    validation, authorization, and persistence behavior.
-   Prefer focused unit tests for isolated business logic and
    integration tests when database or Spring wiring is involved.
-   Keep tests independent of shared databases; use mocks or an isolated PostgreSQL instance.
-   Run the most relevant tests first, then broader checks when
    practical.
-   Report exactly which commands were run and whether they passed. If a
    check could not be run, say why.

## Security and financial-data care

-   Treat financial records and account information as sensitive.
-   Enforce authorization on the server; never rely only on frontend
    checks.
-   Ensure users can access only data they are authorized to access.
-   Validate ownership and access rules when reading, updating, or
    deleting user-associated records.
-   Avoid logging transaction details or personal information unless
    necessary and safe.
-   Do not weaken authentication, authorization, validation, or database
    constraints to make a test pass.

## Git and team workflow

-   Work on a focused feature or fix branch; do not commit directly to
    `main`.
-   Follow the repository's pull-request and branch-protection workflow.
-   Use clear commit messages, preferably in the form
    `type(scope): description`.
-   Do not force-push, rewrite shared history, delete branches, reset
    branches, or discard working-tree changes unless explicitly
    requested.
-   Before any destructive Git command, inspect the working tree and
    explain its effect.
-   Do not bypass required reviews or branch protections.
-   Keep changes scoped to the task. Do not include `.env`, build
    output, IDE files, or unrelated modifications in commits.

## Working style

-   Before implementing a substantial feature, briefly state the
    proposed approach and the files or layers likely to change.
-   For a small, clear task, proceed without unnecessary planning.
-   When debugging, identify the failing layer and use the available
    logs or tests rather than guessing.
-   Prefer explaining the underlying concept when it helps the developer
    understand the change.
-   At the end, summarize:
    -   what changed,
    -   important design decisions,
    -   tests or checks performed,
    -   any remaining risks or follow-up work.

## Instruction precedence

-   Follow the user's explicit task requirements when they do not
    conflict with security or repository safety.
-   Follow this file together with any more specific `AGENTS.md` files
    in subdirectories.
-   Treat actual repository files and current configuration as
    authoritative when this document is stale; call out discrepancies
    and suggest updating this file.
