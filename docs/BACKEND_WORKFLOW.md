# Backend Workflow & Request Flow

How a request travels through the SocialHub backend, end to end. Pairs with the
architecture rules in the root `AGENTS.md`.

- **Stack:** Spring Boot 4 / Java 21, Spring MVC, Spring Data JPA + PostgreSQL,
  Flyway, Spring Security (permit-all for now), MapStruct, springdoc.
- **Layering:** `web (controller)` → `service` → `repository` / `provider`.
  Controllers do HTTP only; services hold logic; repositories/providers do I/O.

---

## 1. Layers & packages

```
HTTP ──► config (filters, security, CORS)
          │
          ▼
    web / controller        OrganizationController, IntegrationController,
          │                 FacebookOAuthController, AnalyticsController
          ▼
       service              IntegrationService, FacebookOAuthService,
          │                 OrganizationService, AnalyticsService
          │                 (+ EncryptionService, OrganizationContextService)
     ┌────┴─────┐
     ▼          ▼
 repository   provider              SocialIntegrationRepository …
 (JPA/PG)     (SocialMediaProvider) FacebookProvider → FacebookGraphClient → Meta Graph API
     │
     ▼
 PostgreSQL (schema owned by Flyway)
```

Cross-cutting: `common` (`BaseEntity`/`TenantBaseEntity`, `ApiResponse`,
`ErrorResponse`, `GlobalExceptionHandler`), `tenant` (`TenantContext`,
`OrganizationContextService`), `config` (`SecurityConfig`, `JpaAuditingConfig`,
`OpenApiConfig`, `AppProperties`).

---

## 2. Request lifecycle (every request)

```
Client
  │  HTTP request (+ X-Organization-Id header)
  ▼
[ CORS ]                      SecurityConfig.corsConfigurationSource (allows :4200)
  ▼
[ TenantContextFilter ]       reads X-Organization-Id → TenantContext (ThreadLocal)
  ▼
[ SecurityFilterChain ]       stateless; currently permitAll()  (TODO[SSO])
  ▼
[ DispatcherServlet ] ──► @RestController method
  ▼
  Controller                  validates @RequestBody (@Valid), no business logic
  ▼
  Service (@Transactional)    business logic; resolves org via OrganizationContextService
  ▼
  Repository / Provider       JPA query  OR  SocialMediaProvider → external API
  ▼
  Entity ──► DTO (MapStruct/manual)
  ▼
  ApiResponse<T> ──────────────────────────────► JSON  { success, data, timestamp }

  …any exception ─► GlobalExceptionHandler ─► ErrorResponse { status, error, message, path }
  …finally        ─► TenantContextFilter clears TenantContext
```

Auditing: on save, `JpaAuditingConfig`'s `AuditorAware` + `BaseEntity` populate
`created_at/updated_at/created_by` automatically.

---

## 3. Multi-tenancy resolution

```
X-Organization-Id: 1
        │
        ▼
TenantContextFilter ──► TenantContext.setOrganizationId(1)   (per request, ThreadLocal)
        │
        ▼
Service ──► OrganizationContextService.currentOrganizationId()
                │ present?  → use it
                │ absent?   → app.tenant.default-organization-id (dev seed org #1)
                ▼
Repository.findByOrganizationId(orgId) / findByIdAndOrganizationId(id, orgId)
```

Every tenant-scoped entity extends `TenantBaseEntity` (`organization_id`). Reads
and writes are always scoped to the resolved org → data isolation.
**TODO[SSO]:** derive the org from the authenticated principal instead of a header.

---

## 4. Security (current vs. future)

- **Now:** `SecurityConfig` is stateless and `permitAll()` so development isn't
  blocked. CORS allows the Angular dev origin. CSRF disabled (stateless API).
- **TODO[SSO]:** add an OAuth2/OIDC resource server, replace `permitAll()` with
  real rules, map JWT claims → authorities + tenant, and resolve
  `AuditorAware`/`TenantContext` from the principal.

> Note: this app-level "Facebook Login" is **integration** auth (connecting a
> Page), not **user** auth (logging into SocialHub). They are independent.

---

## 5. Integration flows

### 5.1 Manual connect  `POST /api/v1/integrations`

```
Controller.connect(ConnectIntegrationRequest{platform, credentials})
   └─ IntegrationService.connect
        ├─ registry.get(platform) → provider.isEnabled()? else 400
        ├─ provider.validateCredentials(credentials)   ── Facebook ──► GET /{pageId}?fields=name,access_token
        │     └─ derive Page token (echoed field, else /me/accounts, else as-is)
        └─ persistConnection(platform, account, "MANUAL", expiresAt=null)
              └─ encrypt token → save SocialIntegration (status=CONNECTED) → IntegrationResponse (token masked)
```

### 5.2 OAuth connect (preferred)

