# Eum-ShoppingMall-backend
이음산업건설 쇼핑몰 백엔드

## Database migrations

MySQL schema changes are managed by Flyway. A fresh database applies the versioned scripts in `src/main/resources/db/migration` before Hibernate validates the entity mappings. Do not edit a migration after it has been applied; add the next sequential `V<number>__description.sql` file in the same change as the entity update.

The `test` profile uses H2 schema generation and disables Flyway because the migration scripts target MySQL. CI starts an empty MySQL 8.4 database and starts the application with `ddl-auto: validate`, so a migration that fails or does not match the entities blocks the required `Validate database migrations` check.

The `local` profile also uses Flyway and Hibernate validation. It baselines a pre-existing local schema at version 1 to preserve databases previously created with Hibernate `update`; this baseline is local-only. Production does not enable automatic baselining. Before connecting Flyway to any existing production database, inspect its schema and baseline it deliberately. The initial migration does not create default categories or administrator accounts; application bootstrap remains responsible for those records.

## Admin order cancellation API

`POST /api/v1/admin/orders/{orderId}/cancel` is available to administrators. Send a JSON body with a required `reason` of up to 200 characters. Paid and preparing orders are fully refunded through the payment gateway; pending orders expire and release reserved stock. Waiting virtual-account orders are canceled at the gateway before they expire and release stock. Shipped or delivered orders and partial refunds are not supported by this endpoint. The admin order detail response includes `cancelReason`, `cancelledByMemberId`, and `cancelledAt` after a successful cancellation.

## Static content API

Supported content codes are `company`, `terms`, and `privacy-policy`. Customers can read `GET /api/v1/contents/{code}` (for example `GET /api/v1/contents/company`). Administrators can list and read saved content with `GET /api/v1/admin/contents` and `GET /api/v1/admin/contents/{code}`, and create or update it with `PUT /api/v1/admin/contents/{code}` using `{ "title": "...", "body": "..." }`. Bodies are plain text; HTML tag markup is rejected. No legal text is seeded by the server.

## Image storage

Uploads use local disk by default (`UPLOAD_STORAGE=local`). To use S3, set `UPLOAD_STORAGE=s3`, `AWS_REGION`, `UPLOAD_S3_BUCKET`, `UPLOAD_CDN_BASE_URL`, `UPLOAD_S3_ACCESS_KEY`, and `UPLOAD_S3_SECRET_KEY`. The keys belong to a dedicated IAM user limited to the bucket's `products/` prefix; the app does not start without them. Pass them as server environment variables or secrets, never in committed files. Uploaded S3 objects use `products/YYYY/MM/{UUID}.{extension}`, the detected image MIME type, and immutable one-year cache headers. The CDN URL is returned to clients. Product and banner `imageUrl` values must be URLs returned by the upload API. With S3, admins can also upload directly: `POST /api/v1/uploads/presigned-url` returns a presigned PUT URL with the headers to send, and `POST /api/v1/uploads/complete` re-checks the uploaded file (image signature, 5MB) before returning its CDN URL. A daily job (`UPLOAD_CLEANUP_CRON`, default 04:30) deletes uploaded images older than 24 hours that no product or banner references. The bucket, CloudFront OAC, the upload IAM user and its policy, and versioning are defined in `infra/aws/uploads.tf`; `scripts/migrate-local-uploads-to-s3.sh` copies existing local files (see `docs/deployment-aws.md`).
## Admin member role API

`PATCH /api/v1/admin/users/{userId}/role` accepts `{"role":"USER"}` or `{"role":"ADMIN"}` and can only be called by a `SUPER_ADMIN`. A user cannot change their own role, another `SUPER_ADMIN` role, or a withdrawn account. Suspended accounts cannot be granted `ADMIN`. Changing a role deletes all that member's refresh tokens and immediately invalidates their issued access tokens, so a demoted administrator loses admin access at once. The configured bootstrap admin is created or promoted as `SUPER_ADMIN`; an existing regular member with the configured email is not promoted.

## Request tracing and metrics

Every response includes `X-Request-Id`. A safe incoming value (1–64 letters, numbers, dots, underscores, or hyphens) is echoed; otherwise the server generates a UUID. The same value is included in application log lines. Actuator `health` and `metrics` are enabled; only health probes are public, while the remaining actuator routes require an administrator token. A metrics backend and alert delivery destination still need to be selected before operational alerts can be enabled. Scheduled jobs run through `ScheduledJobRunner`, which takes a database lease so only one instance runs each job and reports `scheduled.job.failures{job}` (job exceptions and per-item failures) and `scheduled.job.lock.lost{job}` (the lease expired before the job finished).
## Member withdrawal and suspension

