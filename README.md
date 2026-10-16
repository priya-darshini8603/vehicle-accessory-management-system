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
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | | – | Bootstrap admin, created at startup if the email doesn't exist yet (password ≥ 12 chars) |
| `DDL_AUTO` | | `update` | Hibernate schema mode |
| `PORT` | | `8080` | |

## Roles

- **`/api/auth/register` always creates a `CUSTOMER`.** No request can grant itself admin rights.
- The first **`ADMIN`** is the bootstrap account from `ADMIN_EMAIL` / `ADMIN_PASSWORD`. Admins can grant or revoke
  ADMIN for other users with `PUT /api/admin/users/{id}/role`. Admins can't remove their own ADMIN role.
- The user's role is re-read from the database on every request, so a role change applies immediately.

## API

Every endpoint except register and login needs `Authorization: Bearer <accessToken>`.

### Auth (public)
| Method | Path | Body | Response |
|---|---|---|---|
| POST | `/api/auth/register` | `{fullName, email, password (12–72), phoneNumber?}` | `201` user |
| POST | `/api/auth/login` | `{email, password}` | `200 {accessToken, tokenType, expiresIn, user}` |
| POST | `/api/auth/logout` | – (needs `Authorization: Bearer`) | `200`. The token is revoked; using it again returns `401`. |

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
| POST | `/api/admin/accessories` | `{name, category, price, description?, stockQuantity?}` → `201` |
| GET | `/api/admin/accessories?page=0&size=20&sort=name,asc` | Paged |
| GET | `/api/admin/accessories/{id}` | |
| PUT | `/api/admin/accessories/{id}` | |
| DELETE | `/api/admin/accessories/{id}` | `204`. Removes the accessory from every vehicle first. |

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
| 409 | Duplicate vehicle number or email |
| 422 | Business rule violation (e.g. an admin demoting themselves) |
| 500 | Unexpected error (details are logged, never returned) |

<!-- 


## Postman

Import `backend/postman/vehicle-accessory-management-system.postman_collection.json`. Run it top to bottom, or with
the Collection Runner: it logs in, saves tokens and ids, runs every endpoint including the error cases, and cleans up.

*/
-->

## Project layout

```
backend/src/main/java/org/example
├── config/       SecurityConfig, AdminBootstrap
├── controller/   Auth, Vehicle, Accessory, Compatibility, UserAdmin
├── dto/          Request/response records (validated), PageResponse, ErrorResponse
├── entity/       User, Vehicle, Accessory (many-to-many via vehicle_accessories)
├── enums/        Role
├── exception/    Domain exceptions + GlobalExceptionHandler
├── mapper/       Entity ↔ DTO mapping
├── repository/   Spring Data repositories (fetch joins / entity graphs avoid N+1)
├── security/     JWT service + filter, key config, 401/403 JSON handlers
└── service/      Interfaces + impl/
```
