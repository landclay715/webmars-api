# Backend Development — What I Learned
**Project:** WebMARS API (Spring Boot REST backend for a MIPS Assembly Simulator)  
**Stack:** Java 21, Spring Boot, PostgreSQL, JWT Authentication, Maven  
**Repo:** github.com/landclay715/webmars-api

---

## Day 1 — Security Hardening

### Problem: Secrets were exposed in git history
When building the first version of the API, the database password and JWT signing key were hardcoded directly into source files and committed to GitHub. Anyone who cloned the repo could read them and either connect to the production database or forge authentication tokens.

**Files affected:**
- `application.properties` — contained the real Postgres password
- `JwtUtil.java` — contained the hardcoded JWT signing key as a string literal

### What I did to fix it

**1. Rotated the compromised credentials**
- Changed the Postgres password using `ALTER USER postgres WITH PASSWORD '...'`
- Generated a new JWT secret using `openssl rand -base64 48` which produces a cryptographically random 48-byte string

**2. Scrubbed git history**
- Learned that deleting a file from the repo does NOT remove it from git history — every past commit still contains the secret
- Installed `git-filter-repo` and used it to rewrite every commit in the repo's history, replacing the old secrets with placeholder text
- Force pushed the rewritten history to GitHub
- Notified teammates to delete their local clones and re-clone since the histories were now incompatible

**3. Moved secrets to environment variables**
- Rewrote `application.properties` to use `${VARIABLE_NAME}` syntax instead of hardcoded values
- Updated `JwtUtil.java` to read the JWT secret via Spring's `@Value("${jwt.secret}")` annotation instead of a string literal
- Added a length validation — if `JWT_SECRET` is less than 32 bytes, the app throws on startup rather than running insecurely
- Created a `.env` file locally for development (never committed)
- Added `.env` to `.gitignore`
- Created `.env.example` as a template for teammates showing what variables are needed without exposing real values

### What I learned
- Secrets in git history are permanent unless you rewrite history — just deleting the file is not enough
- Environment variables are the industry standard way to handle secrets in any application
- A `.gitignore` only prevents future commits — it does nothing about past ones
- JWT tokens are signed with a secret key — if that key is exposed, anyone can forge tokens and authenticate as any user

---

## Day 2 — CORS Configuration and Input Validation

### Problem 1: The frontend could not call the backend at all
CORS (Cross-Origin Resource Sharing) is a browser security feature that blocks requests from one domain to another unless the server explicitly allows it. The backend had no CORS configuration, so every request from the frontend was blocked by the browser with the error: `No Access-Control-Allow-Origin header`.

### What I did to fix it
- Added a `CorsConfigurationSource` bean to `SecurityConfig.java`
- Configured allowed origins: `localhost:5173`, `localhost:5174` (local dev), and `https://webmarsimulator.com` (production)
- Set allowed HTTP methods: GET, POST, PUT, DELETE, OPTIONS
- Set allowed headers: Authorization, Content-Type
- Wired it into the Spring Security filter chain with `.cors(Customizer.withDefaults())`

### What I learned
- CORS is enforced by the browser, not the server — the server just tells the browser which origins it trusts
- OPTIONS is a preflight request the browser sends before the real request to check if CORS is allowed — you have to permit it
- `allowCredentials(true)` is required when sending Authorization headers
- Dev and production origins need to be listed separately

---

### Problem 2: No input validation on API requests
The API was accepting any data from the frontend with no checks. A user could register with a 1-character username, an empty password, or special characters that could cause issues.

### What I did to fix it
- Added `spring-boot-starter-validation` dependency to `pom.xml`
- Created a `dto` package (Data Transfer Objects) to separate request shapes from internal entities
- Created `LoginRequest.java` — a Java record with `@NotBlank` and `@Size` annotations on username and password
- Created `RegisterRequest.java` — same as LoginRequest plus a `@Pattern` annotation restricting usernames to letters, numbers, and underscores only

### What I learned
- DTOs (Data Transfer Objects) are a pattern for separating what the API accepts from what the database stores — you never expose your internal entity classes directly to the outside world
- Java records are immutable data carriers — perfect for request bodies since you do not want the data changing after it has been received
- Jakarta validation annotations (`@NotBlank`, `@Size`, `@Pattern`) declaratively define rules on fields — Spring enforces them automatically when you add `@Valid` to a controller method parameter
- Validating input early at the API boundary prevents bad data from ever reaching the database

---

## Day 3 — Auth Controller Fixes and Global Exception Handling

