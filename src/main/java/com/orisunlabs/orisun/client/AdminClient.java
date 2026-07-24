package com.orisunlabs.orisun.client;

import io.grpc.*;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import com.orisun.admin.AdminGrpc;
import com.orisun.admin.AdminOuterClass.*;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Client for Orisun Admin service - handles user management and administrative operations
 */
public class AdminClient implements AutoCloseable {
    private final ManagedChannel channel;
    private final AdminGrpc.AdminBlockingStub blockingStub;
    private final AdminGrpc.AdminStub asyncStub;
    private final int defaultTimeoutSeconds;
    private final Logger logger;
    private final TokenCache tokenCache;
    private final String username;
    private final String password;

    public static class Builder {
        private static final int DEFAULT_MAX_INBOUND_MESSAGE_SIZE = 100 * 1024 * 1024;
        private static final int DEFAULT_FLOW_CONTROL_WINDOW = 1024 * 1024;

        private List<ServerAddress> servers = new ArrayList<>();
        private int timeoutSeconds = 30;
        private boolean useTls = false;
        private ManagedChannel channel;
        private String loadBalancingPolicy = "round_robin";
        private String username;
        private String password;
        private Logger logger;
        private boolean enableLogging = false;
        private DefaultLogger.LogLevel logLevel = DefaultLogger.LogLevel.INFO;
        private boolean useDnsResolver = true;

        // Keep-alive settings
        private long keepAliveTimeMs = 30000;
        private long keepAliveTimeoutMs = 10000;
        private boolean keepAlivePermitWithoutCalls = true;
        private int maxInboundMessageSize = DEFAULT_MAX_INBOUND_MESSAGE_SIZE;
        private int flowControlWindow = DEFAULT_FLOW_CONTROL_WINDOW;

        // DNS and static target settings
        private String dnsTarget;
        private String staticTarget;

        public Builder withServer(String host, int port) {
            servers.add(new ServerAddress(host, port));
            return this;
        }

        public Builder withServers(List<ServerAddress> servers) {
            this.servers.addAll(servers);
            return this;
        }

        public Builder withLoadBalancingPolicy(String policy) {
            this.loadBalancingPolicy = policy;
            return this;
        }

        public Builder withDnsTarget(String dnsTarget) {
            this.dnsTarget = dnsTarget;
            return this;
        }

        public Builder withStaticTarget(String staticTarget) {
            this.staticTarget = staticTarget;
            return this;
        }

        public Builder withDnsResolver(boolean useDns) {
            this.useDnsResolver = useDns;
            return this;
        }

        public Builder withTimeout(int seconds) {
            this.timeoutSeconds = seconds;
            return this;
        }

        public Builder withTls(boolean useTls) {
            this.useTls = useTls;
            return this;
        }

        public Builder withChannel(ManagedChannel channel) {
            this.channel = channel;
            return this;
        }

        public Builder withBasicAuth(String username, String password) {
            this.username = username;
            this.password = password;
            return this;
        }

        public Builder withLogger(Logger logger) {
            this.logger = logger;
            return this;
        }

        public Builder withLogging(boolean enableLogging) {
            this.enableLogging = enableLogging;
            return this;
        }

        public Builder withLogLevel(DefaultLogger.LogLevel level) {
            this.logLevel = level;
            return this;
        }

        public Builder withKeepAliveTime(long keepAliveTimeMs) {
            this.keepAliveTimeMs = keepAliveTimeMs;
            return this;
        }

        public Builder withKeepAliveTimeout(long keepAliveTimeoutMs) {
            this.keepAliveTimeoutMs = keepAliveTimeoutMs;
            return this;
        }

        public Builder withKeepAlivePermitWithoutCalls(boolean permitWithoutCalls) {
            this.keepAlivePermitWithoutCalls = permitWithoutCalls;
            return this;
        }

