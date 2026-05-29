# TuneDev - Spring Boot Backend

REST and WebSocket API for a real-time guitar and bass tuner application.

## What It Does

The backend serves a React frontend that lets musicians select a tuning, pick a string, and get live pitch feedback. It maintains a catalog of standard and user-defined tunings in MySQL, persists per-user song and tuning preferences, and broadcasts pitch updates over WebSocket using STOMP. Three tuner modes are supported: browser-driven (the frontend sends detected frequencies), mock (simulated signal for development), and hardware (reads from a Python pitch-detection process running on a Raspberry Pi).

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.3 |
| Build | Gradle 8 |
| Database | MySQL 8 (H2 in tests) |
| WebSocket | STOMP over SockJS |
| JSON | Jackson |
| Testing | JUnit 5, Spring Boot Test, JaCoCo |

## How to Run Locally

**Prerequisites:** Java 21, a running MySQL instance.

**1. Configure the database**

Create a database and user, then set the connection via environment variables (or edit `application.properties`):

```bash
export DB_URL=jdbc:mysql://localhost:3306/guitar_tuner
export DB_USER=tuner_admin
export DB_PASSWORD=tuner_password
```

The application creates its own schema on first startup. No migration tool required.

**2. Start the server**

```bash
./gradlew bootRun
```

The server starts on `http://localhost:8080`. The default tuner mode is `browser`; the frontend sends detected frequencies.

**Optional: run in mock mode** (simulates pitch updates without hardware or a microphone)

```bash
./gradlew bootRun --args='--tuner.mode=mock'
```

**3. Run the tests**

```bash
./gradlew test
```

Test results and a JaCoCo coverage report are written to `build/reports/`.

---

## SOLID Principles in this Project

### Single Responsibility Principle

The codebase is organized in three layers with non-overlapping concerns: controllers handle HTTP routing and request parsing, services own business logic, and DTOs define serialization shape. A change to the API contract only touches controllers; a change to persistence logic only touches services.

Within the service layer, responsibilities are divided by domain. `TuningCatalogService` owns everything related to tuning definitions: MIDI-to-frequency math, key generation, validation, and custom tuning CRUD. `UserPreferencesService` owns user authentication and preference persistence. Neither reaches into the other's domain.

`TuningUpdateMapper` is a focused mapping component with no persistence or routing logic. Its only job is converting raw Python process output and building mock payloads into `TuningUpdate` DTOs.

### Open/Closed Principle

`TunerService` selects its operating mode from a configuration property:

```java
@Value("${tuner.mode:browser}")
private String tunerMode;
```

The three current modes (`browser`, `mock`, and `hardware`) each have isolated startup methods. Adding a fourth mode (e.g., a network audio stream) appends a new branch and a new method without touching the existing mode implementations. The `@PostConstruct`/`@PreDestroy` lifecycle hooks that own startup and shutdown are closed to modification.

The tuning catalog is database-driven rather than hardcoded. New standard or custom tuning types are added as data rows; no service code changes.

### Liskov Substitution Principle

`CorsConfig` and `WebSocketConfig` implement Spring framework interfaces (`WebMvcConfigurer` and `WebSocketMessageBrokerConfigurer`). Both fulfill their contracts correctly; they override only the methods relevant to their purpose, leave all others as Spring's default no operations, and do not strengthen preconditions or weaken postconditions. Spring's application context substitutes them wherever those interfaces are expected.

All five controllers depend on their respective service types via constructor injection. The controller's behavior is determined entirely by the declared method contract; a Mockito mock or a fake in-memory implementation can be substituted in tests without changing the controller. The controller cannot observe which concrete object was injected.

> Note: this codebase has no class inheritance hierarchies and no explicit service interfaces; LSP is enforced through Spring's injection contract and interface implementations rather than through a custom type hierarchy.

### Interface Segregation Principle

`CorsConfig` implements `WebMvcConfigurer`, an interface with roughly fifteen methods covering interceptors, formatters, message converters, resource handlers, and more. `CorsConfig` overrides only `addCorsMappings()` and leaves everything else as Spring's default no operation. It depends only on what it actually uses.

Each service exposes a narrow, domain-specific surface. `UserPreferencesService` has four methods: `validateLogin`, `register`, `getPreferences`, `savePreferences`. `TuningCatalogService` exposes tuning operations only. `TunerService` has no public API at all. It operates purely through Spring's `@PostConstruct` and `@PreDestroy` lifecycle hooks. Each controller injects exactly the service it needs.

Both `AudioInputController` and `TunerService` inject `SimpMessagingTemplate`, Spring's focused abstraction for broadcasting messages to WebSocket topics. Neither depends on a broader session-management or handshake interface.

### Dependency Inversion Principle

All controllers and services use constructor injection, and all critical dependencies are interface types or Spring abstractions, not concrete framework classes or vendor drivers.

```java
// AuthController - depends on service type, not on JdbcTemplate or MySQL
public AuthController(UserPreferencesService userPreferencesService) { ... }

// TunerService - depends on messaging and mapping abstractions
public TunerService(SimpMessagingTemplate messagingTemplate,
                    TuningUpdateMapper tuningUpdateMapper) { ... }

// UserPreferencesService - depends on Spring's JDBC abstraction
public UserPreferencesService(JdbcTemplate jdbcTemplate) { ... }
```

`UserPreferencesService` and `TuningCatalogService` depend on `JdbcTemplate` rather than on `java.sql.Connection` or any MySQL-specific class. The test suite swaps in H2 by changing only `application.properties`: no service code changes. `TuningUpdateMapper` depends on Jackson's `ObjectMapper` abstraction, not on raw string parsing or reflection. No controller constructs a service with `new`.

---

## Refactoring Opportunities

Given a sprint 5 (One more sprint), I would refacotr the following:

1. **Introduce service interfaces** : `UserPreferencesService` and `TuningCatalogService` are concrete classes. Defining `interface UserPreferencesPort` and `interface TuningCatalogPort` would make DIP fully structural: controllers would declare interface types, and a test double could be injected without a mocking framework.

2. **`TunerService` strategy pattern** (OCP) : the current if/else mode dispatch is the obvious place to apply the strategy pattern. A `TunerMode` interface with `BrowserMode`, `MockMode`, and `HardwareMode` implementations would make adding a new mode purely additive : a new class rather than editorial (modifying `TunerService` internals).

3. **Split `UserPreferencesService`** (SRP) : authentication logic (`validateLogin`, `register`) and preference persistence (`getPreferences`, `savePreferences`) are co-located because they share a JDBC dependency, but they have different reasons to change. An `AuthService` + `PreferencesService` split would make each class's responsibility unambiguous.

4. **`FavoriteSongPreference` optional artist** (ISP) : the class uses overloaded constructors to handle the absent-artist case. Converting to a Java record with `Optional<String> artist` or adding a static factory method would give callers a single, consistent entry point and remove the implicit knowledge of which constructor to call.

---

## Known Limitations

- **Plain-text passwords** : credentials are stored and compared as plain strings. Acceptable for a local dev/demo environment; would require BCrypt and Spring Security before any real deployment.
- **No authentication tokens** : sessions are not tracked server-side; login is a one-shot HTTP call. The frontend manages auth state in memory.
- **Single-process deployment** : hardware mode spawns a Python subprocess directly from the JVM. This only works when the backend runs on the same machine as the tuner hardware (Raspberry Pi).
- **No rate limiting or input sanitization beyond basic trimming** : the API is designed for personal use on a trusted network.