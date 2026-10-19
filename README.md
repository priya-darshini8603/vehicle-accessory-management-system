# Vehicle Accessory Management System

REST backend for managing vehicles, accessories, and which accessories fit which vehicle.
Admins manage the catalog; customers look up compatible accessories by vehicle number.

**Stack:** Java 21 · Spring Boot 4.1 · Spring Security 7 (stateless JWT) · Spring Data JPA / Hibernate · PostgreSQL · Maven

---

## Quick start

```bash
cd backend
docker compose up -d          # PostgreSQL on localhost:5432 (vams_db / postgres / admin)
./mvnw spring-boot:run        # http://localhost:8080
```

With no profile set, the app runs the **`dev`** profile (`application-dev.properties`). That profile has a local DB
password, a dev JWT secret, and a bootstrap admin:

| | |
|---|---|
| Admin email | `admin@vams.local` |
| Admin password | `Admin@12345678` |

Run the tests (they use in-memory H2 and need no database):

```bash
./mvnw test
```

## Configuration

Outside your machine, set `SPRING_PROFILES_ACTIVE=prod` (or any profile other than `dev`) and provide the following:

| Variable | Required | Default | Purpose |
|---|---|---|---|
| `DB_URL` | | `jdbc:postgresql://localhost:5432/vams_db` | JDBC URL |
| `DB_USERNAME` | | `postgres` | |
| `DB_PASSWORD` | ✔ | – | |
| `JWT_SECRET` | ✔ | – | Base64 HMAC key, ≥ 32 bytes: `openssl rand -base64 48`. The app refuses to start without it. |
| `JWT_EXPIRATION_MS` | | `1800000` (30 min) | Token lifetime |
| `REFRESH_TOKEN_EXPIRATION_MS` | | `2592000000` (30 days) | Refresh-token lifetime |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | | – | Bootstrap admin, created at startup if the email doesn't exist yet (password ≥ 12 chars) |
| `DDL_AUTO` | | `update` | Hibernate schema mode |
| `PORT` | | `8080` | |

## Roles

- **`/api/auth/register` always creates a `CUSTOMER`.** No request can grant itself admin rights.
- The first **`ADMIN`** is the bootstrap account from `ADMIN_EMAIL` / `ADMIN_PASSWORD`. Admins can grant or revoke
  ADMIN for other users with `PUT /api/admin/users/{id}/role`. Admins can't remove their own ADMIN role.
- The user's role is re-read from the database on every request, so a role change applies immediately.

## API

Every endpoint except register, login, and refresh needs `Authorization: Bearer <accessToken>`.

### Auth (public)
| Method | Path | Body | Response |
|---|---|---|---|
| POST | `/api/auth/register` | `{fullName, email, password (12–72), phoneNumber?}` | `201` user |
| POST | `/api/auth/login` | `{email, password}` | `200 {accessToken, refreshToken, tokenType, expiresIn, user}` |
| POST | `/api/auth/refresh` | `{refreshToken}` | `200` with a new access token and rotated refresh token; the old refresh token cannot be reused |
| POST | `/api/auth/logout` | `{refreshToken}` (optional; needs `Authorization: Bearer`) | `200`. Revokes the supplied refresh token. If omitted, all refresh tokens for the authenticated user are revoked. Access tokens remain valid until they expire (30 minutes by default). |

Refresh tokens are opaque and stored only as SHA-256 hashes. They are checked for expiry and revocation whenever used, and rotated on every refresh request.

### Vehicles (ADMIN)
| Method | Path | Notes |
|---|---|---|
| POST | `/api/admin/vehicles` | `{vehicleNumber, make, model, year, userId?}` → `201` + `Location`. The number is trimmed and upper-cased. Duplicates return `409`. |
| GET | `/api/admin/vehicles?page=0&size=20&sort=vehicleNumber,asc` | Paged (max size 100), includes mapped accessories |
| GET | `/api/admin/vehicles/{id}` | |
| PUT | `/api/admin/vehicles/{id}` | Full replacement (an omitted `userId` clears the owner) |
| DELETE | `/api/admin/vehicles/{id}` | `204`. Deletes the vehicle's mappings too; the accessories are kept. |