```
(1) EXCHANGE   POST /api/v1/integrations/facebook/oauth/exchange { shortLivedToken }
   FacebookOAuthController → FacebookOAuthService.exchange
        ├─ requireOauthConfigured()  (needs FACEBOOK_APP_ID + FACEBOOK_APP_SECRET)
        ├─ FacebookGraphClient.exchangeForLongLivedUserToken   ─► GET /oauth/access_token (fb_exchange_token)
        ├─ FacebookGraphClient.getManagedPages(longLived)      ─► GET /me/accounts (pages + Page tokens)
        ├─ FacebookExchangeStore.put(pages+tokens) → exchangeId (server-side, TTL, never returned)
        └─ return { exchangeId, pages:[{id,name}], userTokenExpiresAt }   ◄─ NO tokens to client

(2) CONNECT    POST /api/v1/integrations/facebook/connect { exchangeId, pageId }
   FacebookOAuthService.connect
        ├─ FacebookExchangeStore.resolve(exchangeId, pageId) → Page token (or 400 if expired)
        ├─ FacebookGraphClient.getPage(...) — validate
        └─ IntegrationService.persistConnection(FACEBOOK, account, "PAGE_OAUTH", expiresAt=null)
```

### 5.3 List / create posts (+ reauth detection)

```
GET  /api/v1/integrations/{id}/posts            POST /api/v1/integrations/{id}/posts
   IntegrationService.getPosts/createPost
        ├─ getOwnedIntegration(id)               (org-scoped; 404 if not owned)
        ├─ decrypt access_token
        ├─ provider.getPosts/createPost          ── Facebook ──► /{pageId}/published_posts | /{pageId}/feed
        │        │
        │        └─ auth error (401 / OAuthException 190 / expired)
        │               ─► FacebookGraphClient throws ProviderAuthException
        └─ catch ProviderAuthException
               ─► IntegrationStatusUpdater.markReauthRequired(id)   (REQUIRES_NEW → commits despite rethrow)
               ─► rethrow (HTTP 401 to client)
```

### 5.4 Reconnect  `POST /api/v1/integrations/{id}/reauth`

```
Frontend re-runs OAuth popup → fresh exchangeId, then:
   FacebookOAuthService.reauth(id, exchangeId)
        ├─ IntegrationService.getOwnedIntegration(id)  (must be FACEBOOK)
        ├─ FacebookExchangeStore.resolve(exchangeId, integration.externalAccountId)
        ├─ validate page token
        └─ IntegrationService.reauth(id, newToken, "PAGE_OAUTH", null)
              └─ encrypt + UPDATE same row, status=CONNECTED   (no duplicate)
```

### 5.5 Disconnect  `DELETE /api/v1/integrations/{id}`
`IntegrationService.disconnect` → `getOwnedIntegration(id)` → `repository.delete`.

---

## 6. Token lifecycle & secret handling

```
short-lived USER token        (browser, FB JS SDK — never persisted)
      │  GET /oauth/access_token?grant_type=fb_exchange_token  (app secret, server-side)
      ▼
long-lived USER token         (used once for /me/accounts — never persisted)
      │  GET /me/accounts
      ▼
PAGE access token (non-expiring)
      │  EncryptionService.encrypt (AES-GCM, key from app.crypto.secret)
      ▼
social_integrations.access_token  (ciphertext at rest)
      │  decrypt only to call Graph; masked (first/last 4) in logs;
      ▼  response DTO exposes only accessTokenMasked + token_type/obtained_at/expires_at
```

- App secret and all token exchange happen **server-side only**.
- Page tokens awaiting selection live in `FacebookExchangeStore` (in-memory, TTL,
  never returned/logged).

---

## 7. Error handling

```
ResourceNotFoundException      → 404
BusinessException(status)      → that status (e.g. 400/409/502)
ProviderAuthException          → 401  (+ integration flagged REAUTH_REQUIRED)
MethodArgumentNotValidException→ 400  (+ field errors)
Exception (uncaught)           → 500  (logged with stack trace; generic message)
```

All produced by `GlobalExceptionHandler` as a consistent `ErrorResponse`
`{ timestamp, status, error, message, path, fieldErrors? }`. Successes use
`ApiResponse<T>`.

---

## 8. Persistence & migrations

- Schema is owned by **Flyway** (`src/main/resources/db/migration`): `V1` base
  schema, `V2` `social_integrations` (+ seed dev org), `V3` token metadata.
- Hibernate runs with `ddl-auto: validate` (never auto-creates/updates). Add a
  `V<n>__*.sql` for every schema change; keep entities and SQL in lockstep.
- Tests run against in-memory **H2** (`test` profile, Flyway disabled) so
  `./mvnw clean package` needs no database.

---

## 9. Where to extend

- **New platform:** add to `SocialPlatform`, implement `SocialMediaProvider` in
  `integration/<platform>` (auto-discovered by `SocialMediaProviderRegistry`).
- **New platform OAuth:** add a `<Platform>OAuthService` + controller inside that
  module; persist via core `IntegrationService` (keeps core platform-agnostic).
- **New endpoint:** DTOs → service → `@RestController` returning `ApiResponse<T>`.

See `AGENTS.md` (root) for the full rules and the step-by-step recipes.
