# Orisun Java Client

A Java client for the Orisun event store, providing a simple and intuitive interface for interacting with the Orisun gRPC service.

## Features

- **gRPC-based**: Built on gRPC for high-performance communication
- **Type-safe**: Generated from Protocol Buffer definitions
- **Easy to use**: Simple builder pattern for client configuration
- **Authentication**: Support for basic authentication with token caching
- **Dual Clients**: Includes both OrisunClient (event operations) and AdminClient (user management)
- **Load Balancing**: Support for single-server, multi-server, DNS-based, and static load balancing

## Installation

### Option 1: GitHub Packages (Recommended)

The client is published to GitHub Packages. To use it, you'll need to authenticate with your GitHub credentials.

**Maven**

Add the GitHub Packages repository and dependency to your `pom.xml`:

```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/OrisunLabs/orisun-client-java</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.orisunlabs</groupId>
        <artifactId>orisun-java-client</artifactId>
        <version>0.0.1</version>
    </dependency>
</dependencies>
```

Configure authentication in your `~/.m2/settings.xml`:

```xml
<server>
    <id>github</id>
    <username>YOUR_GITHUB_USERNAME</username>
    <password>YOUR_GITHUB_TOKEN</password>
</server>
```

**Gradle**

Add to your `build.gradle`:

```groovy
repositories {
    maven {
        url = 'https://maven.pkg.github.com/OrisunLabs/orisun-client-java'
        credentials {
            username = project.findProperty('githubUsername') ?: System.getenv('GITHUB_USERNAME')
            password = project.findProperty('githubToken') ?: System.getenv('GITHUB_TOKEN')
        }
    }
}

dependencies {
    implementation 'com.orisunlabs:orisun-java-client:0.0.1'
}
```

Add credentials to `~/.gradle/gradle.properties`:

```properties
githubUsername=YOUR_GITHUB_USERNAME
githubToken=YOUR_GITHUB_TOKEN
```

**Note**: You can create a GitHub personal access token at https://github.com/settings/tokens

### Option 2: Download from GitHub Releases

