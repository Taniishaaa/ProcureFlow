# ProcureFlow

Enterprise procurement and purchase-order management. Employees raise purchase requests, managers approve or reject them, procurement officers source quotations from suppliers, compare them, raise purchase orders and record deliveries, and administrators manage users, master data, settings and the audit trail.

| Layer    | Technology |
|----------|------------|
| Backend  | Java 21, Spring Boot 3.4, Maven, Spring Data JPA (Hibernate), Spring Security (stateless JWT), Bean Validation, springdoc-openapi |
| Database | PostgreSQL (H2 in-memory for zero-setup demo and tests) |
| Frontend | React 18, React Router 6, Vite 5 (plain CSS, no UI framework) |
| Packaging | Dockerfiles for both apps and a `docker-compose.yml` |

> **Read this first — verification status.** The code was written and reviewed in a sandbox that could not reach Maven Central (and, later, the npm registry), so:
> - The **backend has never been compiled or run by the author.** A Java parse of all 87 source files found no syntax errors (the only compiler errors were missing third-party dependencies), but type-level mistakes can still exist. **Run `mvn test` first** (see below). If anything fails to compile, it will point at an exact line and should be a quick fix.
> - The **frontend** was bundled with esbuild to confirm every import and export resolves, but it has not been run in a browser, and `npm run build` with Vite was not executed.
> - **Docker images and `docker-compose.yml` have not been built.**
>
> Everything below describes how the system is designed to behave and how to run it; treat the first run as the real verification.

---

## 1. Quick start

### Prerequisites

| Tool | Version | Needed for |
|------|---------|-----------|
| JDK | 21 | backend |
| Maven | 3.9+ | backend (no Maven wrapper is included) |
| Node.js | 18 or newer (20 recommended) | frontend |
| Docker + Docker Compose | recent | option A only |
| PostgreSQL | 14+ | option C only |

### Option A — Docker Compose (PostgreSQL + API + UI)

```bash
cp .env.example .env        # optional; all values have defaults
docker compose up --build
```

- UI: <http://localhost:3000>
- API: <http://localhost:8080> — Swagger UI at <http://localhost:8080/swagger-ui.html>
- The database is persisted in the `pgdata` volume. `docker compose down -v` wipes it.

### Option B — Local development, no database install (H2)

Terminal 1 — backend with the in-memory H2 database (data is lost on restart):

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=h2
```

Terminal 2 — frontend:

```bash
cd frontend
npm install
npm run dev
```

Open <http://localhost:5173>. The Vite dev server proxies `/api` to `http://localhost:8080` (override with `VITE_API_PROXY`).

### Option C — Local development with PostgreSQL

```sql
CREATE USER procureflow WITH PASSWORD 'procureflow';
CREATE DATABASE procureflow OWNER procureflow;
```

```bash
cd backend
mvn spring-boot:run          # uses the defaults below
```

Run the frontend as in option B. To use other credentials, set the environment variables listed in section 3.

### Run the tests

```bash
cd backend
mvn test
```

Tests use the in-memory H2 database (no PostgreSQL needed) and include unit tests for pricing and JWT handling plus an end-to-end integration test of the whole procurement workflow (request → approval → quotations → comparison → PO → issue → partial/complete delivery → history, audit and notifications), approval gating, ownership and role checks, validation errors, user deactivation and password reset. There is no multi-threaded concurrency test; the locking is reviewed by reading, not load-tested.

### Build for production

```bash
cd backend && mvn clean package          # target/procureflow-backend-1.0.0.jar
java -jar target/procureflow-backend-1.0.0.jar

cd frontend && npm run build             # static files in frontend/dist
```

---

## 2. Demo accounts

Demo data is created automatically **only when the database is empty** (users, categories, suppliers and sample requests, quotations and orders so every page has content). All demo accounts use the password **`Password@123`**.

| Role | Email |
|------|-------|
| Employee | `employee@procureflow.com` (also `employee2@procureflow.com`) |
| Manager | `manager@procureflow.com` |
| Procurement Officer | `procurement@procureflow.com` |
| Administrator | `admin@procureflow.com` |

The login screen has click-to-fill buttons for these. **Disable seeding and change `JWT_SECRET` for anything beyond a local demo** (section 3).

### A five-minute walkthrough

1. Sign in as **Employee** → *New Request*: pick a category, add items, **Save & submit**.
2. Sign in as **Manager** → *Approval Queue* → open the request → **Approve** (rejecting requires a reason).
3. Sign in as **Procurement Officer** → *Approved Requests* → **Add quote** for two or more suppliers → **Compare** (the comparison flags lowest price, fastest delivery and a recommended offer) → **Create PO** → open the order → **Issue to supplier**.
4. As the procurement officer, **Record delivery** (partial first, then the rest) and watch the order move to *Partially received* and then *Completed*, and the request to *Fulfilled*.
5. As **Employee** the request, order and notifications reflect every step; as **Administrator** the *Audit Log* shows each action.

