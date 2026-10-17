# Eum-ShoppingMall-backend
이음산업건설 쇼핑몰 백엔드

## Database migrations

MySQL schema changes are managed by Flyway. A fresh database applies the versioned scripts in `src/main/resources/db/migration` before Hibernate validates the entity mappings. Do not edit a migration after it has been applied; add the next sequential `V<number>__description.sql` file in the same change as the entity update.

The `test` profile uses H2 schema generation and disables Flyway because the migration scripts target MySQL. CI starts an empty MySQL 8.4 database and starts the application with `ddl-auto: validate`, so a migration that fails or does not match the entities blocks the required `Validate database migrations` check.

The `local` profile also uses Flyway and Hibernate validation. It baselines a pre-existing local schema at version 1 to preserve databases previously created with Hibernate `update`; this baseline is local-only. Production does not enable automatic baselining. Before connecting Flyway to any existing production database, inspect its schema and baseline it deliberately. The initial migration does not create default categories or administrator accounts; application bootstrap remains responsible for those records.

## Admin order cancellation API

`POST /api/v1/admin/orders/{orderId}/cancel` is available to administrators. Send a JSON body with a required `reason` of up to 200 characters. Paid and preparing orders are fully refunded through the payment gateway; pending orders expire and release reserved stock. Waiting virtual-account orders are canceled at the gateway before they expire and release stock. Shipped or delivered orders and partial refunds are not supported by this endpoint. The admin order detail response includes `cancelReason`, `cancelledByMemberId`, and `cancelledAt` after a successful cancellation.

## Static content API

Supported content codes are `company`, `terms`, and `privacy-policy`. Customers can read `GET /api/v1/contents/{code}`; `GET /api/v1/contents/company` remains an alias. Administrators can list and read saved content with `GET /api/v1/admin/contents` and `GET /api/v1/admin/contents/{code}`, and create or update it with `PUT /api/v1/admin/contents/{code}` using `{ "title": "...", "body": "..." }`. Bodies are plain text; HTML tag markup is rejected. No legal text is seeded by the server.
## Admin member role API

`PATCH /api/v1/admin/users/{userId}/role` accepts `{"role":"USER"}` or `{"role":"ADMIN"}` and can only be called by a `SUPER_ADMIN`. A user cannot change their own role, another `SUPER_ADMIN` role, or a withdrawn account. Suspended accounts cannot be granted `ADMIN`. Changing a role deletes all that member's refresh tokens. Already issued access tokens remain usable until they expire; immediate access-token invalidation is outside this endpoint's scope. The configured bootstrap admin is created or promoted as `SUPER_ADMIN`; an existing regular member with the configured email is not promoted.

## Request tracing and metrics

Every response includes `X-Request-Id`. A safe incoming value (1–64 letters, numbers, dots, underscores, or hyphens) is echoed; otherwise the server generates a UUID. The same value is included in application log lines. Actuator `health` and `metrics` are enabled; only health probes are public, while the remaining actuator routes require an administrator token. A metrics backend and alert delivery destination still need to be selected before operational alerts can be enabled.
## Member withdrawal and suspension

`DELETE /api/v1/me` requires `{"password":"..."}`. Withdrawal is rejected while the member has a pending, processing, waiting-for-deposit, paid, preparing, or shipped order. On success, the account is marked `WITHDRAWN`, its login credentials and profile identifiers are anonymized, and refresh tokens, cart items, and the saved checkout address are removed. Historical orders remain. Administrators can suspend or reactivate another account with `PATCH /api/v1/admin/users/{userId}/status`; suspending requires a reason and revokes refresh tokens.