### Accessories (ADMIN)
| Method | Path | Notes |
|---|---|---|
| POST | `/api/admin/accessories` | `{name, category, price, description?, stockQuantity?, minStockThreshold?, leadTimeDays?}` → `201`. Initial stock is audited. |
| GET | `/api/admin/accessories?page=0&size=20&sort=name,asc` | Paged |
| GET | `/api/admin/accessories/{id}` | |
| PUT | `/api/admin/accessories/{id}` | Updates catalog data; a changed `stockQuantity` requires `stockAdjustmentReason`. Prefer inventory movements for receipts, returns and stock corrections. |
| DELETE | `/api/admin/accessories/{id}` | `204`. Removes the accessory from every vehicle first; products with stock history cannot be deleted. |

### Inventory (ADMIN)
| Method | Path | Notes |
|---|---|---|
| POST | `/api/admin/inventory/accessories/{id}/movements` | `{type, quantityDelta, note}` records a movement and updates stock atomically. SALE deltas are negative; PURCHASE and RETURN are positive; ADJUSTMENT accepts either sign. A reason is required for receipts, returns and adjustments. Record a supplier receipt only after delivery verification and a return only after resaleability verification. |
| GET | `/api/admin/inventory/accessories/{id}/movements?page=0&size=20` | Paged stock ledger, newest first, including stock before/after and actor. |
| GET | `/api/admin/inventory/report?days=30` | Stock dashboard, lead-time demand, reorder points, stock cover, low/out-of-stock lists, replenishment suggestions, fast/slow movers, and the latest movements for a 1–365 day sales window. |

`minStockThreshold` and `leadTimeDays` default to 0 and 7. Average daily sales is units sold divided by the requested analysis days; lead-time demand is average daily sales multiplied by lead-time days; reorder point is lead-time demand plus `minStockThreshold` (safety stock). Suggested replenishment is `max(0, ceil(reorderPoint - currentStock))`. A zero-sales product has no calculable stock-cover duration (`stockCoverDays: null`). Fast movers are the top quartile by units sold (excluding products without sales); slow movers are the bottom quartile, including products with no sales. The report uses movement aggregates and is read-only. A daily report and low/out-of-stock warning logs run at midnight UTC by default; configure `INVENTORY_ANALYSIS_CRON` and `INVENTORY_ANALYSIS_TIME_ZONE` to change this.

### Orders (CUSTOMER)

An authenticated customer can purchase accessories directly; a successful request is immediately `CONFIRMED`. There is no payment workflow. Send an `Idempotency-Key` (1–128 characters) with every create request. Repeating the same key and payload returns the existing order; using that key for a different payload returns `409`. Duplicate accessory IDs merge into one item. Insufficient stock rejects the whole order with `422`.

| Method | Path | Notes |
|---|---|---|
| POST | `/api/orders` | Creates and confirms the order, snapshots price/discount/tax, deducts stock and records SALE movements in one transaction. |
| GET | `/api/orders/{orderId}` | Retrieves an order only for its customer. |
| GET | `/api/orders/my?page=0&size=20` | Paged order history for the authenticated customer. |
| POST | `/api/orders/{orderId}/cancel` | Cancels an eligible confirmed order and restores stock once. |

Example request:

```http
POST /api/orders
Authorization: Bearer <customer-token>
Idempotency-Key: checkout-2026-10-19-001
Content-Type: application/json

{"items":[{"accessoryId":12,"quantity":2},{"accessoryId":18,"quantity":1}]}
```

Example response (`201 Created`):

