# CredBridge

CredBridge is a full-stack credit evaluation platform for borrowers, lenders, and admins. It supports quick self-declared credit checks, verified document-based scoring, consent tracking, audit logs, admin review workflows, and downloadable credit reports.

The project is built as a monorepo:

```text
Credit_Score/
├── src/                         # Spring Boot backend
├── frontend/                    # React + TypeScript frontend
├── sample_documents/            # Demo documents for verified uploads
├── .env.example                 # Backend environment template
├── Dockerfile
└── pom.xml
```

## Key Features

- Borrower registration, login, JWT session handling, and role-based access.
- Basic Mode scoring from self-declared income, expense, debt, repayment history, and income stability.
- Verified Mode flow with document upload, file validation, OCR/text extraction, mismatch checks, and verified scoring.
- Admin dashboard for portfolio metrics, application review, status updates, and audit visibility.
- Consent management and audit events for privacy-sensitive credit workflows.
- PDF credit report generation for scored applications.
- Optional Groq/OpenAI-compatible LLM layer for risk explanation and lending recommendation.

## Tech Stack

| Area | Technology |
| --- | --- |
| Backend | Java 21, Spring Boot 4, Spring Web MVC |
| Security | Spring Security, JWT, BCrypt |
| Database | PostgreSQL, Flyway, Spring Data JPA |
| Frontend | React 19, TypeScript, Vite |
| Documents | Multipart upload, encrypted local/S3 storage, Tesseract OCR |
| Reports | OpenPDF |
| API Docs | OpenAPI / Swagger UI |
| Testing | JUnit, MockMvc, H2 PostgreSQL compatibility mode |

## Architecture

```text
┌──────────────────────────────┐
│        React + Vite UI        │
│ Borrower / Lender / Admin     │
└───────────────┬──────────────┘
                │ HTTP JSON + multipart uploads
                v
┌──────────────────────────────┐
│      Spring Boot REST API     │
│ Auth, applications, reports   │
└───────────────┬──────────────┘
                │ JWT filter + role checks
                v
┌──────────────────────────────┐
│         Service Layer         │
│ scoring, OCR, consent, admin  │
└───────┬───────────┬──────────┘
        │           │
        v           v
┌──────────────┐  ┌────────────────────┐
│ PostgreSQL   │  │ Document Storage    │
│ Flyway schema│  │ local encrypted/S3  │
└──────────────┘  └────────────────────┘
        │
        v
┌──────────────────────────────┐
│ Optional LLM Scoring Client   │
│ Groq/OpenAI-compatible API    │
└──────────────────────────────┘
```

## Workflow

### Borrower: Basic Mode

```text
Register/Login
  -> Create Basic Application
  -> Rule-based score calculated immediately
  -> Report available
  -> Lender/Admin can review
```

### Borrower: Verified Mode

```text
Register/Login
  -> Create Verified Application
  -> Upload required documents
  -> Validate file type and size
  -> Store document securely
  -> Extract text with PDF text extraction or OCR
  -> Parse financial fields
  -> Compare declared vs verified values
  -> Calculate verified score
  -> Report available
```

### Admin/Lender Review

```text
Login as staff
  -> View applications and portfolio metrics
  -> Inspect score, risk, AI/verified status, and reports
  -> Move application to UNDER_REVIEW, APPROVED, or REJECTED
  -> Audit event is recorded
```

## Domain Model

```text
users
  └── loan_applications
        ├── financial_profiles
        ├── credit_scores
        ├── documents
        │     └── extracted_financial_fields
        ├── consent_records
        └── audit_events
```

Main tables are managed by Flyway migrations in:

```text
src/main/resources/db/migration
```

Important tables:

- `users`: borrower, lender, and admin accounts.
- `loan_applications`: application status, mode, requested amount, tenure, review metadata.
- `financial_profiles`: declared income, expenses, debt, repayment history, and income stability.
- `credit_scores`: score, risk level, ratios, capacity, verified metrics, LLM metadata, recommendation.
- `documents`: uploaded file metadata and processing status.
- `extracted_financial_fields`: OCR/text-extracted financial values.
- `consent_records`: user/application consent state.
- `audit_events`: compliance and access trail.