        public Builder withMaxInboundMessageSize(int bytes) {
            if (bytes <= 0) {
                throw new IllegalArgumentException("max inbound message size must be positive");
            }
            this.maxInboundMessageSize = bytes;
            return this;
        }

        public Builder withFlowControlWindow(int bytes) {
            if (bytes <= 0) {
                throw new IllegalArgumentException("flow control window must be positive");
            }
            this.flowControlWindow = bytes;
            return this;
        }

        public AdminClient build() {
            // Initialize logger
            Logger clientLogger;
            if (enableLogging && logger == null) {
                clientLogger = new DefaultLogger(logLevel);
            } else {
                clientLogger = Objects.requireNonNullElseGet(logger,
                        () -> new DefaultLogger(DefaultLogger.LogLevel.WARN));
            }

            // Initialize token cache
            TokenCache clientTokenCache = new TokenCache(clientLogger);

            if (this.channel == null) {
                ManagedChannelBuilder<?> channelBuilder;

                // Check for DNS or static targets first
                if (dnsTarget != null && !dnsTarget.trim().isEmpty()) {
                    String target = dnsTarget.startsWith("dns:///") ? dnsTarget : "dns:///" + dnsTarget;
                    channelBuilder = ManagedChannelBuilder.forTarget(target)
                            .defaultLoadBalancingPolicy(loadBalancingPolicy)
                            .maxInboundMessageSize(maxInboundMessageSize)
                            .keepAliveTime(keepAliveTimeMs, TimeUnit.MILLISECONDS)
                            .keepAliveTimeout(keepAliveTimeoutMs, TimeUnit.MILLISECONDS)
                            .keepAliveWithoutCalls(keepAlivePermitWithoutCalls);
                    applyNettyTransportDefaults(channelBuilder);

                    if (!useTls) {
                        channelBuilder.usePlaintext();
                    }

                    this.channel = channelBuilder.build();
                } else if (staticTarget != null && !staticTarget.trim().isEmpty()) {
                    String target = staticTarget.startsWith("static:///") ? staticTarget : "static:///" + staticTarget;
                    channelBuilder = ManagedChannelBuilder.forTarget(target)
                            .defaultLoadBalancingPolicy(loadBalancingPolicy)
                            .maxInboundMessageSize(maxInboundMessageSize)
                            .keepAliveTime(keepAliveTimeMs, TimeUnit.MILLISECONDS)
                            .keepAliveTimeout(keepAliveTimeoutMs, TimeUnit.MILLISECONDS)
                            .keepAliveWithoutCalls(keepAlivePermitWithoutCalls);
                    applyNettyTransportDefaults(channelBuilder);

                    if (!useTls) {
                        channelBuilder.usePlaintext();
                    }

                    this.channel = channelBuilder.build();
                } else {
                    if (servers.isEmpty()) {
                        servers.add(new ServerAddress("localhost", 5005));
                    }

                    if (servers.size() == 1) {
                        ServerAddress server = servers.getFirst();
                        channelBuilder = ManagedChannelBuilder.forAddress(server.host, server.port);
                    } else {
                        String target = createTargetString(servers);
                        channelBuilder = ManagedChannelBuilder.forTarget(target)
                                .defaultLoadBalancingPolicy(loadBalancingPolicy);
                    }

                    if (!useTls) {
                        channelBuilder.usePlaintext();
                    }

                    channelBuilder.keepAliveTime(keepAliveTimeMs, TimeUnit.MILLISECONDS)
                            .keepAliveTimeout(keepAliveTimeoutMs, TimeUnit.MILLISECONDS)
                            .keepAliveWithoutCalls(keepAlivePermitWithoutCalls)
                            .maxInboundMessageSize(maxInboundMessageSize);
                    applyNettyTransportDefaults(channelBuilder);

                    // Add authentication interceptor
                    channelBuilder.intercept(new ClientInterceptor() {
                        @Override
                        public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
                                MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {

                            return new ForwardingClientCall.SimpleForwardingClientCall<>(
                                    next.newCall(method, callOptions)) {

                                @Override
                                public void start(Listener<RespT> responseListener, Metadata headers) {
                                    final var metadata = clientTokenCache.createAuthMetadata(() ->
                                            username != null && password != null ? "Basic " + java.util.Base64.getEncoder()
                                                    .encodeToString((username + ":" + password).getBytes()) : null);

                                    metadata.keys().forEach(key -> {
                                        for (String value : Objects.requireNonNull(metadata
                                                .getAll(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER)))) {
                                            headers.put(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER), value);
                                        }
                                    });

                                    super.start(new Listener<>() {
                                        @Override
                                        public void onHeaders(Metadata headers) {
                                            clientTokenCache.extractAndCacheToken(headers);
                                            responseListener.onHeaders(headers);
                                        }

                                        @Override
                                        public void onMessage(RespT message) {
                                            responseListener.onMessage(message);
                                        }

                                        @Override
                                        public void onClose(Status status, Metadata trailers) {
                                            responseListener.onClose(status, trailers);
                                        }

                                        @Override
                                        public void onReady() {
                                            responseListener.onReady();
                                        }
                                    }, headers);
                                }
                            };
                        }
                    });

                    this.channel = channelBuilder.build();
                }
            }