---

## 3. Configuration

All settings are environment variables with working defaults (`backend/src/main/resources/application.yml`).

| Variable | Default | Purpose |
|----------|---------|---------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/procureflow` | JDBC URL |
| `DB_USERNAME` / `DB_PASSWORD` | `procureflow` / `procureflow` | Database credentials |
| `DDL_AUTO` | `update` | Hibernate schema management. Use `validate` plus a migration tool in production |
| `JWT_SECRET` | dev-only value | Signing key, **at least 32 characters. Change it.** |
| `JWT_EXPIRATION_MINUTES` | `480` | Token lifetime |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://localhost:3000` | Comma-separated browser origins |
| `SEED_ENABLED` | `true` | Create demo users/categories/suppliers on an empty database |
| `SEED_SAMPLE_DATA` | `true` | Also create sample requests, quotations and orders |
| `SEED_PASSWORD` | `Password@123` | Password of all demo accounts |
| `EXPOSE_RESET_TOKEN` | `true` | See "Forgot password" below. **Set `false` in real deployments** |

Application settings editable by an administrator in the UI (*Settings*): company name, purchase-order number prefix, and the maximum estimated total per request.

### Forgot password

There is no mail server in this project. With `EXPOSE_RESET_TOKEN=true` the *Forgot password* endpoint returns the reset token (and logs it) so the flow can be demonstrated end to end; the UI then links straight to the reset page. With `false` the endpoint only returns a generic message and you must plug in an email sender (`AuthService.forgotPassword`) to deliver the token.

---

## 4. Roles and what they can do

| Capability | Employee | Manager | Procurement Officer | Administrator |
|---|:---:|:---:|:---:|:---:|
| Create / edit / submit / cancel own requests | ✔ | | | |
| View requests | own | pending & decided | approved | all |
| Approve / reject submitted requests (not own) | | ✔ | | |
| Manage suppliers | | | ✔ | read |
| Record quotations, compare, create / issue / cancel POs, record deliveries | | | ✔ | read |
| View purchase orders | own (issued) | — | all | all |
| Reports (spend = issued orders, by issue date) | | ✔ | ✔ | ✔ |
| Users, categories, settings, audit log | | | | ✔ |

Permissions are enforced on the server (`@PreAuthorize` plus ownership checks in the services). The UI hides what a role cannot use, but is not relied upon for security.

## 5. Workflows and enforced business rules

**Workflow A — request → approval**

`DRAFT → SUBMITTED → APPROVED | REJECTED`, plus `CANCELLED`. A rejected request can be edited and resubmitted.

**Workflow B — quotation → purchase order → delivery**

`APPROVED → ORDERED → FULFILLED` for the request (it becomes *Ordered* as soon as a purchase order is created); the order moves `DRAFT → ISSUED → PARTIALLY_RECEIVED → COMPLETED` (or `CANCELLED`). Cancelling an order returns the request to *Approved* and re-opens all its quotations so another order can be raised.

Rules the **backend** enforces (the integration test covers the main ones):

- A purchase order can only be created from an **approved** request — never from draft, submitted, rejected, cancelled or already-ordered requests.
- Only the owner edits/submits/cancels a request, and only while its status allows it.
- A manager cannot decide on their own request; a rejection requires a reason.
- Only **active** suppliers registered for the request's **category** may quote, one quotation per supplier per request; prices are per request item. Expired quotations cannot be ordered.
- Deliveries cannot exceed the outstanding quantity of any line; order and request statuses are derived from received quantities.
- Quantities and prices are validated (positive, bounded); the maximum request total is configurable.
- Deactivated users lose access immediately (the JWT filter re-reads the user on every request); administrators cannot deactivate themselves or change their own role.
- Every significant action writes an **audit log** entry in the same transaction and notifies the affected users.

**Concurrency and integrity:** state transitions take a pessimistic row lock and re-check the status under that lock (so two managers or two officers cannot both act on the same request); entities also carry an optimistic `@Version`; unique constraints guard duplicates; money uses `BigDecimal` with fixed scale.

**Quotation comparison:** each orderable offer is scored 60 % price and 40 % delivery time; the best is flagged *Recommended*, alongside *Lowest price* and *Fastest*. The recommendation is advisory.

---

## 6. Project structure

```
procureflow/
├── backend/                       Spring Boot application (Maven)
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/
│       ├── main/java/com/procureflow/
│       │   ├── common/            errors, paging, pricing, shared DTOs
│       │   ├── security/          JWT service/filter, security config
│       │   ├── user/              auth, profile, user administration
│       │   ├── category/          categories
│       │   ├── request/           purchase requests, items, approvals
│       │   ├── supplier/          suppliers, quotations, comparison
│       │   ├── order/             purchase orders, deliveries
│       │   ├── notification/      in-app notifications
│       │   ├── audit/             audit log
│       │   ├── settings/          application settings
│       │   ├── report/            dashboards and reports
│       │   └── config/            OpenAPI config, demo data seeder
│       ├── main/resources/        application.yml, application-h2.yml
│       └── test/java/             unit + integration tests
├── frontend/                      React + Vite
│   ├── src/ (api.js, auth.jsx, components/, pages/)
│   ├── Dockerfile, nginx.conf
├── docker-compose.yml
├── .env.example
└── README.md
```