## Backend Packages

| Package | Responsibility |
| --- | --- |
| `auth` | Registration, login, JWT, user roles, admin bootstrap |
| `application` | Basic and verified application lifecycle |
| `scoring` | Basic scoring, verified scoring, AI/LLM decision data |
| `document` | File validation, storage, OCR, extracted financial fields |
| `privacy` | Consent records and audit events |
| `admin` | Admin overview and application review APIs |
| `report` | JSON report and PDF export |
| `config` | Security, CORS, OpenAPI, async, rate limiting |

## Local Setup

### Prerequisites

- Java 21+
- Node.js 20+
- npm
- PostgreSQL 14+
- Tesseract OCR if you want real OCR for image documents
- Optional: Docker, ClamAV, S3-compatible storage

### 1. Clone

```bash
git clone git@github.com:ruhi19par/Credit_Score.git
cd Credit_Score
```

### 2. Create PostgreSQL Database

```sql
CREATE DATABASE credbridge;
CREATE USER credbridge_user WITH PASSWORD 'change-me';
GRANT ALL PRIVILEGES ON DATABASE credbridge TO credbridge_user;
```

For PostgreSQL 15+ with a non-owner user:

```sql
GRANT ALL ON SCHEMA public TO credbridge_user;
```

### 3. Configure Backend

Copy the example environment file:

```bash
cp .env.example .env
```

Minimum required values:

```env
DB_URL=jdbc:postgresql://localhost:5432/credbridge
DB_USERNAME=credbridge_user
DB_PASSWORD=change-me
JWT_SECRET=replace-with-a-long-random-secret-at-least-32-characters
DOCUMENT_ENCRYPTION_KEY=replace-this-32-byte-document-key
CORS_ALLOWED_ORIGINS=http://localhost:3000
```

Optional AI scoring:

```env
PORT=8080
SERVER_PORT=8080
AI_ENABLED=true
AI_PROVIDER=groq
AI_API_KEY=your-api-key
AI_BASE_URL=https://api.groq.com/openai/v1
AI_MODEL=llama-3.1-8b-instant
```

Optional admin bootstrap:

```env
ADMIN_BOOTSTRAP_EMAIL=admin@example.com
ADMIN_BOOTSTRAP_PASSWORD=change-this-admin-password
```

`ADMIN_BOOTSTRAP_PASSWORD` must be at least 12 characters.

### 4. Run Backend

Linux/macOS:

```bash
set -a
source .env
set +a
./mvnw spring-boot:run
```

Windows PowerShell:

```powershell
$env:DB_URL="jdbc:postgresql://localhost:5432/credbridge"
$env:DB_USERNAME="credbridge_user"
$env:DB_PASSWORD="change-me"
$env:JWT_SECRET="replace-with-a-long-random-secret-at-least-32-characters"
$env:DOCUMENT_ENCRYPTION_KEY="replace-this-32-byte-document-key"
cmd /c mvnw.cmd spring-boot:run
```

Backend URLs:

- API root: `http://localhost:8080/`
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

### 5. Run Frontend

```bash
cd frontend
cp .env.example .env
npm install
npm run dev
```

Frontend URL:

```text
http://localhost:3000
```

For local development, `VITE_API_BASE_URL` can stay blank because Vite proxies `/api` to the backend. For separate deployment, set:

```env
VITE_API_BASE_URL=https://your-backend-url
```

## API Overview

Most endpoints require:

```http
Authorization: Bearer <token>
```

| Area | Endpoint |
| --- | --- |
| Auth | `POST /api/auth/register` |
| Auth | `POST /api/auth/login` |
| Auth | `GET /api/auth/me` |
| Applications | `POST /api/applications/basic` |
| Applications | `POST /api/applications/verified` |
| Applications | `GET /api/applications` |
| Applications | `GET /api/applications/{id}` |
| Applications | `PATCH /api/applications/{id}/status` |
| Documents | `POST /api/documents/upload` |
| Documents | `GET /api/documents/application/{applicationId}` |
| Documents | `DELETE /api/documents/{documentId}` |
| Reports | `GET /api/reports/{applicationId}` |
| Reports | `GET /api/reports/{applicationId}/pdf` |
| Privacy | `GET /api/privacy/consents` |
| Privacy | `PATCH /api/privacy/applications/{applicationId}/consent/revoke` |
| Privacy | `GET /api/privacy/audit-events` |
| Admin | `GET /api/admin/overview` |
| Admin | `GET /api/admin/applications` |

