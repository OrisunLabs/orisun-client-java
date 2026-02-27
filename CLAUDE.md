# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is the Java client for the Orisun Event Store - a gRPC-based event sourcing system. The client provides a type-safe, intuitive interface for interacting with the Orisun server, supporting event storage, retrieval, streaming subscriptions, and administrative operations.

The project includes two main clients:
- **OrisunClient** - For event store operations (save, read, subscribe to events)
- **AdminClient** - For user management and administrative operations

## Build and Development Commands

### Building the Project
```bash
./gradlew build              # Build and test the project
./gradlew jar                # Build JAR only
./gradlew shadowJar          # Build fat JAR with dependencies
./gradlew clean              # Clean build artifacts
```

### Testing
```bash
./gradlew test               # Run all tests
./gradlew test --tests "*ClassName"  # Run specific test class
```

### Proto File Management
The project uses proto definitions from a git submodule in `protos/`:
```bash
git submodule update --init --recursive    # Initialize submodule
git submodule update --remote protos       # Update proto files
./gradlew build                            # Regenerate Java classes from proto
```

### Publishing
```bash
./gradlew publishToMavenLocal              # Publish to local Maven cache
./gradlew publish                          # Publish to remote repository
```

## Architecture

### Client Structure
The `OrisunClient` class uses a builder pattern (`OrisunClient.newBuilder()`) with the following key components:

**Core Client (`OrisunClient.java`)**
- Main entry point for all operations
- Implements both synchronous and asynchronous methods
- Uses gRPC blocking and non-blocking stubs
- Implements `AutoCloseable` for proper resource cleanup
- Supports single-server, multi-server, DNS-based, and static-based load balancing

**Authentication Flow**
- `TokenCache` - Manages authentication tokens, extracting tokens from response headers (`x-auth-token`) and caching them for reuse
- Falls back to Basic Authentication when no token is cached
- Authentication is applied via gRPC interceptors that inject metadata into requests

**Request Validation (`RequestValidator.java`)**
- Validates all requests before sending to server
- Checks for required fields, UUID formats, and logical constraints
- Throws `OrisunException` with detailed context for validation failures

**Error Handling**
- `OrisunException` - Base exception with context map for debugging
- `OptimisticConcurrencyException` - Specific exception for version conflicts (expected vs actual version)

**Event Subscription (`EventSubscription.java`)**
- Manages streaming subscriptions to events
- Uses `EventHandler` interface for callbacks (onEvent, onError, onCompleted)
- Implements `AutoCloseable` for cleanup
- Handles authentication for streaming connections

**Logging (`Logger.java`, `DefaultLogger.java`)**
- Pluggable logging interface
- Default logger with configurable log levels (INFO, WARN, ERROR, DEBUG)
- Can be customized via builder pattern

### Admin Client Structure

The `AdminClient` class mirrors the architecture of `OrisunClient` but focuses on administrative operations:

**Core Admin Client (`AdminClient.java`)**
- Entry point for user management and admin operations
- Implements synchronous methods for all admin RPCs
- Uses gRPC blocking stubs for all operations
- Implements `AutoCloseable` for proper resource cleanup
- Supports the same connection strategies as `OrisunClient`

**Admin Request Validation (`AdminRequestValidator.java`)**
- Validates admin-specific requests before sending to server
- Checks for password requirements (minimum 8 characters)
- Validates UUID formats for user IDs
- Ensures new passwords differ from current passwords
- Throws `OrisunException` with detailed context for validation failures

**Supported Admin Operations:**
- **User Management**: `createUser`, `deleteUser`, `changePassword`, `listUsers`
- **Authentication**: `validateCredentials`
- **Statistics**: `getUserCount`, `getEventCount`
- **Index Management**: `createIndex`, `dropIndex`

Both clients share the same authentication flow, token caching mechanism, and connection management patterns.

### gRPC Integration
The client uses generated gRPC code from Protocol Buffer definitions:
- Proto files are in `protos/` directory (submodule)
- Generated Java classes are in `build/generated/source/proto/`
- Uses grpc-netty-shaded for networking (includes Netty in the JAR)

### Connection Management
- Supports multiple connection strategies:
  - Single server: `withServer("localhost", 5005)`
  - Multiple servers: `withServer(host1, port1).withServer(host2, port2)`
  - DNS-based: `withDnsTarget("dns:///example.com:5005")`
  - Static targets: `withStaticTarget("static:///host1:5005,host2:5005")`
- Configurable keep-alive settings to maintain connections
- Load balancing policies (default: round_robin)

## Testing Strategy

