# WebMARS API

REST backend for the [WebMARS MIPS Assembly Simulator](https://webmarsimulator.com). Handles user authentication, snippet persistence, and run logging.

---

## Table of Contents

1. [Project Overview](#project-overview)
2. [V1.2 Features](#v12-features)
3. [Tech Stack](#tech-stack)
4. [Environment Variables](#environment-variables)
5. [Local Setup](#local-setup)
6. [API Endpoints](#api-endpoints)
7. [Authentication](#authentication)
8. [Database Schema](#database-schema)
9. [Deployment on Render](#deployment-on-render)
10. [Security Notes](#security-notes)
11. [Team](#team)

---

## Project Overview

WebMARS API is a Spring Boot REST backend that powers [webmarsimulator.com](https://webmarsimulator.com) — a browser-based MIPS Assembly simulator. The API provides:

- **User accounts** — registration and JWT-based login
- **Snippet storage** — save, update, delete, and browse MIPS code snippets with public/private visibility
- **Run logging** — record simulation runs and surface usage stats per user

Built with Java 21, Spring Boot 4.0.6, PostgreSQL 18, and stateless JWT authentication.

---

## V1.2 Features

- **Snippet ownership** — snippets are owned by the user who created them. The server sets the owner automatically from the JWT token — clients cannot fake ownership.
- **Visibility** — snippets can be `PUBLIC` or `PRIVATE`. Private snippets return `404` to non-owners to prevent enumeration attacks.
- **Paginated public feed** — `GET /snippets/public` returns a paginated list of public snippets without requiring authentication.
- **Ownership-protected delete** — `DELETE /snippets/{id}` returns `404` to non-owners rather than `403`, preventing attackers from confirming whether a snippet exists.
- **Run history** — every simulation run is logged with duration, instruction count, and exit status (`COMPLETED`, `ERROR`, `PAUSED`, `ABORTED`). Users can view their recent runs and a leaderboard of their most-run snippets.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.0.6 |
| Build tool | Maven (wrapper included) |
| Database | PostgreSQL 18 |
| ORM | Spring Data JPA (Hibernate) |
| Security | Spring Security + BCrypt |
| JWT | jjwt 0.12.6 |
| Migrations | Flyway |

---

## Environment Variables

All configuration is injected via environment variables. Never hard-code secrets in `application.properties`.

| Variable | Default | Description |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/webmars` | JDBC connection URL |
| `DB_USER` | `postgres` | Database username |
| `DB_PASSWORD` | *(required)* | Database password — no default |
| `JWT_SECRET` | *(required)* | HMAC signing secret, minimum 32 bytes. Generate with: `openssl rand -base64 48` |
| `JPA_DDL` | `update` | Hibernate DDL mode. Use `validate` in production |
| `JPA_SHOW_SQL` | `false` | Log SQL queries to stdout |

---

## Local Setup

### Prerequisites

- Java 21 (verify with `java -version`)
- PostgreSQL 18 running locally

### Steps

**1. Clone the repository**

```bash
git clone https://github.com/landclay715/webmars-api.git
cd webmars-api/webmars-api
```

**2. Create the database**

```bash
psql -U postgres -c 'CREATE DATABASE webmars;'
```

**3. Configure environment variables**

```bash
cp .env.example .env
```

Open `.env` and fill in `DB_PASSWORD` and `JWT_SECRET`. At minimum:

```
DB_PASSWORD=your_postgres_password
JWT_SECRET=$(openssl rand -base64 48)
```

**4. Export the variables into your shell**

```bash
export $(grep -v '^#' .env | xargs)
```

> **PowerShell note:** `DB_PASSWORD` must not contain special characters like `$` when setting env vars in PowerShell. Use single quotes: `$env:DB_PASSWORD='yourpassword'` not double quotes.

**5. Build**

```bash
./mvnw clean package -DskipTests
```

**6. Run**

```bash
./mvnw spring-boot:run
```

**7. Verify**

```bash
curl http://localhost:8080/snippets/1
```

A `404` response (not `connection refused`) confirms the server is up.

---

## API Endpoints

### Auth (public)

| Method | Path | Description | Body |
|---|---|---|---|
| `POST` | `/auth/register` | Register a new user | `{ "username": "...", "password": "..." }` |
| `POST` | `/auth/login` | Login and receive a JWT | `{ "username": "...", "password": "..." }` → `{ "token": "..." }` |

### Snippets

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/snippets/{id}` | Optional | Fetch snippet by ID. Anonymous users see public snippets only. |
| `GET` | `/snippets/mine` | Required | All snippets owned by the authenticated user. |
| `GET` | `/snippets/public` | None | Paginated public snippet feed. Params: `page`, `size`. |
| `POST` | `/snippets/save` | Required | Save a new snippet. Body: `{ "title", "code", "visibility" }` |
| `PUT` | `/snippets/{id}` | Required | Update a snippet. Must be the owner. |
| `DELETE` | `/snippets/{id}` | Required | Delete a snippet. Must be the owner. |

### Runs

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/runs` | Required | Log a simulation run. Body: `{ "snippetId", "durationMs", "instructionsExecuted", "exitStatus", "errorMessage" }` |
| `GET` | `/runs/recent` | Required | Recent runs for the authenticated user. Param: `limit` (default `20`). |
| `GET` | `/runs/most-run` | Required | Most-run programs for the authenticated user. Param: `limit` (default `10`). |

---

## Authentication

Protected endpoints require a `Bearer` token in the `Authorization` header:

```
Authorization: Bearer <token>
```

- Tokens are signed with HMAC-SHA and expire after **8 hours**.
- An incorrect password returns `401 Unauthorized`, not `500`.
- Obtain a token via `POST /auth/login`.

---

## Database Schema

### `users`

Stores registered accounts. Passwords are stored as BCrypt hashes — plaintext is never persisted.

### `snippets`

Stores MIPS code snippets. Each row has an `owner_id` foreign key to `users` and a `visibility` field (`PUBLIC` or `PRIVATE`). Private snippets are only accessible to their owner.

### `runs`

Records individual simulation runs. References both `snippet_id` and `user_id` via foreign keys, allowing per-user run history and aggregate stats.

---

## Deployment on Render

**1. Push your branch to GitHub.**

**2. Create a Render Web Service**

- Build command: `./mvnw clean package -DskipTests`
- Start command: `java -jar target/webmars-api-0.0.1-SNAPSHOT.jar`

**3. Add a Render PostgreSQL service**

Link it to the web service and copy the connection details into your environment variables.

**4. Set environment variables in the Render dashboard**

Set all variables from the [Environment Variables](#environment-variables) table. Use `JPA_DDL=validate` in production to prevent Hibernate from altering the schema on startup.

**5. Render free-tier notes**

- Web services on the free tier **sleep after 15 minutes** of inactivity. The first request after sleep may be slow.
- Free PostgreSQL instances **expire after 90 days** and must be recreated manually.

---

## Security Notes

- **Never commit secrets to git.** `.env` is in `.gitignore`. Use environment variables in all deployed environments.
- **Generate JWT_SECRET with:** `openssl rand -base64 48` — this produces a 48-byte secret well above the 32-byte minimum.
- **Private and foreign-owned resources return `404`, not `403`.** This prevents resource enumeration — an attacker cannot distinguish "does not exist" from "exists but you can't see it."

---

## Known Issues and Troubleshooting

### Flyway not running on Spring Boot 4

Spring Boot 4 changed Flyway autoconfiguration. If Flyway migrations are not running, the app requires an explicit `FlywayConfig` bean rather than relying on autoconfiguration. This is already implemented in `FlywayConfig.java`.

### PostgreSQL password reset

If you are locked out of PostgreSQL, edit `C:\Program Files\PostgreSQL\18\data\pg_hba.conf` and change the IPv4 line from `scram-sha-256` to `trust`. Restart the PostgreSQL service, connect without a password, run `ALTER USER postgres WITH PASSWORD 'newpassword'`, then revert `pg_hba.conf` back to `scram-sha-256` and restart the service again.

### PowerShell environment variables with special characters

Passwords containing `$` or other special characters must be set using single quotes in PowerShell to prevent variable interpolation: `$env:DB_PASSWORD='yourpassword'`

### Flyway checksum mismatch

If you manually create a table and insert it into `flyway_schema_history` with checksum `0`, Flyway will fail on next boot with a checksum mismatch error. Fix it by running:

```sql
UPDATE flyway_schema_history SET checksum = <value from error log> WHERE version = '<version>';
```

---

## Team

| Name | Role |
|---|---|
| Bryan Djenabia | Frontend |
| Landon Clay | Backend |
| Zachary Gass | Simulator / Testing |