## Roles

| Role | Capabilities |
| --- | --- |
| `BORROWER` | Create applications, upload documents, view reports, manage consent |
| `LENDER` | Review queue in frontend; application status review through staff APIs |
| `ADMIN` | Admin dashboard, all applications, metrics, audit events, status updates |

## Scoring Summary

### Basic Score

Basic Mode uses declared values:

- Debt-to-income ratio
- Expense ratio
- Repayment capacity
- Repayment history
- Income stability
- Suggested loan limit

### Verified Score

Verified Mode uses extracted document values:

- OCR/text extracted income, expenses, debt, deposits, withdrawals
- Cash flow stability
- Business health indicators
- Declared-vs-verified mismatch detection
- Optional AI/LLM risk explanation and lending recommendation

## Document Upload Rules

Supported file types:

- PDF
- PNG
- JPEG/JPG

Default max size:

```env
DOCUMENT_MAX_FILE_SIZE_BYTES=5242880
```

The backend checks file signatures and extensions. Virus scanning is optional:

```env
DOCUMENT_VIRUS_SCAN_ENABLED=true
DOCUMENT_VIRUS_SCAN_COMMAND=clamscan
```

## Testing

Run backend tests:

```bash
./mvnw test
```

Windows:

```powershell
cmd /c mvnw.cmd test
```

Run frontend build:

```bash
cd frontend
npm run build
```

Test profile uses H2 in PostgreSQL compatibility mode:

```text
src/test/resources/application-test.properties
```

## Deployment Notes

- Use PostgreSQL with Flyway migrations enabled.
- Keep `spring.jpa.hibernate.ddl-auto=validate`.
- Set a strong `JWT_SECRET`.
- Keep `DOCUMENT_ENCRYPTION_KEY` stable; changing it can make existing encrypted local documents unreadable.
- Set `SPRING_PROFILES_ACTIVE=prod` for production-like runtime.
- On Render, use the platform-provided `PORT` value. For local runs, use `PORT` or `SERVER_PORT`.
- Set `CORS_ALLOWED_ORIGINS` to the deployed frontend origin.
- Disable Swagger in production unless needed:

```env
SPRINGDOC_API_DOCS_ENABLED=false
SPRINGDOC_SWAGGER_UI_ENABLED=false
```

- Prefer S3-compatible storage for deployed document storage.
- Never commit `.env`, uploaded documents, build outputs, or dependency folders.

### Static Frontend Routing

If the frontend is deployed as a static site, configure all routes to serve `index.html` so refreshes on client-side routes do not return `Not Found`.

Example rewrite:

```text
/* -> /index.html
```

## Common Troubleshooting

### Backend cannot connect to database

Check:

- PostgreSQL is running.
- `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` are correct.
- For hosted PostgreSQL, SSL may be required:

```env
DB_URL=jdbc:postgresql://host:5432/dbname?sslmode=require
```

### Frontend shows backend offline

Check:

- Backend is running on `http://localhost:8080`.
- Vite proxy is active during local development.
- `VITE_API_BASE_URL` points to the deployed backend if frontend and backend are separate.

### Document upload fails

Check:

- File is PDF, PNG, or JPEG.
- File extension matches real file content.
- File is below max upload size.
- Tesseract is installed if real OCR is required.

## Development Checklist

Before opening a PR:

```bash
./mvnw test
cd frontend
npm run build
```

Also confirm:

- `.env` is not staged.
- Upload folders and generated logs are not staged.
- Database changes are represented as Flyway migrations.
- New user-facing flows are covered by focused backend/frontend checks where practical.
