package com.orisunlabs.orisun.client;

import com.google.protobuf.Timestamp;
import com.orisun.admin.AdminGrpc;
import com.orisun.admin.AdminOuterClass.*;
import com.orisun.admin.AdminOuterClass.CreateUserRequest;
import com.orisun.admin.AdminOuterClass.CreateUserResponse;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AdminClientTest {
    private MockAdminService mockService;
    private AdminClient client;
    private Server server;
    private int port;

    @BeforeEach
    void setUp() throws Exception {
        // Choose a free ephemeral port
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }

        mockService = new MockAdminService();

        // Create and start the server on the chosen port
        server = ServerBuilder.forPort(port)
                .addService(mockService)
                .build()
                .start();

        // Create the client using the port
        client = AdminClient
                .newBuilder()
                .withServer("localhost", port)
                .build();
    }

    @Test
    void testTransportTuningOptions() throws Exception {
        try (AdminClient tunedClient = AdminClient
                .newBuilder()
                .withServer("localhost", port)
                .withMaxInboundMessageSize(100 * 1024 * 1024)
                .withFlowControlWindow(1024 * 1024)
                .build()) {
            assertNotNull(tunedClient);
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        if (client != null) {
            client.close();
        }
        if (server != null) {
            server.shutdownNow();
            server.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        }
    }

    @Test
    void testBoundaryManagement() {
        BoundaryPlacementInput placement = BoundaryPlacementInput.newBuilder()
                .setBackend("postgres")
                .setNamespace("orders")
                .build();
        BoundaryInfo created = BoundaryInfo.newBuilder()
                .setName("orders")
                .setPlacement(placement)
                .setStatus(BoundaryLifecycleStatus.BOUNDARY_LIFECYCLE_STATUS_PROVISIONING)
                .setOrigin(BoundaryRegistrationOrigin.BOUNDARY_REGISTRATION_ORIGIN_CREATED)
                .build();
        BoundaryInfo imported = created.toBuilder()
                .setName("legacy_orders")
                .setOrigin(BoundaryRegistrationOrigin.BOUNDARY_REGISTRATION_ORIGIN_IMPORTED)
                .build();

        mockService.setNextCreateBoundaryResponse(CreateBoundaryResponse.newBuilder().setBoundary(created).build());
        mockService.setNextImportBoundaryResponse(ImportBoundaryResponse.newBuilder().setBoundary(imported).build());
        mockService.setNextListBoundariesResponse(ListBoundariesResponse.newBuilder()
                .addBoundaries(created)
                .addBoundaries(imported)
                .build());
        mockService.setNextGetBoundaryResponse(GetBoundaryResponse.newBuilder().setBoundary(created).build());

        BoundaryInfo createResult = client.createBoundary(CreateBoundaryRequest.newBuilder()
                .setName("orders")
                .setPlacement(placement)
                .build());
        BoundaryInfo importResult = client.importBoundary(ImportBoundaryRequest.newBuilder()
                .setName("legacy_orders")
                .setPlacement(placement)
                .build());
        List<BoundaryInfo> listResult = client.listBoundaries();
        BoundaryInfo getResult = client.getBoundary("orders");

        assertEquals(BoundaryRegistrationOrigin.BOUNDARY_REGISTRATION_ORIGIN_CREATED, createResult.getOrigin());
        assertEquals(BoundaryRegistrationOrigin.BOUNDARY_REGISTRATION_ORIGIN_IMPORTED, importResult.getOrigin());
        assertEquals(2, listResult.size());
        assertEquals("orders", getResult.getName());
        assertEquals("orders", mockService.getLastCreateBoundaryRequest().getName());
        assertEquals("legacy_orders", mockService.getLastImportBoundaryRequest().getName());
        assertEquals("orders", mockService.getLastGetBoundaryRequest().getName());
    }

    @Test
    void testBoundaryManagementValidation() {
        OrisunException createError = assertThrows(OrisunException.class, () ->
                client.createBoundary(CreateBoundaryRequest.newBuilder().setName("orders").build()));
        assertTrue(createError.getMessage().contains("Boundary placement is required"));
        assertEquals("createBoundary", createError.getContext("operation"));

        OrisunException importError = assertThrows(OrisunException.class, () ->
                client.importBoundary(ImportBoundaryRequest.newBuilder()
                        .setName("orders")
                        .setPlacement(BoundaryPlacementInput.newBuilder().setBackend("postgres"))
                        .build()));
        assertTrue(importError.getMessage().contains("namespace is required"));

        OrisunException getError = assertThrows(OrisunException.class, () -> client.getBoundary(""));
        assertTrue(getError.getMessage().contains("Boundary name is required"));

        OrisunException nullNameError = assertThrows(OrisunException.class, () -> client.getBoundary((String) null));
        assertTrue(nullNameError.getMessage().contains("Boundary name is required"));
    }

    @Test
    void testCreateUser() throws Exception {
        // Prepare test data
        CreateUserRequest request = CreateUserRequest.newBuilder()
                .setName("John Doe")
                .setUsername("johndoe")
                .setPassword("password123")
                .addRoles("admin")
                .build();

        // Configure mock response
        AdminUser mockUser = AdminUser.newBuilder()
                .setUserId("user-123")
                .setName("John Doe")
                .setUsername("johndoe")
                .addRoles("admin")
                .setCreatedAt(Timestamp.newBuilder().setSeconds(Instant.now().getEpochSecond()).build())
                .setUpdatedAt(Timestamp.newBuilder().setSeconds(Instant.now().getEpochSecond()).build())
                .build();
        mockService.setNextCreateUserResponse(CreateUserResponse.newBuilder().setUser(mockUser).build());

        // Execute test
        AdminUser result = client.createUser(request);

        // Verify results
        assertNotNull(result);
        assertEquals("user-123", result.getUserId());
        assertEquals("John Doe", result.getName());
        assertEquals("johndoe", result.getUsername());
        assertEquals(1, result.getRolesCount());
        assertEquals("admin", result.getRoles(0));
        assertEquals(request, mockService.getLastCreateUserRequest());
    }

    @Test
    void testCreateUserWithValidation() {
        // Test validation with invalid request
        CreateUserRequest invalidRequest = CreateUserRequest.newBuilder()
                .setName("John Doe")
                .setUsername("johndoe")
                .setPassword("short") // Invalid: password too short
                .build();

        // Execute and verify exception
        OrisunException exception = assertThrows(OrisunException.class, () -> {
            client.createUser(invalidRequest);
        });

        assertTrue(exception.getMessage().contains("Password must be at least 8 characters"));
        assertEquals("createUser", exception.getContext("operation"));
    }

    @Test
    void testDeleteUser() throws Exception {
        // Prepare test data
        String userId = java.util.UUID.randomUUID().toString();
        DeleteUserRequest request = DeleteUserRequest.newBuilder()
                .setUserId(userId)
                .build();

        // Configure mock response
        mockService.setNextDeleteUserResponse(DeleteUserResponse.newBuilder().setSuccess(true).build());

        // Execute test
        boolean result = client.deleteUser(request);

        // Verify results
        assertTrue(result);
        assertEquals(request, mockService.getLastDeleteUserRequest());
    }

    @Test
    void testDeleteUserWithValidation() {
        // Test validation with invalid request
        DeleteUserRequest invalidRequest = DeleteUserRequest.newBuilder()
                .setUserId("invalid-uuid") // Invalid: not a valid UUID
                .build();

        // Execute and verify exception
        OrisunException exception = assertThrows(OrisunException.class, () -> {
            client.deleteUser(invalidRequest);
        });

        assertTrue(exception.getMessage().contains("Invalid user ID format"));
        assertEquals("deleteUser", exception.getContext("operation"));
    }

    @Test
    void testChangePassword() throws Exception {
        // Prepare test data
        String userId = java.util.UUID.randomUUID().toString();
        ChangePasswordRequest request = ChangePasswordRequest.newBuilder()
                .setUserId(userId)
                .setCurrentPassword("oldpassword")
                .setNewPassword("newpassword123")
                .build();

        // Configure mock response
        mockService.setNextChangePasswordResponse(ChangePasswordResponse.newBuilder().setSuccess(true).build());

        // Execute test
        boolean result = client.changePassword(request);

        // Verify results
        assertTrue(result);
        assertEquals(request, mockService.getLastChangePasswordRequest());
    }

    @Test
    void testChangePasswordWithValidation() {
        // Test validation with invalid request - same passwords
        String userId = java.util.UUID.randomUUID().toString();
        ChangePasswordRequest invalidRequest = ChangePasswordRequest.newBuilder()
                .setUserId(userId)
                .setCurrentPassword("password123")
                .setNewPassword("password123") // Invalid: same as current
                .build();

        // Execute and verify exception
        OrisunException exception = assertThrows(OrisunException.class, () -> {
            client.changePassword(invalidRequest);
        });

        assertTrue(exception.getMessage().contains("New password must be different from current password"));
        assertEquals("changePassword", exception.getContext("operation"));
    }

    @Test
    void testListUsers() throws Exception {
        // Prepare test data
        ListUsersRequest request = ListUsersRequest.newBuilder().build();

        // Configure mock response
        List<AdminUser> mockUsers = List.of(
                AdminUser.newBuilder()
                        .setUserId("user-1")
                        .setName("User One")
                        .setUsername("user1")
                        .addRoles("admin")
                        .build(),
                AdminUser.newBuilder()
                        .setUserId("user-2")
                        .setName("User Two")
                        .setUsername("user2")
                        .addRoles("user")
                        .build()
        );
        mockService.setNextListUsersResponse(ListUsersResponse.newBuilder().addAllUsers(mockUsers).build());

        // Execute test
        List<AdminUser> result = client.listUsers(request);

        // Verify results
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("user-1", result.get(0).getUserId());
        assertEquals("User One", result.get(0).getName());
        assertEquals("user1", result.get(0).getUsername());
        assertEquals("user-2", result.get(1).getUserId());
    }

    @Test
    void testListUsersConvenience() throws Exception {
        // Configure mock response
        mockService.setNextListUsersResponse(ListUsersResponse.newBuilder().addAllUsers(List.of()).build());

        // Execute test using convenience method
        List<AdminUser> result = client.listUsers();

        // Verify results
        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    void testValidateCredentials() throws Exception {
        // Prepare test data
        ValidateCredentialsRequest request = ValidateCredentialsRequest.newBuilder()
                .setUsername("johndoe")
                .setPassword("password123")
                .build();

        // Configure mock response
        AdminUser mockUser = AdminUser.newBuilder()
                .setUserId("user-123")
                .setName("John Doe")
                .setUsername("johndoe")
                .build();
        mockService.setNextValidateCredentialsResponse(
                ValidateCredentialsResponse.newBuilder()
                        .setSuccess(true)
                        .setUser(mockUser)
                        .build()
        );

        // Execute test
        ValidateCredentialsResponse result = client.validateCredentials(request);

        // Verify results
        assertNotNull(result);
        assertTrue(result.getSuccess());
        assertEquals("user-123", result.getUser().getUserId());
        assertEquals(request, mockService.getLastValidateCredentialsRequest());
    }

    @Test
    void testValidateCredentialsWithValidation() {
        // Test validation with invalid request
        ValidateCredentialsRequest invalidRequest = ValidateCredentialsRequest.newBuilder()
                .setUsername("johndoe")
                // Missing password
                .build();

        // Execute and verify exception
        OrisunException exception = assertThrows(OrisunException.class, () -> {
            client.validateCredentials(invalidRequest);
        });

        assertTrue(exception.getMessage().contains("Password is required"));
        assertEquals("validateCredentials", exception.getContext("operation"));
    }

    @Test
    void testGetUserCount() throws Exception {
        // Prepare test data
        GetUserCountRequest request = GetUserCountRequest.newBuilder().build();

        // Configure mock response
        mockService.setNextGetUserCountResponse(GetUserCountResponse.newBuilder().setCount(42).build());

        // Execute test
        long result = client.getUserCount(request);

        // Verify results
        assertEquals(42, result);
    }

    @Test
    void testGetUserCountConvenience() throws Exception {
        // Configure mock response
        mockService.setNextGetUserCountResponse(GetUserCountResponse.newBuilder().setCount(100).build());

        // Execute test using convenience method
        long result = client.getUserCount();

        // Verify results
        assertEquals(100, result);
    }

    @Test
    void testGetEventCount() throws Exception {
        // Prepare test data
        String boundary = "test-boundary";
        GetEventCountRequest request = GetEventCountRequest.newBuilder()
                .setBoundary(boundary)
                .build();

        // Configure mock response
        mockService.setNextGetEventCountResponse(GetEventCountResponse.newBuilder().setCount(1234).build());

        // Execute test
        long result = client.getEventCount(request);

        // Verify results
        assertEquals(1234, result);
        assertEquals(request, mockService.getLastGetEventCountRequest());
    }

    @Test
    void testGetEventCountConvenience() throws Exception {
        // Configure mock response
        mockService.setNextGetEventCountResponse(GetEventCountResponse.newBuilder().setCount(5678).build());

        // Execute test using convenience method
        long result = client.getEventCount("test-boundary");

        // Verify results
        assertEquals(5678, result);
    }

    @Test
    void testGetEventCountWithValidation() {
        // Test validation with invalid request
        GetEventCountRequest invalidRequest = GetEventCountRequest.newBuilder()
                .setBoundary("") // Invalid: empty boundary
                .build();

        // Execute and verify exception
        OrisunException exception = assertThrows(OrisunException.class, () -> {
            client.getEventCount(invalidRequest);
        });

        assertTrue(exception.getMessage().contains("Boundary is required"));
        assertEquals("getEventCount", exception.getContext("operation"));
    }

    // Mock service implementation
    private static class MockAdminService extends AdminGrpc.AdminImplBase {
        private CreateBoundaryRequest lastCreateBoundaryRequest;
        private ImportBoundaryRequest lastImportBoundaryRequest;
        private GetBoundaryRequest lastGetBoundaryRequest;
        private CreateUserRequest lastCreateUserRequest;
        private DeleteUserRequest lastDeleteUserRequest;
        private ChangePasswordRequest lastChangePasswordRequest;
        private ListUsersRequest lastListUsersRequest;
        private ValidateCredentialsRequest lastValidateCredentialsRequest;
        private GetUserCountRequest lastGetUserCountRequest;
        private GetEventCountRequest lastGetEventCountRequest;

        private CreateBoundaryResponse nextCreateBoundaryResponse;
        private ImportBoundaryResponse nextImportBoundaryResponse;
        private ListBoundariesResponse nextListBoundariesResponse;
        private GetBoundaryResponse nextGetBoundaryResponse;
        private CreateUserResponse nextCreateUserResponse;
        private DeleteUserResponse nextDeleteUserResponse;
        private ChangePasswordResponse nextChangePasswordResponse;
        private ListUsersResponse nextListUsersResponse;
        private ValidateCredentialsResponse nextValidateCredentialsResponse;
        private GetUserCountResponse nextGetUserCountResponse;
        private GetEventCountResponse nextGetEventCountResponse;

        void setNextCreateBoundaryResponse(CreateBoundaryResponse response) {
            this.nextCreateBoundaryResponse = response;
        }

        void setNextImportBoundaryResponse(ImportBoundaryResponse response) {
            this.nextImportBoundaryResponse = response;
        }

        void setNextListBoundariesResponse(ListBoundariesResponse response) {
            this.nextListBoundariesResponse = response;
        }

        void setNextGetBoundaryResponse(GetBoundaryResponse response) {
            this.nextGetBoundaryResponse = response;
        }

        void setNextCreateUserResponse(CreateUserResponse response) {
            this.nextCreateUserResponse = response;
        }

        void setNextDeleteUserResponse(DeleteUserResponse response) {
            this.nextDeleteUserResponse = response;
        }

        void setNextChangePasswordResponse(ChangePasswordResponse response) {
            this.nextChangePasswordResponse = response;
        }

        void setNextListUsersResponse(ListUsersResponse response) {
            this.nextListUsersResponse = response;
        }

        void setNextValidateCredentialsResponse(ValidateCredentialsResponse response) {
            this.nextValidateCredentialsResponse = response;
        }

        void setNextGetUserCountResponse(GetUserCountResponse response) {
            this.nextGetUserCountResponse = response;
        }

        void setNextGetEventCountResponse(GetEventCountResponse response) {
            this.nextGetEventCountResponse = response;
        }

        CreateBoundaryRequest getLastCreateBoundaryRequest() {
            return lastCreateBoundaryRequest;
        }

        ImportBoundaryRequest getLastImportBoundaryRequest() {
            return lastImportBoundaryRequest;
        }

        GetBoundaryRequest getLastGetBoundaryRequest() {
            return lastGetBoundaryRequest;
        }

        CreateUserRequest getLastCreateUserRequest() {
            return lastCreateUserRequest;
        }

        DeleteUserRequest getLastDeleteUserRequest() {
            return lastDeleteUserRequest;
        }

        ChangePasswordRequest getLastChangePasswordRequest() {
            return lastChangePasswordRequest;
        }

        ListUsersRequest getLastListUsersRequest() {
            return lastListUsersRequest;
        }

        ValidateCredentialsRequest getLastValidateCredentialsRequest() {
            return lastValidateCredentialsRequest;
        }

        GetUserCountRequest lastGetUserCountRequest() {
            return lastGetUserCountRequest;
        }

        GetEventCountRequest getLastGetEventCountRequest() {
            return lastGetEventCountRequest;
        }

        @Override
        public void createBoundary(CreateBoundaryRequest request, StreamObserver<CreateBoundaryResponse> responseObserver) {
            lastCreateBoundaryRequest = request;
            responseObserver.onNext(nextCreateBoundaryResponse);
            responseObserver.onCompleted();
        }

        @Override
        public void importBoundary(ImportBoundaryRequest request, StreamObserver<ImportBoundaryResponse> responseObserver) {
            lastImportBoundaryRequest = request;
            responseObserver.onNext(nextImportBoundaryResponse);
            responseObserver.onCompleted();
        }

        @Override
        public void listBoundaries(ListBoundariesRequest request, StreamObserver<ListBoundariesResponse> responseObserver) {
            responseObserver.onNext(nextListBoundariesResponse);
            responseObserver.onCompleted();
        }

        @Override
        public void getBoundary(GetBoundaryRequest request, StreamObserver<GetBoundaryResponse> responseObserver) {
            lastGetBoundaryRequest = request;
            responseObserver.onNext(nextGetBoundaryResponse);
            responseObserver.onCompleted();
        }

        @Override
        public void createUser(CreateUserRequest request, StreamObserver<CreateUserResponse> responseObserver) {
            lastCreateUserRequest = request;
            responseObserver.onNext(nextCreateUserResponse);
            responseObserver.onCompleted();
        }

        @Override
        public void deleteUser(DeleteUserRequest request, StreamObserver<DeleteUserResponse> responseObserver) {
            lastDeleteUserRequest = request;
            responseObserver.onNext(nextDeleteUserResponse);
            responseObserver.onCompleted();
        }

        @Override
        public void changePassword(ChangePasswordRequest request, StreamObserver<ChangePasswordResponse> responseObserver) {
            lastChangePasswordRequest = request;
            responseObserver.onNext(nextChangePasswordResponse);
            responseObserver.onCompleted();
        }

        @Override
        public void listUsers(ListUsersRequest request, StreamObserver<ListUsersResponse> responseObserver) {
            lastListUsersRequest = request;
            responseObserver.onNext(nextListUsersResponse);
            responseObserver.onCompleted();
        }

        @Override
        public void validateCredentials(ValidateCredentialsRequest request, StreamObserver<ValidateCredentialsResponse> responseObserver) {
            lastValidateCredentialsRequest = request;
            responseObserver.onNext(nextValidateCredentialsResponse);
            responseObserver.onCompleted();
        }

        @Override
        public void getUserCount(GetUserCountRequest request, StreamObserver<GetUserCountResponse> responseObserver) {
            lastGetUserCountRequest = request;
            responseObserver.onNext(nextGetUserCountResponse);
            responseObserver.onCompleted();
        }

        @Override
        public void getEventCount(GetEventCountRequest request, StreamObserver<GetEventCountResponse> responseObserver) {
            lastGetEventCountRequest = request;
            responseObserver.onNext(nextGetEventCountResponse);
            responseObserver.onCompleted();
        }
    }
}
