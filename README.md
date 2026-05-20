# CredBridge

CredBridge is a full-stack credit evaluation application for borrowers, lenders, and admins. Borrowers can register, submit basic or verified loan applications, upload supporting documents, grant or revoke consent, and download score reports. Admins can review applications, monitor portfolio metrics, view audit events, and update application statuses.

The backend lives in this repository. The frontend source is in `/home/ruhi/front_credit` and should be pushed to GitHub with this backend, either as a separate repository or copied into a `frontend/` folder before pushing.

## Tech Stack

### Backend

- Java 21
- Spring Boot 4.0.6
- Spring Web MVC, Spring Security, Spring Data JPA, Bean Validation
- PostgreSQL
- Flyway database migrations
- JWT authentication
- OpenAPI/Swagger UI
- Tesseract OCR for document text extraction
- Optional Groq/OpenAI-compatible LLM scoring
- Optional S3-compatible document storage
- Optional ClamAV file scanning

### Frontend

- React 19
- TypeScript
- Vite 7
- Browser `localStorage` for JWT session persistence

## Architecture

```text
React/Vite frontend
  |
  | HTTP JSON + multipart uploads
  v
Spring Boot REST API
  |
  | Spring Security JWT filter
  v
Service layer
  |-- Auth and role management
  |-- Loan application workflow
  |-- Basic and verified scoring
  |-- Document validation, storage, OCR, and extracted fields
  |-- Consent and audit logging
  |-- Admin overview and review queue
  |
  v
PostgreSQL database managed by Flyway
```

Important backend packages:

- `auth`: registration, login, JWT, current user lookup, optional admin bootstrap.
- `application`: basic and verified loan application workflow.
- `scoring`: rule-based scoring, verified scoring, optional AI scoring outputs.
- `document`: file validation, local/S3 encrypted storage, OCR, extracted financial fields.
- `privacy`: consent records and audit events.
- `admin`: admin overview and application review APIs.
- `report`: report data and PDF export.
- `config`: security, CORS, OpenAPI, async, and rate limiting.

## Database Schema

Flyway migrations are in `src/main/resources/db/migration`.

Main tables:

- `users`: application users with `BORROWER`, `LENDER`, or `ADMIN` roles.
- `loan_applications`: borrower applications, mode (`BASIC` or `VERIFIED`), status, requested amount, tenure, review notes, reviewer, and timestamps.
- `financial_profiles`: one profile per application with employment type, income, expenses, debt payment, repayment history, and income stability.
- `credit_scores`: one score per application with score, risk level, debt-to-income ratio, expense ratio, repayment capacity, suggested loan limit, factors, AI/LLM metadata, recommendation, confidence, and verified scoring fields.
- `documents`: uploaded application documents with document type, stored path, processing status, and timestamp.
- `extracted_financial_fields`: OCR/extracted values per document, including income, expenses, debt payments, deposits, withdrawals, business revenue, tax value, invoice total, and confidence.
- `consent_records`: consent state per user/application.
- `audit_events`: compliance and access events tied to users and applications.

Relationships:

- `users 1 -> many loan_applications`
- `loan_applications 1 -> 1 financial_profiles`
- `loan_applications 1 -> 1 credit_scores`
- `loan_applications 1 -> many documents`
- `documents 1 -> 1 extracted_financial_fields`
- `loan_applications/users 1 -> many consent_records`
- `loan_applications/users 1 -> many audit_events`

Flyway is enabled by default and `spring.jpa.hibernate.ddl-auto` defaults to `validate`, so the database schema is expected to come from migrations, not Hibernate auto-generation.

## Backend Setup

### Prerequisites

Install these on the local machine:

- Java 21
- Maven, or use the included `./mvnw`
- PostgreSQL 14+
- Tesseract OCR if verified document processing is needed locally
- Optional: ClamAV if virus scanning is enabled
- Optional: Docker if running the backend container

### Create the Database

Create a local PostgreSQL database and user. Example:

```sql
CREATE DATABASE credbridge;
CREATE USER credbridge_user WITH PASSWORD 'change-me';
GRANT ALL PRIVILEGES ON DATABASE credbridge TO credbridge_user;
```

If using PostgreSQL 15+ and a non-owner user, also grant schema privileges after connecting to the `credbridge` database:

```sql
GRANT ALL ON SCHEMA public TO credbridge_user;
```

### Configure Environment

Copy the example file and edit local values:

```bash
cp .env.example .env
```

Required backend values:

```env
DB_URL=jdbc:postgresql://localhost:5432/credbridge
DB_USERNAME=credbridge_user
DB_PASSWORD=change-me
JWT_SECRET=change-this-to-a-long-random-secret-at-least-32-characters
DOCUMENT_ENCRYPTION_KEY=change-this-32-byte-document-key
CORS_ALLOWED_ORIGINS=http://localhost:3000
```

Optional but commonly needed values:

```env
SERVER_PORT=8080
AI_ENABLED=true
AI_PROVIDER=groq
AI_API_KEY=your-groq-api-key
AI_BASE_URL=https://api.groq.com/openai/v1
AI_MODEL=llama-3.1-8b-instant
TESSERACT_COMMAND=tesseract
TESSERACT_LANGUAGE=eng
ADMIN_BOOTSTRAP_EMAIL=admin@example.com
ADMIN_BOOTSTRAP_PASSWORD=change-this-admin-password
```

`ADMIN_BOOTSTRAP_PASSWORD` must be at least 12 characters. If both admin bootstrap values are blank, no admin user is created automatically.