### Problem 1: Wrong password returned HTTP 500
The original `AuthController` threw a plain `RuntimeException` when a password was wrong. Spring has no idea what to do with an uncaught `RuntimeException` so it returns HTTP 500 (Internal Server Error). This meant the frontend could not tell whether the server crashed or the user just typed the wrong password.

### What I did to fix it
- Rewrote both `/auth/register` and `/auth/login` to use `ResponseStatusException` instead of `RuntimeException`
- Wrong password now returns `401 Unauthorized` with the message "Invalid credentials"
- Unknown username also returns `401 Unauthorized` with the same message — using the same message for both prevents an attacker from figuring out which usernames exist (user enumeration)
- Duplicate username on register returns `409 Conflict`
- Both endpoints now accept the typed DTOs (`LoginRequest`, `RegisterRequest`) with `@Valid` so validation runs automatically

### What I learned
- HTTP status codes are a contract between the server and the client — using the wrong one breaks that contract and makes the frontend's job harder
- `ResponseStatusException` is Spring's way of throwing an HTTP error with a specific status code and message
- Returning the same error message for "user not found" and "wrong password" is a security best practice — it prevents user enumeration attacks where someone can tell which usernames exist on the platform
- 401 = not authenticated, 403 = authenticated but not allowed, 404 = not found, 409 = conflict (duplicate), 500 = server error

---

### Problem 2: Password field was being exposed in API responses
The `User` entity had a `getPassword()` method. If Spring ever serialized a `User` object into a JSON response, the BCrypt hashed password would be included. Even hashed passwords should not be sent to clients.

### What I did to fix it
- Added `@JsonIgnore` to the password field in `User.java`
- Now any time a `User` object is converted to JSON, the password field is silently excluded

### What I learned
- Jackson is the library Spring uses to convert Java objects to JSON — `@JsonIgnore` tells it to skip a field
- Even BCrypt hashes should not be exposed in API responses — they can be used in offline cracking attempts
- The separation between your internal entity (what the database stores) and your API response (what the client sees) is important — DTOs and `@JsonIgnore` are two tools for managing that separation

---

### Problem 3: Validation errors returned ugly 500 responses
Even after adding `@Valid` to the controllers, when validation failed Spring would return a messy unreadable error response that leaked internal implementation details.

### What I did to fix it
- Created `GlobalExceptionHandler.java` with the `@RestControllerAdvice` annotation
- Added a handler method for `MethodArgumentNotValidException` (the exception Spring throws when `@Valid` fails)
- The handler loops through all field errors and builds a clean JSON map of field name to error message
- Returns HTTP 400 Bad Request

**Before:**
```
500 Internal Server Error — wall of stack trace text
```

**After:**
```json
{
  "username": "Username must be between 3 and 32 characters",
  "password": "Password is required"
}
```

### What I learned
- `@RestControllerAdvice` is a global interceptor that watches all controllers — it is a single place to handle exceptions instead of putting try/catch in every controller
- This is the standard Spring pattern for centralized error handling
- Clean error responses are important for the frontend developer — they need to know exactly what went wrong to show the right message to the user
- Separation of concerns: controllers handle the happy path, the exception handler handles the error path

---

## Day 4 — Flyway Database Migrations

### Problem: Hibernate was managing the database schema automatically
The app used `spring.jpa.hibernate.ddl-auto=update` which lets Hibernate automatically alter the database tables on every boot. This is dangerous in production — Hibernate can make wrong decisions about schema changes and silently corrupt or lose data.

### What I did to fix it
- Added Flyway to `pom.xml` as a dependency
- Created a `db/migration` folder in `src/main/resources`
- Wrote `V1__init.sql` as the baseline migration capturing the existing schema
- Changed `ddl-auto` from `update` to `validate` — Hibernate now only checks that the schema matches the entities, it never changes anything
- Created a custom `FlywayConfig.java` bean because Spring Boot 4 changed how Flyway autoconfiguration works and it no longer runs automatically
- Manually ran the V1 migration against the existing database and registered it in `flyway_schema_history`

### What I learned
- Flyway runs SQL migration files in version order (V1, V2, V3...) and tracks which ones have been applied in a `flyway_schema_history` table
- `ddl-auto=validate` is the production-safe setting — it fails fast if the schema doesn't match the entities instead of silently altering things
- `ddl-auto=update` is fine for early development but should never reach production
- Flyway checksums every migration file — if you change a migration that has already run, Flyway will refuse to start
- Spring Boot 4 broke Flyway autoconfiguration — sometimes you need to wire things manually with an explicit `@Bean` when framework conventions change

