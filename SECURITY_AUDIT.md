# WebMARS API — Security Audit Report

**Date:** 2026-05-19  
**Auditor:** Security Review (adversarial, full codebase)  
**Scope:** All controllers, filters, entities, DTOs, configuration, SQL migrations  
**Status:** DO NOT FIX UNTIL REVIEWED — audit only

---

## Executive Summary

The API has a solid foundation: BCrypt password hashing, JJWT 0.12.x with algorithm enforcement, validation annotations, and a custom global exception handler that avoids leaking stack traces. However, several meaningful vulnerabilities exist ranging from a committed database credential to missing authorization checks that allow any user to log runs against any snippet, a storage exhaustion attack via an unbounded `code` field, and a rate-limiting implementation that is both memory-leaky and broken behind a reverse proxy.

**Findings by severity:**

| Severity | Count |
|----------|-------|
| Critical | 2 |
| High | 4 |
| Medium | 8 |
| Low | 11 |

---

## CRITICAL

---

### C1 — Plaintext Credential Committed to Repository

**File:** `.env:1`  
**Line:** `DB_PASSWORD=webmars123`

**Description:**  
The `.env` file contains a real database password (`webmars123`) and is present in the project directory. If this file is tracked by git (check with `git ls-files .env`), the credential is permanently in git history even after deletion. The `.env.example` file exists as a template, but the actual `.env` with a real value is present alongside it.

The `JWT_SECRET=paste-your-token-here` value is a placeholder (21 bytes), which means `JwtUtil` would throw `IllegalStateException` on startup if used as-is — so that value is not the actual production secret. But the DB password appears to be a real credential.

**Exploit:**  
Any developer who clones the repo, any CI/CD system with repo access, or any attacker who obtains the git history gains direct database access credentials.

**Fix:**  
- Add `.env` to `.gitignore` immediately.
- Rotate `webmars123` if it has ever been pushed.
- Use a secrets manager (AWS Secrets Manager, Vault, etc.) or environment-injected variables in CI/CD — never commit `.env` files containing real secrets.

---

### C2 — Missing Access Control on POST /runs: Any User Can Log a Run for Any Snippet

**File:** `RunController.java:31-41`

```java
@PostMapping
public RunResponse log(@Valid @RequestBody LogRunRequest req, @AuthenticationPrincipal String username){
    User me = users.findByUsername(username).orElseThrow(...);
    Snippet s = snippets.findById(req.snippetId()).orElseThrow(...); // no ownership/visibility check
    Run r = new Run();
    r.setUser(me);
    r.setSnippet(s);
    ...
```

**Description:**  
After verifying the user exists and the snippet exists, no check is made that the authenticated user owns or has read access to the snippet. Any valid JWT holder can log a run for ANY snippet ID, including private snippets owned by other users.

**Exploit:**
1. Attacker brute-forces snippet IDs (`snippetId: 1`, `2`, `3`, ...).
2. A 404 means the snippet doesn't exist. A 201 means it does — even if private. This is a **private snippet existence oracle**.
3. Attacker can inflate or deflate run counts for any snippet (corrupting statistics/leaderboards).
4. The `RunResponse` includes `snippetTitle` — so a successful log call leaks the title of a private snippet the attacker doesn't own.
5. A malicious user could create thousands of run records against other users' snippets, filling the database.

**Severity:** Critical — information disclosure + data integrity violation + storage abuse.

**Fix:**  
Before saving the run, verify that `s.getVisibility() == PUBLIC || s.getOwner().getUsername().equals(username)`. Return 403/404 if the user doesn't have access.

---

## HIGH

---

### H1 — GET /snippets/** Wildcard Bypasses Spring Security for /snippets/mine

**File:** `SecurityConfig.java:49-50`

```java
.requestMatchers(HttpMethod.GET, "/snippets/public").permitAll()
.requestMatchers(HttpMethod.GET, "/snippets/**").permitAll()  // overly broad
```

**Description:**  
The second rule (`/snippets/**`) matches every GET request under `/snippets/`, including `/snippets/mine`. This means Spring Security never enforces authentication on `GET /snippets/mine` — the protection relies entirely on the application-layer null check in the controller. The first rule (`/snippets/public`) is made completely redundant by the second.

