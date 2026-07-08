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
   - `GET http://localhost:8081/api/v1/integrations/providers` → FACEBOOK / INSTAGRAM / WHATSAPP
   - `GET http://localhost:8081/api/v1/organizations` → `[]`
   - Swagger UI: `http://localhost:8081/swagger-ui.html`
   - Health: `http://localhost:8081/actuator/health`

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

## Add Post Media Flow

The single-post Add Post flow supports ordered media-library-backed drafts:

- `POST /api/v1/posts` accepts an optional `mediaAssetId` in addition to the
  existing `mediaUrl`, and also accepts `mediaAssetIds` for multiple attached
  image/video assets. The first item is mirrored to the legacy
  `posts.media_asset_id`, `media_url`, and `media_type` columns for existing
  schedule/publish compatibility; all attachments are stored in
  `post_media_assets` in display order.
- The frontend can create multiple account-specific drafts from one form submit
  by calling this single-post endpoint once per selected platform/account
  combination. Each saved row keeps its own `platform` and `socialIntegrationId`,
  so post list, scheduling, and publishing continue to operate on one concrete
  account target at a time.
- When media asset ids are supplied, the backend verifies ownership of every
  referenced `media_assets` row, stores the ordered attachments, and mirrors the
  primary item's resolved Drive URL and media type onto the post for publish-time
  compatibility.
- `GET /api/v1/posts` and `GET /api/v1/posts/{id}` now return the linked media
  metadata needed by the UI: `mediaAssetId`, `googleDriveFileId`,
  `googleDriveUrl`, `directDownloadUrl`, `thumbnailUrl`, `mediaUploadStatus`,
  and `mediaItems[]` for all ordered attachments.
- Flyway migration `V14__posts_media_assets.sql` adds the legacy primary
  post-to-media-library link; `V19__post_multiple_media_assets.sql` adds ordered
  multi-media attachments.

## Bulk Upload Media Flow

Bulk post import now supports both `.xlsx` and `.csv` templates plus
Drive-backed media attachment:

- `GET /api/v1/posts/template?platform=FACEBOOK&format=xlsx|csv` downloads the
  bulk template.
- Template columns are: required `postContent`, `product`, `postTitle`,
  `pageId`; optional `productSku`, `link`, `imageUrl`, `videoUrl`,
  `googleDriveUrl`.
- Each media column may contain one or more URLs separated by semicolons,
  commas, or new lines. Public `imageUrl` / `videoUrl` values are downloaded and
  imported into the user's connected Google Drive account as media-library
  assets. `googleDriveUrl` values must resolve to accessible files in that same
  connected Drive account and are attached without a duplicate upload.
- Invalid rows are returned with row-level errors and a downloadable CSV error
  report; valid rows are always created as `DRAFT` with `scheduledAt = null`.

## Scheduled Native Publishing

Scheduled Facebook publishing now uses a safer worker flow and uploads actual
media bytes instead of publishing Google Drive links:

- Scheduling a draft saves `scheduledAt`, keeps the selected Facebook page on
  the post, and moves status to `PENDING`.
- Rich schedules no longer require a schedule-level connected account. Each post
  keeps the platform/account chosen during Add Post, and the backend computes
  sequential `scheduledAt` values from the schedule type and post order. Custom
  schedules use `custom_interval_hours` (1-24) so selected posts publish one by
  one every N hours instead of all at the same timestamp.
- Schedule create/update is configuration-only. Posts are attached from the Add
  Post list, and only unscheduled `DRAFT` posts can be attached. Posted posts are
  preserved as immutable history; `POST /api/v1/posts/{id}/clone` clones a
  `POSTED` post into a new `DRAFT` row before it can be scheduled again.
- Schedule post actions include detaching a waiting post without deleting it and
  setting/clearing a per-post time override while leaving the schedule default
  posting time unchanged.
- Editing a schedule's posting time or date range recalculates waiting linked
  posts from the schedule start date and posting time. A post-level
  `timeOverride` is optional and, when set, replaces only that post's time while
  keeping the schedule's date/sequence rule.
- Schedule post responses include `mediaAssetId` and `thumbnailUrl` so the UI can
  load authenticated previews for linked posts instead of relying on raw media
  URLs.
- The scheduler runs every minute (`SCHEDULED_PUBLISHER_POLL_INTERVAL_MS`,
  default `60000`) and claims due posts where `scheduledAt <= now` and status
  is `PENDING`.
- Claimed posts move to `PROCESSING` before any Facebook API call, which keeps
  restart-safe state in the database and prevents duplicate scheduler claims.
- Text-only posts publish to `/{page-id}/feed`.
- Image posts download the Drive file through the user's connected Google Drive
  account and upload it natively to `/{page-id}/photos`.
- Video posts download the Drive file through the user's connected Google Drive
  account and upload it natively to `/{page-id}/videos`.
- The caption/message/description always comes from `Post.content`. Drive URLs
  are not pushed into the Facebook caption or link fields for media posts.
- Publishing results stored on `posts` include `external_post_id`,
  `published_at`, `publish_response_summary`, `error_message`, `retry_count`,
  and `last_retry_at`.
- Temporary publish failures are re-queued automatically with backoff
  (`10m * retryCount`, max 3 retries). Failed posts can also be retried
  immediately through `POST /api/v1/posts/{id}/retry`.
# socialHubBack
