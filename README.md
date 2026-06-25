# SocialHub Backend

Multi-tenant social media management & analytics platform — Spring Boot foundation.

- **Java 21**, **Spring Boot 4.0.x**, **Maven**
- **PostgreSQL** + Spring Data JPA + **HikariCP**
- **Flyway** migrations
- **Spring Security** scaffolding (permit-all for now; SSO plugs in later)
- **MapStruct** DTO mapping, **springdoc** OpenAPI/Swagger UI

## Architecture

Layered: **controller (`web`) → service → repository**, with each social platform
isolated behind a common interface so new integrations are additive.

```
com.socialhub.socialhubBackend
├── config        SecurityConfig, JpaAuditingConfig, OpenApiConfig, AppProperties
├── common        BaseEntity / TenantBaseEntity, exceptions + GlobalExceptionHandler,
│                 ApiResponse / ErrorResponse wrappers
├── tenant        Organization (the tenant root), TenantContext(+Filter), full
│                 controller→service→repo→mapper→DTO reference slice
├── user          User model + repository (auth wired later)
├── integration
│   ├── core      SocialMediaProvider interface, AbstractSocialMediaProvider,
│   │             SocialMediaProviderRegistry, SocialAccount entity, shared DTOs
│   ├── facebook  FacebookProvider (stub)
│   ├── instagram InstagramProvider (stub)
│   └── whatsapp  WhatsAppProvider (stub)
├── post          unified Post model + PostSyncService placeholder
└── analytics     AnalyticsService + endpoint (structure only)
```

### Multi-tenancy
`Organization` is the tenant root. Tenant-scoped entities extend
`TenantBaseEntity` (adds `organization_id`); shared audit fields (`id`,
`created_at`, `updated_at`, `created_by`) live in `BaseEntity` with JPA auditing
enabled (`JpaAuditingConfig`). `TenantContext` holds the active tenant per
request; `TenantContextFilter` currently reads it from the `X-Organization-Id`
header (a dev placeholder — see SSO note).

### Security / SSO (not implemented yet)
`SecurityConfig` is stateless and **permits all requests** so development isn't
blocked. Every extension point is marked `TODO[SSO]`: add an OAuth2/OIDC resource
server, swap `permitAll()` for real rules, wire a JWT→authorities+tenant
converter, and resolve `AuditorAware`/`TenantContext` from the authenticated
principal.

## Adding a new social platform

No changes needed outside the new package:

1. Add the constant to `integration.core.SocialPlatform`.
2. Create `integration.<platform>` with a `@Component` extending
   `AbstractSocialMediaProvider`, implementing `platform()` (override the real
   operations as you build them).

Spring discovers the bean and `SocialMediaProviderRegistry` registers it
automatically. Verify with `GET /api/v1/integrations/providers`.

## Configuration

Config is externalized via env vars (see `.env.example`); profiles are `dev`
(default) and `prod`. Key vars: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`,
`CORS_ALLOWED_ORIGINS`, `SERVER_PORT`, `SPRING_PROFILES_ACTIVE`.

## Running locally

1. **Create the role + database** with the provisioning script (idempotent;
   re-run it whenever you change DB settings):
   ```bash
   ./scripts/init-db.sh
   # or with different settings:
   DB_NAME=socialhub_prod DB_USERNAME=app DB_PASSWORD=secret ./scripts/init-db.sh
   ```
   It reads `DB_NAME`/`DB_USERNAME`/`DB_PASSWORD` (defaults match `application.yml`)
   and prompts for sudo to act as the `postgres` superuser.
2. **Run** (Flyway applies migrations on startup):
   ```bash
   ./mvnw spring-boot:run
   ```
3. Verify:
   - `GET http://localhost:8080/api/v1/integrations/providers` → FACEBOOK / INSTAGRAM / WHATSAPP
   - `GET http://localhost:8080/api/v1/organizations` → `[]`
   - Swagger UI: `http://localhost:8080/swagger-ui.html`
   - Health: `http://localhost:8080/actuator/health`

Build / test: `./mvnw clean package` (tests run against in-memory H2 via the
`test` profile, so no Postgres is needed to build).

## Endpoints (foundation)

| Method | Path                              | Purpose                          |
|--------|-----------------------------------|----------------------------------|
| GET    | `/api/v1/organizations`           | List organizations               |
| GET    | `/api/v1/organizations/{id}`      | Get one organization             |
| GET    | `/api/v1/analytics/summary`       | Aggregated analytics (placeholder) |
| GET    | `/api/v1/integrations/providers`  | List selectable platforms (+ enabled flag) |
| GET    | `/api/v1/integrations`            | List connected integrations (org-scoped) |
| POST   | `/api/v1/integrations`            | Connect a platform (validates credentials) |
| DELETE | `/api/v1/integrations/{id}`       | Disconnect an integration        |
| GET    | `/api/v1/integrations/{id}/posts` | List posts (cursor pagination)   |
| POST   | `/api/v1/integrations/{id}/posts` | Create a post                    |

Integration requests are organization-scoped (via `X-Organization-Id` for now;
see SSO note). Access tokens are encrypted at rest and never returned.
# socialHubBack