The controller does handle this gracefully today (calling `findByUsername(null)` returns an empty Optional, then `orElseThrow` returns 401), but this is defense-in-depth failure: security is enforced by business logic rather than the security framework.

**Exploit:**  
If a future refactor changes the null handling in `SnippetController.mine()`, or if Spring's `@AuthenticationPrincipal` injection behavior ever changes, the endpoint becomes unauthenticated. Path traversal or URL normalization tricks (e.g., `/snippets/;mine`, `/snippets/%2Fmine`) might bypass the Spring Security path matcher while still routing to the correct servlet path.

**Fix:**  
Remove the overly broad wildcard. Define exactly which endpoints need to be public:
```java
.requestMatchers(HttpMethod.GET, "/snippets/public").permitAll()
.requestMatchers(HttpMethod.GET, "/snippets/{id}").permitAll()
.anyRequest().authenticated()
```
Move `GET /snippets/mine` to the authenticated set (covered by `anyRequest().authenticated()`).

---

### H2 — No Rate Limiting on Authenticated Endpoints (POST /runs Especially)

**File:** `RateLimitFilter.java:47-63`

**Description:**  
`RateLimitFilter` only covers `/auth/login` and `/auth/register`. Every other endpoint — including `POST /runs`, `POST /snippets/save`, `GET /snippets/public` — has no rate limiting.

**Exploits:**
- `POST /runs`: A valid token holder can send thousands of requests per second, creating millions of run records. Combined with no code-size validation (H3), this is a direct database storage exhaustion attack.
- `POST /snippets/save`: Unlimited snippet creation fills the database.
- `GET /snippets/public`: Can be hammered to scrape the entire public snippet catalog.
- `GET /snippets/{id}`: Sequential IDs can be enumerated rapidly with no throttling.

**Fix:**  
Apply rate limiting (per-user, not just per-IP for authenticated endpoints) to mutating endpoints. Consider:
- 60 snippet saves per hour per user
- 300 run logs per hour per user
- General per-IP rate limiting on all endpoints at the reverse proxy or servlet filter level

---

### H3 — Unbounded `code` Field Allows Storage and Memory Exhaustion

**File:** `SaveSnippetRequest.java:12`

```java
@NotBlank(message = "Code is required")
String code,   // NO @Size constraint
```

**Description:**  
The `code` field has no maximum size. The entity maps it to a PostgreSQL `TEXT` column (theoretical max ~1 GB per row). An attacker with a valid token can submit a request body with a multi-megabyte `code` value. Jackson will deserialize the entire payload into a Java `String` before any validation runs.

**Exploit:**
```bash
# Create a snippet with 50MB of code
curl -X POST /snippets/save \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"title":"x","code":"<50MB of A>","visibility":"PRIVATE"}'
```

This can:
1. Exhaust JVM heap during deserialization
2. Fill PostgreSQL storage
3. Cause massive response latency when reading back `/snippets/mine` (returns all snippets, including the bloated one)

The same issue applies to `errorMessage` in `LogRunRequest.java:12` (no `@Size` constraint, maps to `TEXT` column).

**Fix:**  
- Add `@Size(max = 65536)` (or whatever the domain maximum is) to `code` in `SaveSnippetRequest`.
- Add `@Size(max = 2048)` to `errorMessage` in `LogRunRequest`.
- Configure Spring's `max-http-request-header-size` and server's max request body size in `application.properties`:
  ```properties
  spring.servlet.multipart.max-request-size=1MB
  server.tomcat.max-http-form-post-size=1MB
  ```

---

### H4 — Username Enumeration via Login Timing Attack

**File:** `AuthController.java:39-47`

```java
User found = userRepository.findByUsername(req.username())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

if (!passwordEncoder.matches(req.password(), found.getPassword())) {
    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
}
```

**Description:**  
Both branches return the same error message ("Invalid credentials") and the same HTTP status (401), which is correct to prevent username enumeration. However, the **response time** differs:

- **Non-existent user:** DB index lookup returns empty → immediately throws. No BCrypt work done. Response in ~5ms.
- **Existing user, wrong password:** DB lookup succeeds → BCrypt.matches() runs → ~200ms of CPU work → then throws.