            return new AdminClient(this.channel, timeoutSeconds, clientLogger, clientTokenCache, username, password);
        }

        private String createTargetString(List<ServerAddress> servers) {
            StringBuilder sb = new StringBuilder(useDnsResolver ? "dns:///" : "static:///");
            boolean first = true;
            for (ServerAddress server : servers) {
                if (!first) {
                    sb.append(",");
                }
                sb.append(server.host).append(":").append(server.port);
                first = false;
            }
            return sb.toString();
        }

        private void applyNettyTransportDefaults(ManagedChannelBuilder<?> channelBuilder) {
            if (channelBuilder instanceof NettyChannelBuilder) {
                ((NettyChannelBuilder) channelBuilder).flowControlWindow(flowControlWindow);
            }
        }
    }

    public static class ServerAddress {
        private final String host;
        private final int port;

        public ServerAddress(String host, int port) {
            this.host = host;
            this.port = port;
        }

        public String getHost() {
            return host;
        }

        public int getPort() {
            return port;
        }
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    private AdminClient(ManagedChannel channel, int timeoutSeconds, Logger logger, TokenCache tokenCache,
                       String username, String password) {
        this.channel = channel;
        this.defaultTimeoutSeconds = timeoutSeconds;
        this.logger = logger;
        this.tokenCache = tokenCache;
        this.username = username;
        this.password = password;
        this.blockingStub = AdminGrpc.newBlockingStub(channel);
        this.asyncStub = AdminGrpc.newStub(channel);

        this.logger.info("AdminClient initialized with timeout: {} seconds", timeoutSeconds);
    }

    // Boundary Management Operations

    /**
     * Record a catalog definition and idempotently provision its physical storage.
     *
     * @param request boundary definition and durable placement
     * @return the catalog entry, initially in provisioning state
     * @throws OrisunException when validation or the RPC fails
     */
    public BoundaryInfo createBoundary(CreateBoundaryRequest request) throws OrisunException {
        AdminRequestValidator.validateCreateBoundaryRequest(request);

        try {
            CreateBoundaryResponse response = blockingStub
                    .withDeadlineAfter(defaultTimeoutSeconds, TimeUnit.SECONDS)
                    .createBoundary(request);
            return response.getBoundary();
        } catch (StatusRuntimeException e) {
            throw handleException(e, "createBoundary");
        }
    }

    /**
     * List the event-backed boundary catalog.
     *
     * @param request empty list request
     * @return all cataloged boundaries
     * @throws OrisunException when validation or the RPC fails
     */
    public List<BoundaryInfo> listBoundaries(ListBoundariesRequest request) throws OrisunException {
        AdminRequestValidator.validateListBoundariesRequest(request);

        try {
            ListBoundariesResponse response = blockingStub
                    .withDeadlineAfter(defaultTimeoutSeconds, TimeUnit.SECONDS)
                    .listBoundaries(request);
            return response.getBoundariesList();
        } catch (StatusRuntimeException e) {
            throw handleException(e, "listBoundaries");
        }
    }

    /**
     * List the event-backed boundary catalog.
     *
     * @return all cataloged boundaries
     * @throws OrisunException when the RPC fails
     */
    public List<BoundaryInfo> listBoundaries() throws OrisunException {
        return listBoundaries(ListBoundariesRequest.newBuilder().build());
    }

    /**
     * Get one boundary from the event-backed catalog.
     *
     * @param request boundary lookup request
     * @return the cataloged boundary
     * @throws OrisunException when validation or the RPC fails
     */
    public BoundaryInfo getBoundary(GetBoundaryRequest request) throws OrisunException {
        AdminRequestValidator.validateGetBoundaryRequest(request);

        try {
            GetBoundaryResponse response = blockingStub
                    .withDeadlineAfter(defaultTimeoutSeconds, TimeUnit.SECONDS)
                    .getBoundary(request);
            return response.getBoundary();
        } catch (StatusRuntimeException e) {
            throw handleException(e, "getBoundary");
        }
    }

    /**
     * Get one boundary from the event-backed catalog.
     *
     * @param name boundary name
     * @return the cataloged boundary
     * @throws OrisunException when validation or the RPC fails
     */
    public BoundaryInfo getBoundary(String name) throws OrisunException {
        if (name == null) {
            throw new OrisunException("Boundary name is required")
                    .addContext("operation", "getBoundary");
        }
        return getBoundary(GetBoundaryRequest.newBuilder().setName(name).build());
    }

    // User Management Operations

    /**
     * Create a new user
     */
    public AdminUser createUser(CreateUserRequest request) throws OrisunException {
        AdminRequestValidator.validateCreateUserRequest(request);

        logger.debug("Creating user with username: {}", request.getUsername());

        try {
            CreateUserResponse response = blockingStub
                    .withDeadlineAfter(defaultTimeoutSeconds, TimeUnit.SECONDS)
                    .createUser(request);

            logger.info("Successfully created user with ID: {}", response.getUser().getUserId());
            return response.getUser();

        } catch (StatusRuntimeException e) {
            throw handleException(e, "createUser");
        }
    }

    /**
     * Delete a user by ID
     */
    public boolean deleteUser(DeleteUserRequest request) throws OrisunException {
        AdminRequestValidator.validateDeleteUserRequest(request);

        logger.debug("Deleting user: {}", request.getUserId());

        try {
            DeleteUserResponse response = blockingStub
                    .withDeadlineAfter(defaultTimeoutSeconds, TimeUnit.SECONDS)
                    .deleteUser(request);

            logger.info("Successfully deleted user: {}", request.getUserId());
            return response.getSuccess();

        } catch (StatusRuntimeException e) {
            throw handleException(e, "deleteUser");
        }
    }

    /**
     * Change a user's password
     */
    public boolean changePassword(ChangePasswordRequest request) throws OrisunException {
        AdminRequestValidator.validateChangePasswordRequest(request);

        logger.debug("Changing password for user: {}", request.getUserId());

        try {
            ChangePasswordResponse response = blockingStub
                    .withDeadlineAfter(defaultTimeoutSeconds, TimeUnit.SECONDS)
                    .changePassword(request);

            logger.info("Successfully changed password for user: {}", request.getUserId());
            return response.getSuccess();

        } catch (StatusRuntimeException e) {
            throw handleException(e, "changePassword");
        }
    }

    /**
     * List all users
     */
    public List<AdminUser> listUsers(ListUsersRequest request) throws OrisunException {
        AdminRequestValidator.validateListUsersRequest(request);

        logger.debug("Listing all users");

        try {
            ListUsersResponse response = blockingStub
                    .withDeadlineAfter(defaultTimeoutSeconds, TimeUnit.SECONDS)
                    .listUsers(request);

            logger.debug("Successfully retrieved {} users", response.getUsersCount());
            return response.getUsersList();

        } catch (StatusRuntimeException e) {
            throw handleException(e, "listUsers");
        }
    }

    /**
     * List all users (convenience method with empty request)
     */
    public List<AdminUser> listUsers() throws OrisunException {
        return listUsers(ListUsersRequest.newBuilder().build());
    }

    // Authentication Operations

    /**
     * Validate user credentials
     */
    public ValidateCredentialsResponse validateCredentials(ValidateCredentialsRequest request) throws OrisunException {
        AdminRequestValidator.validateValidateCredentialsRequest(request);

        logger.debug("Validating credentials for username: {}", request.getUsername());

        try {
            ValidateCredentialsResponse response = blockingStub
                    .withDeadlineAfter(defaultTimeoutSeconds, TimeUnit.SECONDS)
                    .validateCredentials(request);

            if (response.getSuccess()) {
                logger.info("Successfully validated credentials for user: {}", request.getUsername());
            } else {
                logger.warn("Failed to validate credentials for user: {}", request.getUsername());
            }
            return response;

        } catch (StatusRuntimeException e) {
            throw handleException(e, "validateCredentials");
        }
    }

    // Statistics Operations

    /**
     * Get total user count
     */
    public long getUserCount(GetUserCountRequest request) throws OrisunException {
        AdminRequestValidator.validateGetUserCountRequest(request);

        logger.debug("Getting user count");

        try {
            GetUserCountResponse response = blockingStub
                    .withDeadlineAfter(defaultTimeoutSeconds, TimeUnit.SECONDS)
                    .getUserCount(request);

            logger.debug("User count: {}", response.getCount());
            return response.getCount();

        } catch (StatusRuntimeException e) {
            throw handleException(e, "getUserCount");
        }
    }

    /**
     * Get total user count (convenience method with empty request)
     */
    public long getUserCount() throws OrisunException {
        return getUserCount(GetUserCountRequest.newBuilder().build());
    }

    /**
     * Get event count for a boundary
     */
    public long getEventCount(GetEventCountRequest request) throws OrisunException {
        AdminRequestValidator.validateGetEventCountRequest(request);

        logger.debug("Getting event count for boundary: {}", request.getBoundary());

        try {
            GetEventCountResponse response = blockingStub
                    .withDeadlineAfter(defaultTimeoutSeconds, TimeUnit.SECONDS)
                    .getEventCount(request);

            logger.debug("Event count for boundary '{}': {}", request.getBoundary(), response.getCount());
            return response.getCount();

        } catch (StatusRuntimeException e) {
            throw handleException(e, "getEventCount");
        }
    }

    /**
     * Get event count for a boundary (convenience method)
     */
    public long getEventCount(String boundary) throws OrisunException {
        return getEventCount(GetEventCountRequest.newBuilder().setBoundary(boundary).build());
    }

    private OrisunException handleException(StatusRuntimeException e, String operation) {
        Map<String, Object> context = new HashMap<>();
        context.put("operation", operation);
        context.put("statusCode", e.getStatus().getCode().name());
        context.put("statusDescription", e.getStatus().getDescription());

        return new OrisunException("Admin operation failed: " + operation, e, context);
    }

    @Override
    public void close() {
        logger.debug("Closing AdminClient connection");

        if (channel != null && !channel.isShutdown()) {
            try {
                channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
                logger.info("AdminClient connection closed successfully");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Interrupted while closing AdminClient connection", e);
            }
        }
    }
}