---

## Day 5 — Snippet Ownership and Visibility

### Problem: Snippets had no concept of ownership
Any user could save, read, or delete any snippet. There was no concept of who created a snippet, whether it was public or private, or when it was last updated.

### What I did to fix it
- Wrote `V2__snippet_ownership.sql` adding `owner_id` (foreign key to users), `visibility` (PUBLIC or PRIVATE), and `updated_at` columns to the snippets table
- Updated `Snippet.java` entity with a `@ManyToOne` relationship to `User`, a `Visibility` enum, and `@PreUpdate` to automatically set `updated_at` on every save
- Created `SaveSnippetRequest.java` record DTO so the API accepts a clean typed request instead of a raw entity
- Added `findByOwnerIdOrderByUpdatedAtDesc` and `findByVisibility` query methods to `SnippetRepository` — Spring Data JPA generates the SQL automatically from the method name
- Rewrote `SnippetController` with ownership-aware endpoints: GET with visibility gating, GET /mine, GET /public (paginated), POST /save (owner set server-side from JWT), DELETE with ownership check
- Added `@AuthenticationPrincipal String username` to controller methods to get the logged-in user from Spring Security's context

### What I learned
- `@AuthenticationPrincipal` pulls the authenticated user's identity directly from Spring Security's context — the server always decides who owns what, the client never supplies it
- `@ManyToOne` means many snippets can belong to one user — the foreign key lives on the snippets table
- Returning 404 instead of 403 to non-owners prevents resource enumeration — an attacker cannot tell whether a resource exists or just that they cannot access it
- Spring Data JPA generates SQL queries from method names — `findByOwnerIdOrderByUpdatedAtDesc` becomes `SELECT * FROM snippets WHERE owner_id = ? ORDER BY updated_at DESC`
- `FetchType.LAZY` can cause Hibernate internals like `hibernateLazyInitializer` to leak into JSON responses — fixed with `@JsonIgnoreProperties`

---

## Day 6 — Run History

### Problem: There was no way to track simulation runs
The frontend had no data about how many times a snippet was run, whether runs succeeded or failed, or which snippets were most popular.

### What I did to fix it
- Wrote `V3__runs.sql` creating a `runs` table with foreign keys to both `snippets` and `users`, plus fields for duration, instruction count, exit status, and error message
- Created `Run.java` entity with an `ExitStatus` enum (COMPLETED, ERROR, PAUSED, ABORTED)
- Created `RunRepository` with two query methods: `findByUserIdOrderByStartedAtDesc` for recent runs, and a custom `@Query` using JPQL aggregation for the most-run leaderboard
- Created three DTOs: `LogRunRequest` (incoming), `RunResponse` (outgoing), `MostRunResponse` (outgoing from projection)
- Built `RunController` with POST /runs, GET /runs/recent, and GET /runs/most-run

### What I learned
- A `MostRunProjection` interface is how Spring maps aggregated JPQL query results — when your query returns computed columns like `COUNT()` and `MAX()`, you cannot map to a regular entity, you need a projection interface with matching getter names
- The `from()` static factory method pattern on DTOs is a clean way to convert entities to response objects — keeps the conversion logic inside the DTO itself
- Two `@ManyToOne` relationships on one entity (`snippet` and `user`) are needed when a record needs to reference two different things — a run has a snippet it ran AND a user who ran it, and these can be different people for public snippets
- `@Query` with JPQL lets you write custom aggregation queries that Spring Data cannot generate automatically from method names

---

## Day 7 — Rate Limiting

### Problem: Auth endpoints had no protection against brute force attacks
Without rate limiting, an attacker could write a script to try millions of password combinations against `/auth/login` or flood `/auth/register` to fill the database with fake accounts.

### What I did to fix it
- Implemented `RateLimitFilter.java` extending `OncePerRequestFilter` — a Spring filter that runs on every request before it reaches any controller
- Used `ConcurrentHashMap<String, List<Instant>>` to track request timestamps per IP address
- Login limited to 5 attempts per IP per 15 minutes — returns 429 Too Many Requests when exceeded
- Register limited to 10 attempts per IP per hour
- Used a sliding window approach — timestamps older than the window are removed on each request, then the remaining count is checked against the limit