```json
{
  "id": 41,
  "orderNumber": "ORD-<generated-uuid>",
  "status": "CONFIRMED",
  "inventoryProcessingStatus": "DEDUCTED",
  "subtotal": 250.00,
  "discountAmount": 0.00,
  "taxAmount": 45.00,
  "finalTotal": 295.00,
  "createdAt": "2026-10-19T16:24:00Z",
  "updatedAt": "2026-10-19T16:24:00Z",
  "items": [
    {
      "accessoryId": 12,
      "accessoryName": "Floor Mats",
      "quantity": 2,
      "unitPrice": 100.00,
      "unitDiscount": 0.00,
      "discountName": null,
      "taxRate": 18.00,
      "subtotal": 200.00,
      "discountAmount": 0.00,
      "taxAmount": 36.00,
      "lineTotal": 236.00
    },
    {
      "accessoryId": 18,
      "accessoryName": "Roof Rack",
      "quantity": 1,
      "unitPrice": 50.00,
      "unitDiscount": 0.00,
      "discountName": null,
      "taxRate": 18.00,
      "subtotal": 50.00,
      "discountAmount": 0.00,
      "taxAmount": 9.00,
      "lineTotal": 59.00
    }
  ]
}
```

Cancellation changes the status to `CANCELLED` and inventory processing state to `RESTORED`; a second cancellation is rejected with `409`. The order and its item snapshots remain available in customer order history.

### Vehicle ↔ accessory mappings (ADMIN)
| Method | Path | Notes |
|---|---|---|
| POST | `/api/admin/vehicles/{vehicleId}/accessories/{accessoryId}` | Map one (idempotent) → vehicle |
| DELETE | `/api/admin/vehicles/{vehicleId}/accessories/{accessoryId}` | Unmap one. Returns `404` if it wasn't mapped. |
| PUT | `/api/admin/vehicles/{vehicleId}/accessories` | `{accessoryIds: [..]}` sets exactly this set (`[]` clears it). Nothing changes if any id is unknown. |

### Users (ADMIN)
| Method | Path | Notes |
|---|---|---|
| GET | `/api/admin/users` | Paged |
| PUT | `/api/admin/users/{id}/role` | `{role: "ADMIN" \| "CUSTOMER"}` |

### Compatibility search (ADMIN, CUSTOMER)
| Method | Path |
|---|---|
| GET | `/api/compatibility?vehicleNumber=KA01AB1234` |
| GET | `/api/compatibility/vehicles/{vehicleNumber}/accessories` |

Matching ignores case and surrounding whitespace. The response looks like this:

```json
{
  "vehicle": {"id": 1, "vehicleNumber": "KA01AB1234", "make": "Maruti Suzuki", "model": "Swift", "year": 2022},
  "totalAccessories": 2,
  "compatibleAccessories": [{"id": 2, "name": "Roof Rack", "category": "Exterior", "price": 8999.50, "...": "..."}]
}
```

### My garage (ADMIN, CUSTOMER): a customer's own vehicles
The owner is always the signed-in user. Another user's vehicle id returns `404`, the same as a missing one.

| Method | Path | Notes |
|---|---|---|
| GET | `/api/me/vehicles` | Primary vehicle first, then in the order added. Each entry has `compatibleAccessoryCount`. |
| POST | `/api/me/vehicles` | `{vehicleNumber, make, model, year, nickname?}` → `201`. The first vehicle becomes primary. Max 10 per customer (`MAX_VEHICLES_PER_CUSTOMER`). |
| GET | `/api/me/vehicles/{id}` | |
| PATCH | `/api/me/vehicles/{id}` | `{nickname}`. Nicknames are unique within a garage. |
| PUT | `/api/me/vehicles/{id}/primary` | Makes this the default vehicle |
| DELETE | `/api/me/vehicles/{id}` | `204`. Unlinks the vehicle (e.g. sold); it stays in the catalog. If it was primary, the oldest remaining vehicle becomes primary. |
| GET | `/api/me/vehicles/{id}/accessories` | Compatible accessories for one of your vehicles |

Adding a number that already exists: `409` if it's yours or someone else's; if it's unowned (a catalog entry or
released by its previous owner) you can add it, but only when make, model and year match (`422` otherwise).

### Discounts (ADMIN)
| Method | Path | Notes |
|---|---|---|
| POST | `/api/admin/discounts` | See body below → `201` |
| GET | `/api/admin/discounts` | Paged. `live` = active and inside its validity window now. |
| GET / PUT / DELETE | `/api/admin/discounts/{id}` | Send `"active": false` to pause one without deleting it |