If you prefer not to use GitHub Packages, you can download the JAR directly from [Releases](https://github.com/OrisunLabs/orisun-client-java/releases).

Download the `orisun-java-client-{version}.jar` file and use one of the methods below.

#### Maven - System Dependency

Place the JAR in your project's `lib/` directory and add to `pom.xml`:

```xml
<dependency>
    <groupId>com.orisunlabs</groupId>
    <artifactId>orisun-java-client</artifactId>
    <version>0.0.1</version>
    <scope>system</scope>
    <systemPath>${project.basedir}/lib/orisun-java-client-0.0.1.jar</systemPath>
</dependency>
```

#### Gradle - File Dependency

Place the JAR in your project's `libs/` directory and add to `build.gradle`:

```groovy
dependencies {
    implementation files('libs/orisun-java-client-0.0.1.jar')
}
```

Or use a flat directory repository:

```groovy
repositories {
    flatDir { dirs 'libs' }
}

dependencies {
    implementation 'com.orisunlabs:orisun-java-client:0.0.1'
}
```

### Building from Source

```bash
# Clone the repository
git clone https://github.com/OrisunLabs/orisun-client-java.git
cd orisun-client-java

# Initialize submodules
git submodule update --init --recursive

# Build with Gradle
./gradlew build

# The fat JAR with dependencies will be at: build/libs/orisun-client-{version}.jar
```

## Proto Files

This client uses proto definitions from a submodule. To update the proto files:

```bash
# Update the submodule
git submodule update --remote protos

# Rebuild the project
./gradlew build
```

## Quick Start

### OrisunClient - Event Store Operations

```java
import com.orisunlabs.orisun.client.OrisunClient;
import com.orisun.eventstore.Eventstore;
import java.util.UUID;

// Create a client
try (OrisunClient client = OrisunClient.newBuilder()
    .withServer("localhost", 5005)
    .withBasicAuth("admin", "changeit")
    .build()) {

    // Save events
    Eventstore.SaveEventsRequest request = Eventstore.SaveEventsRequest.newBuilder()
        .setBoundary("users")
        .addEvents(Eventstore.EventToSave.newBuilder()
            .setEventId(UUID.randomUUID().toString())
            .setEventType("UserCreated")
            .setData("{\"userId\":\"user-123\",\"email\":\"john@example.com\"}")
            .build())
        .build();

    Eventstore.WriteResult result = client.saveEvents(request);

    // Read events
    Eventstore.GetEventsResponse response = client.getEvents(
        Eventstore.GetEventsRequest.newBuilder()
            .setBoundary("users")
            .setCount(10)
            .build()
    );

    // Read latest carried-state context from one server-side snapshot
    Eventstore.GetLatestByCriteriaResponse latest = client.getLatestByCriteria(
        Eventstore.GetLatestByCriteriaRequest.newBuilder()
            .setBoundary("users")
            .addCriteria(Eventstore.Criterion.newBuilder()
                .addTags(Eventstore.Tag.newBuilder()
                    .setKey("userId")
                    .setValue("user-123")
                    .build())
                .build())
            .build()
    );

    Eventstore.Position expectedPosition = latest.getContextPosition();
}
```

### AdminClient - Boundary Management and Users

```java
import com.orisunlabs.orisun.client.AdminClient;
import com.orisun.admin.AdminOuterClass.*;

// Create an admin client
try (AdminClient adminClient = AdminClient.newBuilder()
    .withServer("localhost", 5005)
    .withBasicAuth("admin", "changeit")
    .build()) {

    BoundaryPlacementInput placement = BoundaryPlacementInput.newBuilder()
        .setBackend("postgres")
        .setNamespace("orders")
        .build();

    BoundaryInfo boundary = adminClient.createBoundary(
        CreateBoundaryRequest.newBuilder()
            .setName("orders")
            .setDescription("Order lifecycle events")
            .setPlacement(placement)
            .build()
    );

    while (boundary.getStatus()
            == BoundaryLifecycleStatus.BOUNDARY_LIFECYCLE_STATUS_PROVISIONING) {
        Thread.sleep(100);
        boundary = adminClient.getBoundary("orders");
    }

    List<BoundaryInfo> boundaries = adminClient.listBoundaries();

    // Create a user
    CreateUserRequest createRequest = CreateUserRequest.newBuilder()
        .setName("John Doe")
        .setUsername("johndoe")
        .setPassword("securePassword123")
        .addRoles("user")
        .build();

    AdminUser user = adminClient.createUser(createRequest);

    // List all users
    List<AdminUser> users = adminClient.listUsers();

    // Validate credentials
    ValidateCredentialsResponse response = adminClient.validateCredentials(
        ValidateCredentialsRequest.newBuilder()
            .setUsername("johndoe")
            .setPassword("securePassword123")
            .build()
    );
}
```

Set `existedBeforeCatalog` on `CreateBoundaryRequest` when the physical
boundary already exists. Boundary names remain unique, so repeated creates are
rejected with gRPC `ALREADY_EXISTS`.

### Advanced Configuration

Both clients support advanced configuration:

```java
OrisunClient client = OrisunClient.newBuilder()
    .withServer("localhost", 5005)
    .withBasicAuth("admin", "changeit")
    .withTimeout(30)                              // Request timeout in seconds
    .withTls(false)                               // Use TLS
    .withLoadBalancingPolicy("round_robin")      // Load balancing policy
    .withKeepAliveTime(30000)                     // Keep-alive time in ms
    .withKeepAliveTimeout(10000)                  // Keep-alive timeout in ms
    .withMaxInboundMessageSize(100 * 1024 * 1024) // Max inbound gRPC message size
    .withFlowControlWindow(1024 * 1024)           // Netty HTTP/2 flow-control window
    .withLogging(true)                            // Enable logging
    .withLogLevel(com.orisunlabs.orisun.client.DefaultLogger.LogLevel.INFO)
    .build();
```

The defaults already use a 100 MB inbound message limit and a 1 MB Netty HTTP/2 flow-control window, matching Orisun's server-side high-throughput settings. Override them only when your deployment has a measured reason.

### High-throughput writes

Use one `OrisunClient` per target and reuse it. The client caches Orisun auth tokens after the first authenticated response, so hot `saveEvents` calls should use that cached token path rather than repeatedly re-establishing authentication.

For burst imports or very high write volume, bound the number of concurrent `saveEvents` calls on that one client. In local SQLite gRPC benchmarks, roughly 512-1024 in-flight saves performed better than launching every pending write at once.

```java
ExecutorService executor = Executors.newFixedThreadPool(1024);
List<CompletableFuture<Eventstore.WriteResult>> writes = requests.stream()
    .map(request -> CompletableFuture.supplyAsync(() -> client.saveEvents(request), executor))
    .toList();

for (CompletableFuture<Eventstore.WriteResult> write : writes) {
    write.join();
}

executor.shutdown();
```

## License

MIT License - see [LICENSE](LICENSE) for details.

## Repository

https://github.com/OrisunLabs/orisun-client-java