### What I learned
- `OncePerRequestFilter` is Spring's base class for filters — `doFilterInternal` runs once per request, and calling `filterChain.doFilter()` passes the request to the next filter or controller
- Rate limiting in production should use Redis instead of in-memory storage — in-memory state is per-server, so on multi-server deployments each server would have its own counter and the limit would be multiplied by the number of servers
- The token bucket algorithm (used by libraries like Bucket4j) is more sophisticated than timestamp lists — it handles burst traffic more gracefully
- 429 Too Many Requests is the correct HTTP status code for rate limiting — include a Retry-After header in production to tell clients when to try again

---

## Day 8 — Polish and Error Handling

### Problem 1: Database connections were being held open longer than necessary
`spring.jpa.open-in-view` was enabled by default, keeping the database connection open for the entire HTTP request including JSON serialization. For a REST API this wastes connection pool resources.

### What I did to fix it
- Added `spring.jpa.open-in-view=false` to `application.properties`
- This releases database connections as soon as data fetching is complete, before JSON serialization begins

### Problem 2: GlobalExceptionHandler only handled validation errors
Any other uncaught exception (database conflicts, unexpected errors) would either return a raw Spring error response or leak a stack trace.

### What I did to fix it
- Added a `DataIntegrityViolationException` handler returning 409 — catches race conditions where two simultaneous registrations pass the username check but one fails at the database constraint level
- Added a `ResponseStatusException` handler to ensure these always return clean JSON regardless of which controller threw them
- Added a generic `Exception` handler returning 500 with a safe message — no stack traces ever reach the client

### What I learned
- `DataIntegrityViolationException` is thrown when a database constraint is violated — most commonly a UNIQUE constraint — it is the safety net that catches race conditions the application-level duplicate check cannot catch
- Defense in depth: the application checks for duplicates first, and the database constraint is the final line of defense
- Never expose stack traces to API clients — they reveal internal implementation details that help attackers understand your system
- `spring.jpa.open-in-view=false` is a best practice for REST APIs — the open session in view pattern was designed for server-side rendered HTML, not JSON APIs

---

## Key Concepts Summary

| Concept | What it means |
|---|---|
| Environment variables | Storing secrets outside of code so they are never committed to git |
| Git history scrubbing | Rewriting past commits to remove accidentally committed secrets |
| CORS | Browser security feature — servers must explicitly allow cross-origin requests |
| DTOs | Data Transfer Objects — typed shapes for API requests/responses, separate from database entities |
| Jakarta Validation | Annotation-based input validation (`@NotBlank`, `@Size`, `@Pattern`) |
| ResponseStatusException | Spring's way of returning specific HTTP error codes from a controller |
| @JsonIgnore | Excludes a field from JSON serialization — used to hide the password field |
| @RestControllerAdvice | Global exception handler that catches errors from all controllers |
| HTTP status codes | 200 OK, 400 Bad Request, 401 Unauthorized, 409 Conflict, 500 Server Error |
| User enumeration | Security vulnerability where an API reveals which usernames exist — prevented by using the same error message for both cases |
| Flyway | Versioned SQL migration tool — runs migration files in order and tracks which have been applied |
| @ManyToOne | JPA relationship annotation — many records point to one parent record via a foreign key |
| @AuthenticationPrincipal | Injects the currently authenticated user's identity from Spring Security's context |
| OncePerRequestFilter | Spring base class for filters — intercepts every HTTP request before it reaches a controller |
| Rate limiting | Restricting how many requests a client can make in a time window to prevent brute force and abuse |
| Projection interface | Maps aggregated JPQL query results to a typed Java interface when the result is not a full entity |
| DataIntegrityViolationException | Thrown when a database constraint is violated — the final safety net against duplicate records |
| open-in-view | Spring setting that controls when database connections are released — should be false for REST APIs |
| Sliding window | Rate limiting algorithm that tracks request timestamps and removes expired ones on each check |

---

## Tools and Technologies Used

- **Java 21** — Language
- **Spring Boot 4.0.6** — Framework
- **Spring Security** — Authentication and authorization
- **Spring Data JPA** — Database access layer
- **PostgreSQL** — Relational database
- **jjwt** — JWT token generation and validation
- **BCrypt** — Password hashing
- **Jakarta Validation** — Input validation annotations
- **Maven** — Build tool
- **git-filter-repo** — Git history rewriting tool
- **IntelliJ IDEA** — IDE
- **PowerShell** — Terminal
- **GitHub** — Version control and code review
- **Flyway** — Database migration tool
- **Postman** — API testing
- **Bucket4j (evaluated)** — Token bucket rate limiting library (opted for custom implementation instead)