An attacker can measure response time to determine whether a username exists, regardless of the password submitted.

**Exploit:**  
```
POST /auth/login {"username":"alice","password":"xxxxx"} → 201ms → alice EXISTS
POST /auth/login {"username":"zzzzz","password":"xxxxx"} →   4ms → zzzzz DOES NOT EXIST
```

This is particularly dangerous combined with the 5 attempts / 15 minute rate limit, since each probe only consumes one attempt.

**Fix:**  
When the user is not found, perform a dummy BCrypt comparison to equalize response time:
```java
private static final String DUMMY_HASH = new BCryptPasswordEncoder().encode("dummy");

User found = userRepository.findByUsername(req.username()).orElse(null);
if (found == null) {
    passwordEncoder.matches(req.password(), DUMMY_HASH); // equalize timing
    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
}
```

---

## MEDIUM

---

### M1 — Memory Leak in RateLimitFilter: IP Map Grows Without Bound

**File:** `RateLimitFilter.java:20-21`

```java
private final Map<String, List<Instant>> loginAttempts = new ConcurrentHashMap<>();
private final Map<String, List<Instant>> registerAttempts = new ConcurrentHashMap<>();
```

**Description:**  
Individual timestamps within each list are pruned via `removeIf`, but the IP-keyed entries themselves are never removed from the outer map. Every unique IP address that ever contacts the API creates a permanent entry. In production over months with real traffic, this map grows indefinitely. An attacker with a botnet of distinct IPs can accelerate this.

**Fix:**  
Use an LRU cache or Caffeine `LoadingCache` with an expiry TTL matching the window, or periodically sweep the map for entries whose lists are empty and expired.

---

### M2 — Rate Limiting Broken Behind Reverse Proxy

**File:** `RateLimitFilter.java:45`

```java
String ip = request.getRemoteAddr();
```

**Description:**  
`getRemoteAddr()` returns the immediate TCP connection's IP address. When the API runs behind a reverse proxy (nginx, AWS ALB, Cloudflare — all typical for production), every request appears to originate from the proxy's IP.

Two consequences:
1. **All users share one rate-limit bucket.** A single legitimate user who hits `/auth/login` 5 times locks out all other users from the same proxy (i.e., everyone).
2. **Rate limiting is trivially bypassed** if the attacker controls any header the proxy forwards. If the proxy is configured to forward `X-Forwarded-For`, an attacker can spoof it; if the proxy overrides it, the rate limit is per-proxy, not per-client.

**Fix:**  
If behind a proxy, extract the real client IP from `X-Forwarded-For` (taking the left-most IP, not the right-most which can be spoofed). Use Spring's `ForwardedHeaderFilter` or configure `server.tomcat.remoteip.remote-ip-header=X-Forwarded-For` in `application.properties`.

---

### M3 — GET /snippets/mine Returns All Snippets Without Pagination

**File:** `SnippetController.java:39-43`, `SnippetRepository.java:10`

```java
public List<Snippet> mine(@AuthenticationPrincipal String username){
    ...
    return snippets.findByOwnerIdOrderByUpdatedAtDesc(me.getId()); // no Pageable
}
```

**Description:**  
There is no limit on how many snippets a user can create (no per-user quota). A user who creates 10,000 snippets will cause `/snippets/mine` to load all 10,000 rows plus their owners (N+1 queries, see L6) and serialize the entire result into one JSON response. This will cause JVM memory pressure and extreme response latency.

Combined with H3 (no code size limit), a single response could be gigabytes.

**Fix:**  
Convert to a paginated endpoint: change the return type to `Page<Snippet>` and add a `Pageable` parameter. Also enforce a per-user snippet limit in `POST /snippets/save`.

---

### M4 — No Request Body Size Limit Configured

**File:** `application.properties` (absent)

**Description:**  
Spring Boot's default max request size via the embedded Tomcat is 2MB for multipart, but JSON bodies via `@RequestBody` are not subject to this by default — they are limited only by the Tomcat connector's `maxPostSize` (default: 2MB). However, the effective limit depends on configuration and may be higher or unlimited depending on deployment.