```json
{"name": "Festive sale", "code": "FESTIVE25", "type": "PERCENTAGE", "value": 25,
 "scope": "CATEGORY", "category": "Interior", "maxDiscountAmount": 500, "minQuantity": 1,
 "validFrom": "2026-10-20T00:00:00Z", "validTo": "2026-11-05T00:00:00Z", "active": true}
```

- No `code` = automatic promotion. With a `code` = coupon, applied only when the customer enters it.
- `type`: `PERCENTAGE` (≤ 100, optional `maxDiscountAmount` cap per unit) or `FLAT` (amount off per unit).
- `scope`: `ALL` (no target), `CATEGORY` (needs `category`), `ACCESSORY` (needs `accessoryId`).
- `minQuantity` gives bulk pricing ("15% off when you buy 4").

Accessories also take optional `costPrice` (the price can't be lower) and `taxRate` (GST %, default 18 via
`DEFAULT_TAX_RATE`). `costPrice` only appears in admin responses. `GET /api/admin/accessories/{id}/price-history`
shows every price change and who made it.

### Price quote (ADMIN, CUSTOMER)
`POST /api/pricing/quote` with `{items: [{accessoryId, quantity}], couponCode?, vehicleId?}` returns each line's
unit price, discount, tax and total, plus the cart totals. Nothing is saved.

- Discounts don't stack. Each line gets the one discount that saves the most (an automatic one or the coupon).
- A discount never takes the price below `costPrice`. GST is charged on the discounted price.
- Coupon errors are `422` with a specific message: not valid, no longer active, not active yet, expired, or doesn't
  apply to any item. A valid coupon that loses to a better automatic discount is reported in `warnings`.
- Lines flag `Out of stock` / `Only N in stock`, and, when `vehicleId` is given, accessories not listed as compatible.

### Errors

Every error uses the same response body. `fieldErrors` appears only on validation failures:

```json
{
  "timestamp": "2026-10-14T14:45:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Vehicle with number 'KA01AB1234' not found",
  "path": "/api/compatibility/vehicles/KA01AB1234/accessories"
}
```

| Status | When |
|---|---|
| 400 | Validation failure, malformed JSON, bad path/query value, unknown sort property |
| 401 | Missing, invalid or expired token; wrong login credentials |
| 403 | Authenticated, but the role isn't allowed |
| 404 | Unknown vehicle, accessory, user or mapping |
| 409 | Duplicate vehicle number, email or discount code; the record was changed by a concurrent request |
| 422 | Business rule violation (e.g. an admin demoting themselves) |
| 500 | Unexpected error (details are logged, never returned) |

## Postman

Import `backend/postman/vehicle-accessory-management-system.postman_collection.json`. Run it top to bottom, or with
the Collection Runner: it logs in, saves tokens and ids, runs every endpoint including the error cases, and cleans up.

## Project layout

```
backend/src/main/java/org/example
├── config/       SecurityConfig, AdminBootstrap
├── controller/   Auth, Vehicle, Accessory, Compatibility, UserAdmin
├── dto/          Request/response records (validated), PageResponse, ErrorResponse
├── entity/       User, Vehicle, Accessory, Order, OrderItem, StockMovement, Discount, PriceChange
├── enums/        Role, OrderStatus, InventoryProcessingStatus, StockMovementType
├── exception/    Domain exceptions + GlobalExceptionHandler
├── mapper/       Entity ↔ DTO mapping
├── pricing/      PriceCalculator: pure pricing rules (no database access)
├── repository/   Spring Data repositories (fetch joins / entity graphs avoid N+1)
├── security/     JWT service + filter, key config, 401/403 JSON handlers
└── service/      Interfaces + impl/ (including order processing)
```

More detail: [docs/FEATURE_DESIGN.md](docs/FEATURE_DESIGN.md) (why each feature exists, rules and edge cases) and
[docs/CODING_PRACTICES.md](docs/CODING_PRACTICES.md) (how we write code in this repo).