### Screens (30 logical pages)

- **Everyone:** login, forgot password, reset password, dashboard (role-specific), profile & change password, notifications, access denied, not found
- **Employee:** my requests, new/edit request, request details, my orders, order details
- **Manager:** approval queue, request review (approve/reject), approval history, reports
- **Procurement officer:** approved requests, suppliers (list/create/edit/activate), supplier details, quotations list, quotation form, quotation comparison, create purchase order, purchase orders, order details (issue/cancel/record delivery), deliveries, reports
- **Administrator:** users, categories, all requests, purchase orders, suppliers, audit log, reports, settings

## 7. API

Interactive documentation: **`/swagger-ui.html`** (OpenAPI JSON at `/v3/api-docs`). Click *Authorize* and paste the token returned by `POST /api/auth/login`.

| Area | Endpoints (prefix `/api`) |
|------|---------------------------|
| Auth | `POST /auth/login`, `/auth/forgot-password`, `/auth/reset-password` |
| Me | `GET/PUT /me`, `POST /me/change-password` |
| Users (admin) | `GET/POST /users`, `GET/PUT /users/{id}`, `PATCH /users/{id}/active` |
| Categories | `GET /categories`, admin: `POST`, `PUT /{id}`, `PATCH /{id}/active` |
| Requests | `POST /requests`, `GET /requests/mine`, `/pending-approval`, `/approved`, `GET /requests` (admin), `GET/PUT /requests/{id}`, `POST /requests/{id}/submit │ cancel │ approve │ reject` |
| Approvals | `GET /approvals` (decision history) |
| Suppliers | `GET/POST /suppliers`, `GET/PUT /suppliers/{id}`, `PATCH /suppliers/{id}/active` |
| Quotations | `GET/POST /quotations`, `GET/PUT /quotations/{id}`, `GET /requests/{id}/eligible-suppliers`, `GET /requests/{id}/comparison` |
| Purchase orders | `GET/POST /purchase-orders`, `GET /{id}`, `POST /{id}/issue │ cancel │ deliveries`, `GET /{id}/history` |
| Other | `GET /dashboard`, `GET /reports/summary?from&to`, `GET/PUT /settings`, `GET /audit-logs`, `GET/POST /notifications…` |
| Ops | `GET /actuator/health` |

Errors use one JSON shape: `{ "timestamp", "status", "error", "message", "path", "fieldErrors": { … } }`. Lists are paginated (`page`, `size`, max 100) and return `{ content, page, size, totalElements, totalPages }`.

---

## 8. Design decisions and deviations

- **Modular monolith, not four services.** The requirement lists four logical areas; they are implemented as separate packages (`user`, `request`, `supplier`, `order`) inside one deployable. This keeps transactions (e.g. "approve + audit + notify") atomic and the setup simple. The packages communicate through services, so extraction later is feasible.
- **Schema management by Hibernate (`ddl-auto=update`)** for a zero-config demo. For production, set `DDL_AUTO=validate` and add Flyway or Liquibase migrations.
- **JWT in `localStorage`.** Simple and adequate for a demo; production deployments typically prefer an HttpOnly cookie with CSRF protection.
- **No email delivery** (see "Forgot password"). Notifications are in-app only.
- **Request/PO numbers** (`PR-2026-000001`, `PO-2026-000001`) are derived from the database id after insert, so they are unique but not necessarily gap-free.
- **Currency** is displayed as INR in the UI (`frontend/src/utils.js`); the backend stores plain decimal amounts.
- **Approval is single-step** (any manager). Approval thresholds or multi-level chains are not implemented.

## 9. Troubleshooting

| Symptom | Fix |
|---------|-----|
| `Cannot reach the server` in the UI | Backend not running or wrong port. Dev UI expects the API on `localhost:8080`. |
| Backend fails with `Connection refused` to PostgreSQL | Start PostgreSQL / create the database (option C), or use `-Dspring-boot.run.profiles=h2`. |
| Startup error about the JWT secret | `JWT_SECRET` must be at least 32 characters. |
| Browser CORS error in a custom setup | Add your UI origin to `CORS_ALLOWED_ORIGINS`. |
| Login fails after changing `SEED_PASSWORD` | Seeding only runs on an empty database; reset the DB or change the password in the UI. |
| `mvn` not found | Install Maven 3.9+ (no wrapper is bundled) or build in Docker. |
| Port 8080 / 5173 / 3000 in use | Stop the other process or change `server.port` / the compose port mapping. |