Given that `code` (H3) and `errorMessage` have no `@Size` constraints, a request with a very large JSON body will be fully buffered into memory before any validation runs.

**Fix:**  
Explicitly set:
```properties
server.tomcat.max-http-form-post-size=2MB
server.tomcat.max-swallow-size=2MB
```
And add `@Size` to unbounded fields as noted in H3.

---

### M5 — Sequential Integer IDs Enable Snippet Enumeration

**File:** `V1__init.sql:2`, `Snippet.java:13-14`

```sql
id BIGSERIAL PRIMARY KEY
```

**Description:**  
Snippet IDs are auto-incrementing integers starting from 1. An attacker can iterate `GET /snippets/1`, `/snippets/2`, `/snippets/3`, ... to:
1. Determine exactly how many snippets exist (find the highest ID that returns non-404).
2. Identify all public snippets.
3. Confirm the existence of private snippets (they return 404, but the pattern of hits vs. misses reveals "someone has a snippet here").
4. Supply those IDs to `POST /runs` (C2) to log runs against private snippets.

**Fix:**  
Use UUIDs as public-facing identifiers: `@GeneratedValue(strategy = GenerationType.UUID)`. This eliminates enumeration without changing the schema significantly.

---

### M6 — JWT Tokens Are Not Revocable

**File:** `JwtUtil.java:33-39`, `JwtFilter.java:38`

**Description:**  
Tokens are validated purely cryptographically — there is no database lookup, no token blacklist, and no session store. Once issued, a token is valid until it expires (8 hours) regardless of:
- Password change
- Account deletion (no endpoint exists today, but could be added)
- Explicit logout
- Token compromise

An attacker who steals a token has 8 hours of unrestricted access with no way for the victim or admin to invalidate it.

**Fix:**  
For low-friction revocation, maintain a Redis or DB-backed token blacklist keyed by `jti` (JWT ID) claim. On logout or password change, add the token's `jti` to the blacklist. Check the blacklist in `JwtFilter` after signature verification. Alternatively, use short-lived access tokens (15 minutes) plus a revocable refresh token.

---

### M7 — No Validation on durationMs and instructionsExecuted

**File:** `LogRunRequest.java:8-11`

```java
Integer durationMs,
Integer instructionsExecuted,
```

**Description:**  
Both fields are optional `Integer` with no `@Min`, `@Max`, or `@PositiveOrZero` constraints. An attacker can submit:
- Negative values (`durationMs: -9999999`)
- `Integer.MAX_VALUE` (2,147,483,647 ms ≈ 24 days of run time)
- Zero

These corrupt analytics, leaderboards, and any stats derived from run history. If duration/instruction data is ever aggregated or displayed, adversarially crafted values could cause integer overflow or display garbage.

**Fix:**  
```java
@PositiveOrZero Integer durationMs,
@PositiveOrZero @Max(86400000) Integer durationMs,  // max 24h
@PositiveOrZero Integer instructionsExecuted,
```

---

### M8 — .orElseThrow() Without Argument Causes 500 Instead of 401 in RunController

**File:** `RunController.java:46, 52`

```java
User me = users.findByUsername(username).orElseThrow();  // throws NoSuchElementException
```

**Description:**  
If `username` is somehow null (e.g., via a future security misconfiguration), `findByUsername(null)` returns an empty Optional and `.orElseThrow()` throws `NoSuchElementException`. The `GlobalExceptionHandler` catches it as a generic `Exception` and returns `500 "An unexpected error occurred"` — not a 401. This masks an authentication failure as a server error and obscures the real problem in logs.

Compare to `AuthController` and `SnippetController` which properly use `.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED))`.

**Fix:**  
Replace all bare `.orElseThrow()` calls in `RunController` with `.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED))`.

---

## LOW

---

### L1 — Development Localhost Origins Present in Production CORS Config

**File:** `SecurityConfig.java:60-61`

```java
config.setAllowedOrigins(List.of(
    "http://localhost:5173",
    "http://localhost:5174",
    ...
));
config.setAllowCredentials(true);
```