For local file storage, keep:

```env
DOCUMENT_STORAGE_PROVIDER=local
DOCUMENT_UPLOAD_DIR=uploads/documents
DOCUMENT_VIRUS_SCAN_ENABLED=false
```

For S3-compatible storage, set:

```env
DOCUMENT_STORAGE_PROVIDER=s3
DOCUMENT_S3_BUCKET=your-bucket
DOCUMENT_S3_REGION=us-east-1
DOCUMENT_S3_ENDPOINT=
DOCUMENT_S3_ACCESS_KEY=your-access-key
DOCUMENT_S3_SECRET_KEY=your-secret-key
DOCUMENT_S3_PATH_STYLE_ACCESS=true
```

Do not commit `.env`. It is already ignored by `.gitignore`.

### Run Backend

This app reads environment variables from the shell. One simple local option is:

```bash
set -a
source .env
set +a
./mvnw spring-boot:run
```

The backend runs on `http://localhost:8080` by default.

Useful URLs:

- Health/root response: `http://localhost:8080/`
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

### Backend Tests

```bash
./mvnw test
```

Tests use the H2 PostgreSQL compatibility mode configured in `src/test/resources/application-test.properties`.

## Frontend Setup

The frontend source currently lives at:

```text
/home/ruhi/front_credit
```

If pushing backend and frontend in one GitHub repository, copy that folder into this repository as `frontend/` before committing:

```bash
cp -R /home/ruhi/front_credit ./frontend
```

Do not commit `frontend/node_modules`, `frontend/dist`, or frontend `.env` files.

### Frontend Prerequisites

- Node.js 20+
- npm

### Configure Frontend Environment

Inside the frontend folder:

```bash
cp .env.example .env
```

For local development with the Vite proxy, this can stay blank:

```env
VITE_API_BASE_URL=
```

When the frontend is served separately from the backend without the Vite dev proxy, set the backend URL:

```env
VITE_API_BASE_URL=http://localhost:8080
```

For production, set `VITE_API_BASE_URL` to the deployed backend base URL and set backend `CORS_ALLOWED_ORIGINS` to the deployed frontend origin.

### Run Frontend

From the frontend folder:

```bash
npm install
npm run dev
```

The frontend runs on `http://localhost:3000`.

Build command:

```bash
npm run build
```

Preview command:

```bash
npm run preview
```

## API Overview

Authentication:

- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/auth/me`

Applications:

- `POST /api/applications/basic`
- `POST /api/applications/verified`
- `GET /api/applications`
- `GET /api/applications/{id}`
- `PATCH /api/applications/{id}/status`

Documents:

- `POST /api/documents/upload`
- `GET /api/documents/application/{applicationId}`
- `DELETE /api/documents/{documentId}`

Reports:

- `GET /api/reports/{applicationId}`
- `GET /api/reports/{applicationId}/pdf`

Privacy:

- `GET /api/privacy/consents`
- `PATCH /api/privacy/applications/{applicationId}/consent/revoke`
- `GET /api/privacy/audit-events` admin only

Admin:

- `GET /api/admin/overview`
- `GET /api/admin/applications`

Most endpoints require a JWT in:

```http
Authorization: Bearer <token>
```

## Roles and Local Login Flow

Users can register as:

- `BORROWER`: creates applications, uploads documents, views reports, manages consent.
- `LENDER`: frontend routes to the review queue. Backend admin APIs currently require `ADMIN`.
- `ADMIN`: accesses admin overview, review queue, and audit events.

For local testing, either register an admin through the UI or set `ADMIN_BOOTSTRAP_EMAIL` and `ADMIN_BOOTSTRAP_PASSWORD` before starting the backend.

## Document Upload Rules

Supported file types:

- PDF
- PNG
- JPEG/JPG

Default max upload size is 5 MB:

```env
DOCUMENT_MAX_FILE_SIZE_BYTES=5242880
```

The backend validates file signatures and extensions. Virus scanning is disabled locally by default. If `DOCUMENT_VIRUS_SCAN_ENABLED=true`, the configured scanner command, usually `clamscan`, must be installed and available on `PATH`.

## Production Notes

Before deploying or sharing with another developer:

- Use a strong `JWT_SECRET`.
- Use a stable `DOCUMENT_ENCRYPTION_KEY`; changing it may make previously encrypted local documents unreadable.
- Keep `spring.jpa.hibernate.ddl-auto=validate` and use Flyway migrations for schema changes.
- Set `SPRING_PROFILES_ACTIVE=prod` for production-like runtime.
- Set `CORS_ALLOWED_ORIGINS` to the exact frontend origin.
- Disable Swagger/OpenAPI in production unless needed:

```env
SPRINGDOC_API_DOCS_ENABLED=false
SPRINGDOC_SWAGGER_UI_ENABLED=false
```

- Prefer S3-compatible document storage for deployed environments.
- Keep `.env`, uploaded documents, build outputs, and dependency folders out of Git.

## Suggested GitHub Push Checklist

1. Decide repository layout:
   - Separate repos: push this backend repo and `/home/ruhi/front_credit` as two GitHub repos.
   - Monorepo: copy `/home/ruhi/front_credit` into `frontend/` in this repo.
2. Confirm `.env` files are not staged.
3. Commit `.env.example` files so the other developer knows what to configure.
4. Run backend tests:

```bash
./mvnw test
```

5. Run frontend build:

```bash
cd frontend
npm install
npm run build
```

6. Push to GitHub.

