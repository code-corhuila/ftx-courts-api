# ftx-courts-api

> Courts domain API: the four fixed football courts, their price and status

Part of **Futbolix**, the distributed system for reserving the four football courts of a
single sports facility. Governance and documentation live in
[`ftx-docs`](https://github.com/code-corhuila/ftx-docs). User story: HU-ADM-001.
Contract: `ftx-docs/07-api/contracts/openapi/court-service.yaml`.

## Endpoints

| Method and path | Who | Responses |
|---|---|---|
| `GET /api/v1/courts` | Public | 200 `{ "data": [Court] }` |
| `GET /api/v1/courts/{id}` | Public | 200 `Court`, 400 bad id, 404 |
| `PATCH /api/v1/admin/courts/{id}` | `ADMINISTRATOR` | 200 `Court`, 400 invalid or unknown field, 401 no identity, 403 other role, 404, 409 name in use, 422 `INVALID_COURT_STATUS_TRANSITION` |
| `GET /health`, `/health/liveness`, `/health/readiness` | Probes | 200 `UP` |

Courts are never created or deleted (BR-001): there are no POST or DELETE endpoints, and the
database user cannot do it either. Allowed status changes: `ACTIVE` ↔ `INACTIVE`,
`ACTIVE` ↔ `UNDER_MAINTENANCE`. Errors use `ErrorResponse` of `_shared.yaml`, with the request's
`X-Correlation-Id` as `traceId`. The weekly schedules (`/courts/{id}/schedules`, bookable slots)
belong to `ftx-schedules-api`.

### Who can change a court

`/api/v1/admin/**` needs the administrator access token in `Authorization: Bearer <token>`, and
this service **checks it itself**: RS256, signed by a key of the JWKS that `ftx-customers-api`
publishes (`GET /api/v1/auth/jwks`), issuer `ftx-customers-api`, not expired, with `sub`, `iat`,
`exp`, `jti` and `roles`. No valid token: 401. A role other than `ADMINISTRATOR`: 403. The keys
cannot be fetched: 503, and nothing is changed. Public reads never need the keys.

`X-User-Id` and `X-User-Role` are **ignored**. The port of this API is published on the host, so
a client could send those headers, and `ftx-docs/07-api/authentication.md` says a service must
never trust client-supplied identity headers. The gateway forwards the `Authorization` header
unchanged. Every change is logged on the `audit` logger with the administrator id (the `sub` of the
token) and the values before and after (FR-036).

## Architecture (hexagonal)

```
domain/                  Court, CourtStatus, Money: rules only, no framework, no I/O
application/             CourtService (use cases) and its ports
  port/in/               ListCourts, GetCourt, UpdateCourt
  port/out/              CourtRepository, CourtAuditTrail
adapter/in/web/          controllers, ErrorResponse, administrator guard, correlation id
adapter/out/persistence/ JdbcCourtRepository (Spring JDBC on courts.court)
adapter/out/audit/       structured audit log
adapter/config/          wiring; an update runs in one transaction
```

## Configuration

| Variable | Default | Meaning |
|---|---|---|
| `COURTS_DB_URL` | `jdbc:postgresql://postgres:5432/ftx` | The single instance of `ftx-infra-postgres` |
| `COURTS_DB_USER` | `courts_app` | Login user created by `ftx-infra-postgres` |
| `COURTS_DB_PASSWORD` | none (required) | Its password, from `env/<environment>.env` of `ftx-infra-postgres` |
| `COURTS_AUTH_JWKS_URL` | `http://ftx-customers-api:8081/api/v1/auth/jwks` | Public keys of the administrator tokens (cached by the service) |
| `COURTS_AUTH_ISSUER` | `ftx-customers-api` | Required `iss` of the tokens |
| `PORT` | `8082` | HTTP port |

The service never runs migrations: the `courts` schema belongs to `ftx-courts-db`. Logs are JSON
(ECS) with the `correlationId` of each request.

## Run it

Requirements: JDK 21 and Maven 3.9, the database of `ftx-infra-postgres` running and the
`ftx-courts-db` migrations applied (see their READMEs).

```bash
mvn package -DskipTests
COURTS_DB_URL=jdbc:postgresql://localhost:5432/ftx COURTS_DB_PASSWORD=<courts_app password> java -jar target/ftx-courts-api-0.1.0.jar
```

With Docker, on the `platform` network:

```bash
docker build -t ftx-courts-api .
docker run --rm --network platform -p 8082:8082 -e COURTS_DB_PASSWORD=<courts_app password> ftx-courts-api
```

## Tests

```bash
mvn verify
```

- Unit tests of the domain and the use cases, and web tests of every response of the contract,
  including a request from a non-administrator that must be refused, spoofed `X-User-*` headers,
  expired, foreign and forged tokens, and the keys being unavailable. The tests sign tokens with
  a key made for each run.
- Integration tests against a real PostgreSQL with the **real migrations** of the sibling
  `ftx-courts-db` clone (`-Dcourts.db.dir=...`, default `../ftx-courts-db`), connected as
  `courts_app` with only the rights `ftx-courts-db` grants it. The database is a Testcontainers
  PostgreSQL 16 when Docker is available, or an existing one given by `FTX_IT_JDBC_URL`,
  `FTX_IT_ADMIN_USER` and `FTX_IT_ADMIN_PASSWORD` (it drops and rebuilds the `courts` schema
  there: never point it at a shared database). Without either, they are skipped.

The `ci` workflow runs `mvn verify`, gitleaks over the whole history and the image build.

## Branching

`develop`, `qa` and `main` accept no direct commits. Per user story: `hu-<id>-dev → develop`, then
`git cherry-pick -x` into `hu-<id>-qa → qa`, then into a release branch `→ main` with the
instructor's approval and the tag.
