# TODOS

## Follow-ups (P3)

### Model search cannot use an index

**What:** `VehicleRepository` matches models with `LOWER(v.model) LIKE LOWER(:pattern)`, which no index can serve.

**Why:** Fine at demo scale, but it scans every vehicle; CODING_PRACTICES.md asks for comparisons an index can use.

**Context:** Owner: Darshini. Options: store a normalised `model_key` column with an index, or a PostgreSQL expression index on `lower(model)` in a V3 migration (prefix matches only).

**Effort:** S
**Priority:** P3
**Depends on:** None

### Rate limiting on login and refresh

**What:** Per-IP and per-account throttling on `/api/auth/login` and `/api/auth/refresh`, with a short lockout after repeated failures.

**Why:** Security audit finding (low): nothing stops credential stuffing, and each attempt costs a BCrypt check.

**Context:** A servlet filter with Bucket4j, or a gateway in front of the app once one exists.

**Effort:** M
**Priority:** P3
**Depends on:** None

### Coupon usage limits

**What:** Total and per-customer redemption limits for coupons (e.g. single-use codes).

**Why:** Today a coupon can be used any number of times by anyone.

**Context:** Orders store each line's discount name and amount (`OrderItem.discountName`) but not the coupon code. Add a `coupon_code` column to `customer_orders` in a V3 migration, then count redemptions inside the order transaction (the accessory rows are already locked there).

**Effort:** M
**Priority:** P3
**Depends on:** None

## Completed

### Auth hardening from the 2026-10-19 eng review (done by Darshini)

- Revoke the whole token family when a rotated refresh token is replayed: `RefreshTokenServiceImpl.handleRevokedTokenReuse`, with a `REFRESH_TOKEN_REUSE_GRACE_MS` retry window.
- Cap refresh sessions at an absolute lifetime: rotation keeps the original expiry.
- Allow logout with only the refresh token: `/api/auth/logout` is public and takes `{refreshToken}`.
- Purge expired and revoked refresh tokens: `RefreshTokenCleanupJob`.

**Completed:** 2026-10-20 (origin/main 3a22747)

### Eng review tasks T1-T3, Flyway and the PostgreSQL rehearsal (2026-10-20)

- T1: discount `version` returned and required on PUT; stale version returns `409` (`AdminRulesIntegrationTest`).
- T2: discount categories stored lower-case; the quote query no longer wraps the column in `LOWER()`; existing rows fixed by `V2__lowercase_discount_categories.sql`.
- T3: regression tests for coupon messages, admin quotes and the cost-floor warning, discount edit/delete, primary-vehicle handover and the version `409`; two checked by breaking the code on purpose.
- Flyway: `V1__baseline.sql` from PostgreSQL, `ddl-auto=validate`, baseline-on-migrate for pre-Flyway databases.
- Rehearsal: on PostgreSQL 16, the demo folder (20 requests) and folders 01-08 (52 requests) all pass.

**Completed:** 2026-10-20