**Description:**  
`allowCredentials(true)` means the browser will include cookies and authorization headers for cross-origin requests from these origins. Including `localhost` origins in production means any page served from the victim's own machine at those ports can make credentialed requests to the production API. This is a low-probability but real attack vector (attacker serves malicious page on localhost via a different exploit; developer's local tooling is compromised).

**Fix:**  
Move allowed origins to environment-specific configuration. In production, only allow `https://webmarsimulator.com` and `https://www.webmarsimulator.com`.

---

### L2 — Missing Content-Security-Policy and Referrer-Policy Headers

**File:** `SecurityConfig.java` (absent)

**Description:**  
Spring Security automatically adds `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, and `Strict-Transport-Security` (for HTTPS). However, no `Content-Security-Policy` or `Referrer-Policy` headers are configured. For a pure REST API this is lower risk than for an HTML-serving app, but CSP can prevent certain attack classes if the API ever gains a web UI.

**Fix:**  
```java
http.headers(headers -> headers
    .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'"))
    .referrerPolicy(rp -> rp.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
);
```

---

### L3 — V1 Migration Has Wrong Column Name (username Instead of title) in snippets Table

**File:** `V1__init.sql:10`

```sql
CREATE TABLE IF NOT EXISTS snippets (
    id  BIGSERIAL PRIMARY KEY,
    username VARCHAR(32) UNIQUE NOT NULL,  -- should be "title"?
    code     TEXT,
    created_at TIMESTAMP
);
```

**Description:**  
The `snippets` table was created with a `username VARCHAR(32) UNIQUE NOT NULL` column that is never referenced by any entity or subsequent migration. The `Snippet` entity has a `title` field but no migration ever creates a `title` column. This means:

1. The `username` column on `snippets` is an orphaned, never-used column with a `UNIQUE NOT NULL` constraint — which may actually prevent inserting any snippet if Hibernate expects `title` but the column doesn't exist.
2. If `spring.jpa.hibernate.ddl-auto=validate` is active and `title` doesn't exist in the DB, the app would fail to start.
3. The developer may have worked around this by running with `ddl-auto=update` locally at some point, which auto-created `title` but left `username` in place.

**Impact:**  
Schema inconsistency between migrations and entities. The `UNIQUE` constraint on `snippets.username` could silently block data insertion depending on the actual deployed schema.

**Fix:**  
Add a `V4__fix_snippets_schema.sql` migration that:
1. Renames `username` to `title` (or drops it if truly orphaned)
2. Removes the UNIQUE constraint (titles are not unique)
3. Adds `title VARCHAR(255)` if it doesn't already exist

---

### L4 — User.id Exposed in Snippet and Run API Responses

**File:** `Snippet.java:23`, `User.java` (no `@JsonIgnore` on `id`)

**Description:**  
When a `Snippet` is returned, the `owner` object is serialized with `@JsonIgnoreProperties({"password", "createdAt", ...})`. The `id` field of `User` is not in this exclusion list and is therefore included in every snippet response. Combined with M5 (sequential IDs), this leaks the internal numeric IDs of all snippet owners.

**Fix:**  
Add `"id"` to the `@JsonIgnoreProperties` list on `Snippet.owner`, or create a `SnippetResponse` DTO that projects only `owner.username`.

---

### L5 — No Explicit JWT Algorithm Pinning

**File:** `JwtUtil.java:17-23, 25-31`

```java
.signWith(key)        // algorithm inferred from key size
```

**Description:**  
JJWT 0.12.x picks the HMAC algorithm based on key length (HS256 for 32-byte, HS384 for 48-byte, HS512 for 64+ byte). This is safe in practice because `verifyWith(key)` will only accept tokens whose algorithm matches the key type. However, the algorithm is not explicitly declared, making the contract implicit and brittle if the key length changes.

**Fix:**  
```java
.signWith(key, Jwts.SIG.HS256)
```
Pin to a specific algorithm to make the intent explicit.

---

### L6 — N+1 Query Problem on GET /snippets/public

**File:** `Snippet.java:21`, `SnippetController.java:46-50`

```java
@ManyToOne(fetch = FetchType.LAZY)
private User owner;
```

**Description:**  
`Snippet.owner` is lazily loaded. When Jackson serializes a page of 100 snippets from `/snippets/public`, each call to `snippet.getOwner()` triggers a separate `SELECT` on the `users` table. A request for 100 snippets with 100 distinct owners generates 101 database queries (1 for snippets + 100 for owners).

**Fix:**  
Use a JPQL `JOIN FETCH` query in `SnippetRepository`, or annotate the repository method with `@EntityGraph(attributePaths = "owner")`.

---

### L7 — Debug Output Left in Production Code

**File:** `SecurityConfig.java:30`

```java
System.out.println("SecurityConfig loaded");
```

**Description:**  
A `System.out.println` debug statement was left in the `SecurityConfig` constructor. In production this pollutes logs and could interfere with structured logging systems. It's also a mild signal to attackers that the code is not fully hardened.

**Fix:**  
Remove the `System.out.println` call. Use `log.debug(...)` via SLF4J if startup logging is desired.

---

### L8 — Duplicate @EnableWebSecurity Import

**File:** `SecurityConfig.java:7, 17`

```java
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
// ... (line 17)
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
```

**Description:**  
`@EnableWebSecurity` is imported twice. This is harmless (Java allows duplicate imports) but indicates the file was edited carelessly and is a code quality signal.

**Fix:**  
Remove the duplicate import.

---

### L9 — Flyway Configuration Duplicated Between Properties and Java Bean

**File:** `application.properties:8-13`, `FlywayConfig.java`

**Description:**  
Flyway is configured in both `application.properties` (Spring Boot auto-configuration) and via an explicit `FlywayConfig.java` `@Bean`. Spring Boot's auto-configured Flyway and the manual `FlywayConfig` bean will both attempt to migrate on startup. Flyway's `flyway_schema_history` table prevents double-applying migrations, but having two Flyway instances is redundant and confusing. If the manual bean is removed, properties-based config is sufficient.

Additionally, `baseline-on-migrate=true` in production means if the `flyway_schema_history` table is ever accidentally dropped, Flyway will silently baseline at version 1 and skip all migrations, leaving the schema in an unknown state.

**Fix:**  
Remove `FlywayConfig.java` entirely and rely on Spring Boot's auto-configuration via `application.properties`. Evaluate whether `baseline-on-migrate` should be `false` in production.

---

### L10 — No Snippet Creation Limit Per User

**File:** `SnippetController.java:52-61`

**Description:**  
Any authenticated user can call `POST /snippets/save` an unlimited number of times. There is no per-user quota. Combined with no code size limit (H3), no rate limiting on this endpoint (H2), and unbounded `/snippets/mine` (M3), a single user can fill the database and cause OOM when anyone calls `/snippets/mine`.

**Fix:**  
Enforce a per-user snippet limit (e.g., 500) and return 429 or 403 when exceeded.

---

### L11 — Snippet Returned Directly from Entity (No DTO Projection)

**File:** `SnippetController.java:28-35, 52-61`, `Snippet.java`

**Description:**  
Controllers return `Snippet` entity objects directly rather than DTO projections. This creates tight coupling between the database schema and the API contract, and means any field added to `Snippet` (e.g., internal flags, audit fields) is automatically exposed to clients unless explicitly annotated with `@JsonIgnore`. It also makes it harder to evolve the schema independently of the API.

This is an architectural concern rather than an immediate vulnerability, but it is the root cause of L4 (user ID leakage) and similar issues.

**Fix:**  
Introduce a `SnippetResponse` DTO that explicitly projects only the intended fields: `id`, `title`, `code`, `visibility`, `createdAt`, `updatedAt`, and `owner.username`. Return that DTO from all snippet endpoints.

---

## Summary Table

| ID | Vulnerability | File | Severity |
|----|--------------|------|----------|
| C1 | Credential committed in .env | `.env:1` | Critical |
| C2 | Any user can log runs for any snippet | `RunController.java:31` | Critical |
| H1 | GET /snippets/** permitAll too broad | `SecurityConfig.java:50` | High |
| H2 | No rate limiting on authenticated endpoints | `RateLimitFilter.java` | High |
| H3 | Unbounded code/errorMessage fields | `SaveSnippetRequest.java:12`, `LogRunRequest.java:12` | High |
| H4 | Username enumeration via login timing | `AuthController.java:39` | High |
| M1 | Memory leak in RateLimitFilter | `RateLimitFilter.java:20` | Medium |
| M2 | Rate limit broken behind reverse proxy | `RateLimitFilter.java:45` | Medium |
| M3 | /snippets/mine unpaginated | `SnippetController.java:39` | Medium |
| M4 | No request body size limit | `application.properties` | Medium |
| M5 | Sequential IDs allow snippet enumeration | `V1__init.sql:2` | Medium |
| M6 | JWTs are not revocable | `JwtUtil.java`, `JwtFilter.java` | Medium |
| M7 | No validation on durationMs/instructionsExecuted | `LogRunRequest.java:8` | Medium |
| M8 | .orElseThrow() bare causes 500 not 401 | `RunController.java:46,52` | Medium |
| L1 | Localhost CORS origins in production | `SecurityConfig.java:60` | Low |
| L2 | Missing CSP and Referrer-Policy headers | `SecurityConfig.java` | Low |
| L3 | V1 migration wrong column name in snippets | `V1__init.sql:10` | Low |
| L4 | user.id leaked in Snippet responses | `Snippet.java:23` | Low |
| L5 | No explicit JWT algorithm pinning | `JwtUtil.java:19` | Low |
| L6 | N+1 query on /snippets/public | `Snippet.java:21` | Low |
| L7 | System.out.println in production | `SecurityConfig.java:30` | Low |
| L8 | Duplicate @EnableWebSecurity import | `SecurityConfig.java:7,17` | Low |
| L9 | Flyway config duplicated + risky baseline-on-migrate | `FlywayConfig.java`, `application.properties` | Low |
| L10 | No snippet creation limit per user | `SnippetController.java:52` | Low |
| L11 | Entities returned directly without DTO projection | `SnippetController.java` | Low |

---

## Questions and Areas to Investigate Further

The following questions arose during the audit. Answers would either confirm or dismiss additional attack vectors:

1. **Is `.env` tracked by git?** Run `git ls-files .env` in the `webmars-api` directory. If it is, the DB password is permanently in git history even after deletion and must be rotated.

2. **What is the actual deployed `JWT_SECRET`?** The `.env` has a placeholder that would crash the app. What value is injected in production? Is it a proper random 32+ byte secret, or something derivable (like a hostname, project name, or another weak value)?

3. **Is the API behind a reverse proxy in production?** If yes, M2 (rate limit uses RemoteAddr) is actively broken. All users share one rate-limit bucket, and an attacker can trigger account lockout for everyone by burning through 5 login attempts from the proxy IP.

4. **What is the actual database schema?** The V1 migration creates `snippets.username` but the entity has `title`. If the app is running with `ddl-auto=validate`, something doesn't add up — there may be manual schema patches not reflected in migrations. Understanding the actual DB state is needed to fully assess L3.

5. **Is there a frontend that stores the JWT?** If the token is stored in `localStorage`, it's vulnerable to XSS on the frontend. If stored in a cookie, make sure it's `HttpOnly` + `SameSite=Strict`. The backend doesn't control this, but it changes the risk profile of M6 (non-revocable tokens).

6. **Are snippet IDs ever shared publicly?** If the app shares snippet URLs like `/snippets/42`, users can infer the approximate snippet count and iterate. If IDs were UUIDs, this surface disappears.

7. **Is there admin functionality planned?** There is no role system at all (all authenticated users have empty authorities). If an admin role is added later without a proper RBAC refactor, privilege escalation is likely.

8. **Does the frontend send `durationMs` and `instructionsExecuted` from client-side execution timing?** If yes, these values are entirely client-controlled and can be trivially faked (M7). If these are used for any leaderboard or performance analytics, they're gameable with zero effort.

9. **Are run records ever deleted or archived?** There is no `DELETE /runs` endpoint. Runs accumulate forever. With no rate limiting and no creation cap, the `runs` table is unbounded and can be filled by any authenticated user.

10. **What is `durationMs` vs `startedAt`?** `startedAt` is set by the server (`@PrePersist`), but `durationMs` is client-reported. The two are inconsistent by design, but it means a client can claim a 0ms run that "started" at the server's current time — there's no way to validate that the snippet was actually executed.