`DELETE /api/v1/me` requires `{"password":"..."}`. Withdrawal is rejected while the member has a pending, processing, waiting-for-deposit, paid, preparing, shipped, or cancel-requested (refund not yet confirmed) order (`OrderStatus.IN_PROGRESS`). On success, the account is marked `WITHDRAWN`, its login credentials and profile identifiers are anonymized, and refresh tokens and cart items are removed. Historical orders remain. Administrators can suspend or reactivate another account with `PATCH /api/v1/admin/users/{userId}/status`; suspending requires a reason and revokes refresh tokens.

## API access policy

Which APIs are public, admin-only, rate limited per IP, and recorded in the admin audit log is defined in one table, `global/security/ApiPolicy`; the security configuration, the rate-limit filter, and the audit interceptor all read it. `ApiPolicyTests` fails when controllers and the table disagree: an `Admin*` controller outside the admin table or not audited, an `/api/v1/admin/**` route served by a non-admin controller, a stale path or rate-limit rule, or a login-free write without a rate limit. The per-IP limit and the login lock share one Redis counter implementation (`SharedCounter`) that falls back to per-instance counting during a Redis outage.

## Login attempt limit

Besides the per-IP request limit (`RATE_LIMIT_PER_MINUTE`), each login email is limited: after `LOGIN_LOCK_MAX_FAILURES` (default 10) failed logins within 10 minutes of the first failure, member and admin login for that email return `429 LOGIN_LOCKED` until those 10 minutes pass, even with the correct password. Unknown emails are counted the same way, so the response does not reveal whether an account exists. A successful login or password reset clears the count. Counts are shared through Redis and fall back to per-instance counting during a Redis outage.

## Admin audit log

Administrator `POST`, `PUT`, `PATCH`, and `DELETE` requests (including image uploads) are recorded when they succeed or are rejected for lack of permission (403); other failures such as validation errors are not recorded. Each entry has the acting member, server-observed client IP, HTTP method, target type/identifier, route summary, response status, and the JSON request body with sensitive fields (`password`, `secret`, `token`, `card`) masked and truncated to 2,000 characters. The target is the innermost `name/{id}` of the matched route: `/api/v1/admin/products/{productId}/skus/{skuId}/stock-adjustments` records `skus` and the SKU id, and image uploads record `uploads`. `GET /api/v1/admin/audit-logs` supports actor, target type, and time filters and is restricted to `SUPER_ADMIN`; `fromDate`/`toDate` accept `2026-10-01` or `2026-10-01T09:00:00`, and a date-only `toDate` includes that whole day. The log API has no update or delete operations. The optional administrator 2FA and member PII-read audit remain separate follow-up work.

## Email verification limits

Email verification codes (sign-up and password reset) can be sent at most 5 times per email and purpose in 24 hours, and at most 10 wrong codes are accepted in total in that period (`429 VERIFICATION_DAILY_LIMIT_EXCEEDED`). Password-reset requests over the limit still get the same success response, but no mail is sent.

## Shipping policy

Orders have no discount; the shipping fee is the only amount added to the item total. Administrators set it with `GET`/`PUT /api/v1/admin/shipping-policy` (`{"shippingFee":3000,"freeShippingThreshold":50000}`): items totaling at least `freeShippingThreshold` ship free, omitting `freeShippingThreshold` always charges the fee, and `shippingFee` 0 makes shipping free. Until an administrator saves a policy, the fee is 5,000 KRW below 100,000 KRW. Cart estimates and new orders use the current policy; each order stores the fee it was created with, so policy changes do not alter existing orders. `GET /api/v1/shipping-policy` returns the policy without login for storefront notices. The cart response no longer has `discountAmount`; order responses keep it for orders created before the change, and new orders record 0.

## Product read cache

Public product reads (home, categories, list/search, facets, suggestions) are cached in Redis for `PRODUCT_READ_CACHE_TTL_SECONDS` (default 60). Only administrator catalog changes clear the whole cache; stock and sales-count changes from orders show up within the TTL, while product detail, cart, and order checks always read the database. `product.read.cache{result=hit|miss|unavailable}` counts lookups.
