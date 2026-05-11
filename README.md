# WebMARS API — v1.1

A REST API backend built as a v1.1 update to [WebMARS](https://github.com/landon/WebMARS), a web-based MIPS simulator. This backend enables users to save, retrieve, and delete MIPS assembly programs as shareable snippets — like a mini Pastebin built specifically for MIPS code.

---

## What It Does

- `POST /snippets/save` — saves a MIPS snippet (title + code) to a PostgreSQL database and returns the saved snippet with a generated ID
- `GET /snippets/{id}` — retrieves a saved snippet by ID
- `DELETE /snippets/{id}` — deletes a snippet from the database

---

## Tech Stack

| Tool | Purpose |
|------|---------|
| Java 23 | Primary language |
| Spring Boot 4 | REST API framework |
| Spring Data JPA | Database access layer |
| Hibernate | ORM (object-relational mapping) |
| PostgreSQL 18 | Relational database |
| pgAdmin 4 | Database GUI |
| Maven | Dependency management |
| Postman | API testing |
| IntelliJ IDEA | IDE |

---

## Database Schema

```sql
CREATE TABLE snippets (
    id SERIAL PRIMARY KEY,
    title VARCHAR(255),
    code TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

---

## Project Structure

```
src/main/java/com/webmars/webmars_api/
├── WebmarsApiApplication.java   # Entry point
├── Snippet.java                 # Entity model
├── SnippetRepository.java       # Database access (JPA)
└── SnippetController.java       # REST endpoints
```

---

## Development Timeline

| Task | Time |
|------|------|
| Spring Boot setup + first GET endpoint | ~2 hours |
| Debugging routing and package issues | ~1 hour |
| PostgreSQL install + pgAdmin setup | ~30 minutes |
| Connecting Spring Boot to PostgreSQL | ~30 minutes |
| Building Snippet model + repository | ~30 minutes |
| Wiring controller to database | ~30 minutes |
| Testing all endpoints in Postman | ~30 minutes |
| **Total** | **~5.5 hours** |

---

## Roadmap

### v1.1 (Current) — Snippet Sharing
- [x] POST /snippets/save
- [x] GET /snippets/{id}
- [x] DELETE /snippets/{id}
- [x] PostgreSQL integration
- [x] Auto-generated timestamps

### v1.2 (Planned) — User Accounts
- [ ] Auth system (login/register)
- [ ] JWT token authentication
- [ ] Save snippets to user accounts
- [ ] Execution history with timestamps
- [ ] Track most recently used programs
- [ ] Public vs private snippets

---

## Getting Started

### Prerequisites
- Java 23
- PostgreSQL 18
- Maven

### Setup

1. Clone the repo
```bash
git clone https://github.com/yourusername/webmars-api.git
```

2. Create a PostgreSQL database called `webmars` and run:
```sql
CREATE TABLE snippets (
    id SERIAL PRIMARY KEY,
    title VARCHAR(255),
    code TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

3. Update `src/main/resources/application.properties`:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/webmars
spring.datasource.username=postgres
spring.datasource.password=YOUR_PASSWORD
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.datasource.driver-class-name=org.postgresql.Driver
```

4. Run the app via IntelliJ or:
```bash
./mvnw spring-boot:run
```

Server starts on `http://localhost:8080`

---

Built by Landon as part of the WebMARS project — a web-based MIPS32 simulator built with React + TypeScript + Vite.