Tests use in-process gRPC servers with mock implementations (`OrisunClientTest.java`):
- Uses `ServerBuilder.forPort(0)` for dynamic port allocation
- `MockEventStoreService` implements the gRPC service interface
- Tests cover sync operations, async operations, subscriptions, and error cases
- All tests use ephemeral ports to avoid conflicts

## Key Design Patterns

1. **Builder Pattern** - Used extensively in `OrisunClient.Builder` for configuration
2. **Interceptor Pattern** - gRPC interceptors inject authentication and handle token extraction
3. **Observer Pattern** - `StreamObserver` for async operations and event subscriptions
4. **Validation Pattern** - `RequestValidator` performs pre-request validation
5. **Resource Management** - Both `OrisunClient` and `EventSubscription` implement `AutoCloseable`

## Important Implementation Notes

- **Token Caching**: The `TokenCache` automatically extracts tokens from response headers and reuses them for subsequent requests, reducing authentication overhead
- **Concurrency**: Uses `AtomicReference` for thread-safe token caching
- **Metadata Handling**: Authentication metadata is copied carefully through interceptor chains to avoid loss
- **Resource Cleanup**: Always use try-with-resources or explicitly call `close()` on both client and subscription objects
- **Error Context**: Exceptions include context maps with operation names, boundaries, and other debugging information

## Dependencies

- gRPC 1.75.0 (protobuf, stub, netty-shaded, testing, inprocess)
- Protobuf 4.28.2
- JUnit 5.10.1 for testing
- javax.annotation-api 1.3.2

## Common Patterns

**Creating a client:**
```java
try (OrisunClient client = OrisunClient.newBuilder()
    .withServer("localhost", 5005)
    .withBasicAuth("username", "password")
    .withTimeout(30)
    .build()) {
    // Use client
}
```

**Saving events:**
```java
Eventstore.SaveEventsRequest request = Eventstore.SaveEventsRequest.newBuilder()
    .setBoundary("boundary-name")
    .addEvents(Eventstore.EventToSave.newBuilder()
        .setEventId(UUID.randomUUID().toString())
        .setEventType("EventType")
        .setData("{\"data\":\"value\"}")
        .build())
    .build();
Eventstore.WriteResult result = client.saveEvents(request);
```

**Subscribing to events:**
```java
EventSubscription subscription = client.subscribeToEvents(request,
    new EventSubscription.EventHandler() {
        public void onEvent(Eventstore.Event event) { /* handle */ }
        public void onError(Throwable error) { /* handle */ }
        public void onCompleted() { /* handle */ }
    });
// Later: subscription.close();
```

**Creating an admin client:**
```java
try (AdminClient adminClient = AdminClient.newBuilder()
    .withServer("localhost", 5005)
    .withBasicAuth("admin", "adminpassword")
    .withTimeout(30)
    .build()) {
    // Use admin client
}
```

**Creating a user:**
```java
CreateUserRequest request = CreateUserRequest.newBuilder()
    .setName("John Doe")
    .setUsername("johndoe")
    .setPassword("securePassword123")
    .addRoles("user")
    .addRoles("admin")
    .build();
AdminUser user = adminClient.createUser(request);
```

**Listing users:**
```java
List<AdminUser> users = adminClient.listUsers();
for (AdminUser user : users) {
    System.out.println("User: " + user.getUsername());
}
```

**Validating credentials:**
```java
ValidateCredentialsRequest request = ValidateCredentialsRequest.newBuilder()
    .setUsername("johndoe")
    .setPassword("securePassword123")
    .build();
ValidateCredentialsResponse response = adminClient.validateCredentials(request);
if (response.getSuccess()) {
    System.out.println("Valid credentials for: " + response.getUser().getName());
}
```

**Getting statistics:**
```java
long userCount = adminClient.getUserCount();
long eventCount = adminClient.getEventCount("users-boundary");
```

**Creating an index:**
```java
CreateIndexRequest request = CreateIndexRequest.newBuilder()
    .setBoundary("events-boundary")
    .setName("idx_user_id")
    .addFields(IndexField.newBuilder()
        .setJsonKey("userId")
        .setValueType(ValueType.TEXT)
        .build())
    .addFields(IndexField.newBuilder()
        .setJsonKey("timestamp")
        .setValueType(ValueType.TIMESTAMPTZ)
        .build())
    .addConditions(IndexCondition.newBuilder()
        .setKey("eventType")
        .setOperator("=")
        .setValue("UserCreated")
        .build())
    .setConditionCombinator(ConditionCombinator.AND)
    .build();
adminClient.createIndex(request);
```

**Dropping an index:**
```java
DropIndexRequest request = DropIndexRequest.newBuilder()
    .setBoundary("events-boundary")
    .setName("idx_user_id")
    .build();
adminClient.dropIndex(request);
```