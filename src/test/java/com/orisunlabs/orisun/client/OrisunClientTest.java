package com.orisunlabs.orisun.client;

import com.google.protobuf.Timestamp;
import com.orisun.eventstore.EventStoreGrpc;
import com.orisun.eventstore.Eventstore;
import com.orisun.eventstore.Eventstore.*;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class OrisunClientTest {
    private MockEventStoreService mockService;
    private OrisunClient client;
    private Server server;
    private int port;

    @BeforeEach
    void setUp() throws Exception {
        // Choose a free ephemeral port
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }

        mockService = new MockEventStoreService();

        // Create and start the server on the chosen port
        server = ServerBuilder.forPort(port)
                .addService(mockService)
                .build()
                .start();

        // Create the client using the port
        client = OrisunClient
                .newBuilder()
                .withServer("localhost", port)
                .build();
    }

    @Test
    void testWriteContextsPreserveObservationsAndWriteIds() throws Exception {
        var position = Eventstore.Position.newBuilder().setCommitPosition(Long.MAX_VALUE).setPreparePosition(7).build();
        var query = Eventstore.Query.newBuilder().addCriteria(Eventstore.Criterion.newBuilder()
                .addTags(Eventstore.Tag.newBuilder().setKey("__eventType").setValue("Created"))).build();
        var context = Eventstore.WriteContext.newBuilder().setWriteId(Long.MAX_VALUE + ":7")
                .addConsistency(Eventstore.ConsistencyObservation.newBuilder().setQuery(query).setPosition(position)).build();
        mockService.nextWriteContext = context;
        var request = Eventstore.GetWriteContextRequest.newBuilder().setBoundary("orders")
                .setWriteId(context.getWriteId()).build();
        assertEquals(context, client.getWriteContext(request));
        assertEquals(context, client.getWriteContextAsync(request).get(5, TimeUnit.SECONDS));
        assertEquals(request, mockService.lastWriteContextRequest);
        mockService.nextWriteContext = context.toBuilder().clearConsistency().build();
        assertEquals(0, client.getWriteContext(request).getConsistencyCount());
        mockService.setNextWriteResult(Eventstore.WriteResult.newBuilder().setWriteId(context.getWriteId())
                .setLogPosition(position).build());
        var saved = client.saveEventsV2(Eventstore.SaveEventsV2Request.newBuilder().setBoundary("orders")
                .addEvents(Eventstore.EventToSave.newBuilder().setEventId(UUID.randomUUID().toString())
                        .setEventType("Created").setData("{}"))
                .addAllConsistency(context.getConsistencyList()).build());
        assertEquals(context.getWriteId(), saved.getWriteId());
        assertEquals(Long.MAX_VALUE, saved.getLogPosition().getCommitPosition());
    }

    @Test
    void testWriteContextValidationAndErrors() throws Exception {
        assertThrows(OrisunException.class, () -> client.getWriteContext(null));
        assertThrows(OrisunException.class, () -> client.getWriteContext(Eventstore.GetWriteContextRequest.getDefaultInstance()));
        assertTrue(client.getWriteContextAsync(null).isCompletedExceptionally());
        var request = Eventstore.GetWriteContextRequest.newBuilder().setBoundary("orders").setWriteId("1:1").build();
        var error = assertThrows(OrisunException.class, () -> client.getWriteContext(request));
        assertEquals(io.grpc.Status.Code.NOT_FOUND, io.grpc.Status.fromThrowable(error.getCause()).getCode());
        assertThrows(java.util.concurrent.ExecutionException.class,
                () -> client.getWriteContextAsync(request).get(5, TimeUnit.SECONDS));
    }

    @Test
    void testTransportTuningOptions() throws Exception {
        try (OrisunClient tunedClient = OrisunClient
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
        (client).close();
        if (server != null) {
            server.shutdownNow();
            server.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void testSaveEvents() throws Exception {
        // Prepare test data
        String eventId = UUID.randomUUID().toString();
        Eventstore.SaveEventsRequest request = Eventstore.SaveEventsRequest.newBuilder()
                .setBoundary("users")
                .addEvents(Eventstore.EventToSave
                        .newBuilder()
                        .setEventId(eventId)
                        .setEventType("UserCreated")
                        .setData("{\"username\":\"test\"}")
                        .build())
                .build();

        // Configure mock response
        mockService.setNextWriteResult(Eventstore.WriteResult.newBuilder()
                .setLogPosition(Eventstore.Position.newBuilder()
                        .setCommitPosition(1)
                        .setPreparePosition(1)
                        .build())
                .build());

        // Execute test
        Eventstore.WriteResult result = client.saveEvents(request);

        // Verify results
        assertNotNull(result);
        assertEquals(1, result.getLogPosition().getCommitPosition());
        assertEquals(1, result.getLogPosition().getPreparePosition());
        assertEquals(request.getBoundary(), mockService.getLastSaveEventsV2Request().getBoundary());
        assertEquals(request.getEventsList(), mockService.getLastSaveEventsV2Request().getEventsList());
    }

    @Test
    void testSaveEventsWithValidation() throws Exception {
        // Test validation with invalid request
        Eventstore.SaveEventsRequest invalidRequest = Eventstore.SaveEventsRequest.newBuilder()
                .setBoundary("") // Invalid: empty boundary
                .build();

        // Execute and verify exception
        OrisunException exception = assertThrows(OrisunException.class, () -> {
            client.saveEvents(invalidRequest);
        });

        assertTrue(exception.getMessage().contains("Boundary is required"));
        assertEquals("saveEvents", exception.getContext("operation"));
    }

    @Test
    void testSaveEventsV2() throws Exception {
        Eventstore.SaveEventsV2Request request = Eventstore.SaveEventsV2Request.newBuilder()
                .setBoundary("shipping")
                .addEvents(Eventstore.EventToSave.newBuilder()
                        .setEventId(UUID.randomUUID().toString())
                        .setEventType("ShipmentDispatched")
                        .setData("{\"shipmentId\":\"shipment-9\"}")
                        .build())
                .addConsistency(Eventstore.ConsistencyObservation.newBuilder()
                        .setQuery(Eventstore.Query.newBuilder()
                                .addCriteria(Eventstore.Criterion.newBuilder()
                                        .addTags(Eventstore.Tag.newBuilder()
                                                .setKey("shipmentId")
                                                .setValue("shipment-9"))))
                        .setPosition(Eventstore.Position.newBuilder()
                                .setCommitPosition(12)
                                .setPreparePosition(12)))
                .build();

        mockService.setNextWriteResult(Eventstore.WriteResult.newBuilder()
                .setLogPosition(Eventstore.Position.newBuilder()
                        .setCommitPosition(13)
                        .setPreparePosition(13))
                .build());

        Eventstore.WriteResult result = client.saveEventsV2(request);

        assertEquals(13, result.getLogPosition().getCommitPosition());
        assertEquals(request, mockService.getLastSaveEventsV2Request());
    }

    @Test
    void testSaveEventsV2RejectsIncompleteObservation() {
        Eventstore.SaveEventsV2Request request = Eventstore.SaveEventsV2Request.newBuilder()
                .setBoundary("shipping")
                .addEvents(Eventstore.EventToSave.newBuilder()
                        .setEventId(UUID.randomUUID().toString())
                        .setEventType("ShipmentDispatched")
                        .setData("{}"))
                .addConsistency(Eventstore.ConsistencyObservation.newBuilder())
                .build();

        OrisunException exception = assertThrows(OrisunException.class, () -> client.saveEventsV2(request));
        assertTrue(exception.getMessage().contains("must include query and position"));
    }

    @Test
    void testSaveEventsAsync() throws Exception {
        // Prepare test data
        String eventId = UUID.randomUUID().toString();
        Eventstore.SaveEventsV2Request request = Eventstore.SaveEventsV2Request.newBuilder()
                .setBoundary("users")
                .addEvents(Eventstore.EventToSave
                        .newBuilder()
                        .setEventId(eventId)
                        .setEventType("UserCreated")
                        .setData("{\"username\":\"test\"}")
                        .build()
                )
                .build();

        // Configure mock response
        mockService.setNextWriteResult(Eventstore.WriteResult.newBuilder()
                .setLogPosition(Eventstore.Position.newBuilder()
                        .setCommitPosition(1)
                        .setPreparePosition(1)
                        .build())
                .build());

        // Execute test
        CompletableFuture<Eventstore.WriteResult> future = client.saveEventsV2Async(request);
        Eventstore.WriteResult result = future.get(5, TimeUnit.SECONDS);

        // Verify results
        assertNotNull(result);
        assertEquals(1, result.getLogPosition().getCommitPosition());
        assertEquals(1, result.getLogPosition().getPreparePosition());
    }

    @Test
    void testGetLatestByCriteria() throws Exception {
        Eventstore.GetLatestByCriteriaRequest request = Eventstore.GetLatestByCriteriaRequest.newBuilder()
                .setBoundary("accounts")
                .addCriteria(Eventstore.Criterion.newBuilder()
                        .addTags(Eventstore.Tag.newBuilder()
                                .setKey("account_id")
                                .setValue("acct-1")
                                .build())
                        .build())
                .addCriteria(Eventstore.Criterion.newBuilder()
                        .addTags(Eventstore.Tag.newBuilder()
                                .setKey("account_id")
                                .setValue("acct-2")
                                .build())
                        .build())
                .build();

        mockService.setNextLatestByCriteriaResponse(Eventstore.GetLatestByCriteriaResponse.newBuilder()
                .addResults(Eventstore.LatestCriterionResult.newBuilder()
                        .setCriterion(request.getCriteria(0))
                        .setEvent(Eventstore.Event.newBuilder()
                                .setEventId(UUID.randomUUID().toString())
                                .setEventType("MoneyCredited")
                                .setData("{\"account_id\":\"acct-1\",\"balance\":100}")
                                .setPosition(Eventstore.Position.newBuilder()
                                        .setCommitPosition(12)
                                        .setPreparePosition(12))
                                .build())
                        .build())
                .addResults(Eventstore.LatestCriterionResult.newBuilder()
                        .setCriterion(request.getCriteria(1))
                        .build())
                .setContextPosition(Eventstore.Position.newBuilder()
                        .setCommitPosition(12)
                        .setPreparePosition(12))
                .build());

        Eventstore.GetLatestByCriteriaResponse result = client.getLatestByCriteria(request);

        assertNotNull(result);
        assertEquals(2, result.getResultsCount());
        assertTrue(result.getResults(0).hasEvent());
        assertFalse(result.getResults(1).hasEvent());
        assertEquals(12, result.getContextPosition().getCommitPosition());
        assertEquals(request, mockService.getLastGetLatestByCriteriaRequest());
    }

    @Test
    void testGetLatestByCriteriaWithValidation() {
        Eventstore.GetLatestByCriteriaRequest invalidRequest = Eventstore.GetLatestByCriteriaRequest.newBuilder()
                .setBoundary("accounts")
                .build();

        OrisunException exception = assertThrows(OrisunException.class, () -> {
            client.getLatestByCriteria(invalidRequest);
        });

        assertTrue(exception.getMessage().contains("At least one criterion is required"));
        assertEquals("getLatestByCriteria", exception.getContext("operation"));
    }

    @Test
    void testGetLatestByCriteriaAsync() throws Exception {
        Eventstore.GetLatestByCriteriaRequest request = Eventstore.GetLatestByCriteriaRequest.newBuilder()
                .setBoundary("accounts")
                .addCriteria(Eventstore.Criterion.newBuilder()
                        .addTags(Eventstore.Tag.newBuilder()
                                .setKey("account_id")
                                .setValue("acct-1")
                                .build())
                        .build())
                .build();

        mockService.setNextLatestByCriteriaResponse(Eventstore.GetLatestByCriteriaResponse.newBuilder()
                .addResults(Eventstore.LatestCriterionResult.newBuilder()
                        .setCriterion(request.getCriteria(0))
                        .build())
                .setContextPosition(Eventstore.Position.newBuilder()
                        .setCommitPosition(-1)
                        .setPreparePosition(-1))
                .build());

        CompletableFuture<Eventstore.GetLatestByCriteriaResponse> future = client.getLatestByCriteriaAsync(request);
        Eventstore.GetLatestByCriteriaResponse result = future.get(5, TimeUnit.SECONDS);

        assertNotNull(result);
        assertEquals(1, result.getResultsCount());
        assertEquals(-1, result.getContextPosition().getCommitPosition());
    }

    @Test
    void testSubscribeToEvents() throws Exception {
        CountDownLatch eventLatch = new CountDownLatch(1);
        List<Eventstore.Event> receivedEvents = new CopyOnWriteArrayList<>();

        // Prepare subscription request
        final var request = CatchUpSubscribeToEventStoreRequest.newBuilder()
                .setBoundary("users")
                .setSubscriberName("test-subscriber")
                .build();

        // Set up subscription
        try (final var subscription = client.subscribeToEvents(request,
                new EventSubscription.EventHandler() {
                    @Override
                    public void onEvent(Eventstore.Event event) {
                        receivedEvents.add(event);
                        eventLatch.countDown();
                    }

                    @Override
                    public void onError(Throwable error) {
                        fail("Unexpected error: " + error);
                    }

                    @Override
                    public void onCompleted() {
                        // Not expected in this test
                    }
                })) {

            // Simulate server sending an event
            mockService.sendEvent(
                    Eventstore.Event.newBuilder()
                            .setEventId(UUID.randomUUID().toString())
                            .setEventType("UserCreated")
                            .setData("{\"username\":\"test\"}")
                            .build());

            // Wait for event to be received
            assertTrue(eventLatch.await(5, TimeUnit.SECONDS));
            assertEquals(1, receivedEvents.size());
            assertEquals("UserCreated", receivedEvents.getFirst().getEventType());
        }
    }

    @Test
    void testSubscribeToEventsWithValidation() throws Exception {
        // Test validation with invalid request
        Eventstore.CatchUpSubscribeToEventStoreRequest invalidRequest = Eventstore.CatchUpSubscribeToEventStoreRequest
                .newBuilder()
                .setBoundary("users")
                .setSubscriberName("") // Invalid: empty subscriber name
                .build();

        // Execute and verify exception
        OrisunException exception = assertThrows(OrisunException.class, () -> {
            client.subscribeToEvents(invalidRequest, new EventSubscription.EventHandler() {
                @Override
                public void onEvent(Eventstore.Event event) {
                }

                @Override
                public void onError(Throwable error) {
                }

                @Override
                public void onCompleted() {
                }
            });
        });

        assertTrue(exception.getMessage().contains("Subscriber name is required"));
        assertEquals("subscribeToEvents", exception.getContext("operation"));
    }

    @Test
    void testHealthCheck() throws Exception {
        // Mock successful ping
        mockService.setPingResponse(true);

        // Execute health check
        boolean isHealthy = client.healthCheck("test-boundary");

        // Verify result
        assertTrue(isHealthy);
    }

    @Test
    void testHealthCheckFailure() throws Exception {
        // Mock failed ping
        mockService.setPingResponse(false);

        // Execute health check and verify exception
        OrisunException exception = assertThrows(OrisunException.class, () -> {
            client.healthCheck("test-boundary");
        });

        assertTrue(exception.getMessage().contains("Ping failed"));
        assertEquals("ping", exception.getContext("operation"));
    }

    @Test
    void testPing() throws Exception {
        // Mock successful ping
        mockService.setPingResponse(true);

        // Execute ping - should not throw exception
        assertDoesNotThrow(() -> {
            client.ping();
        });
    }

    @Test
    void testPingFailure() throws Exception {
        // Mock failed ping
        mockService.setPingResponse(false);

        // Execute ping and verify exception
        OrisunException exception = assertThrows(OrisunException.class, () -> {
            client.ping();
        });

        assertTrue(exception.getMessage().contains("Ping failed"));
        assertEquals("ping", exception.getContext("operation"));
    }

    @Test
    void testGetServerInfo() throws Exception {
        Eventstore.GetServerInfoResponse info = client.getServerInfo();

        assertEquals("0.10.0", info.getVersion());
        assertEquals(Eventstore.StorageBackend.STORAGE_BACKEND_SQLITE, info.getBackend());
        assertEquals("node-1", info.getNodeId());
        assertEquals(List.of(
                Eventstore.ServerCapability.SERVER_CAPABILITY_COMMAND_CONTEXT_CONSISTENCY,
                Eventstore.ServerCapability.SERVER_CAPABILITY_GRPC_HEALTH
        ), info.getCapabilitiesList());
    }

    // Mock service implementation
    private static class MockEventStoreService extends EventStoreGrpc.EventStoreImplBase {
        private Eventstore.WriteContext nextWriteContext;
        private Eventstore.GetWriteContextRequest lastWriteContextRequest;

        @Override
        public void getWriteContext(Eventstore.GetWriteContextRequest request,
                                    StreamObserver<Eventstore.WriteContext> observer) {
            lastWriteContextRequest = request;
            if (nextWriteContext == null) {
                observer.onError(io.grpc.Status.NOT_FOUND.asRuntimeException());
            } else {
                observer.onNext(nextWriteContext);
                observer.onCompleted();
            }
        }

        private Eventstore.WriteResult nextWriteResult;
        private Eventstore.GetLatestByCriteriaResponse nextLatestByCriteriaResponse;
        private Eventstore.SaveEventsV2Request lastSaveEventsV2Request;
        private Eventstore.GetLatestByCriteriaRequest lastGetLatestByCriteriaRequest;
        private StreamObserver<Eventstore.Event> eventObserver;
        private boolean pingSuccess = true;

        void setNextWriteResult(Eventstore.WriteResult result) {
            this.nextWriteResult = result;
        }

        void setPingResponse(boolean success) {
            this.pingSuccess = success;
        }



        Eventstore.SaveEventsV2Request getLastSaveEventsV2Request() {
            return lastSaveEventsV2Request;
        }

        void setNextLatestByCriteriaResponse(Eventstore.GetLatestByCriteriaResponse response) {
            this.nextLatestByCriteriaResponse = response;
        }

        Eventstore.GetLatestByCriteriaRequest getLastGetLatestByCriteriaRequest() {
            return lastGetLatestByCriteriaRequest;
        }

        void sendEvent(Eventstore.Event event) {
            if (eventObserver != null) {
                eventObserver.onNext(event);
            }
        }


        @Override
        public void saveEventsV2(Eventstore.SaveEventsV2Request request,
                                 StreamObserver<Eventstore.WriteResult> responseObserver) {
            lastSaveEventsV2Request = request;
            responseObserver.onNext(nextWriteResult);
            responseObserver.onCompleted();
        }

        @Override
        public void getLatestByCriteria(Eventstore.GetLatestByCriteriaRequest request,
                                        StreamObserver<Eventstore.GetLatestByCriteriaResponse> responseObserver) {
            lastGetLatestByCriteriaRequest = request;
            responseObserver.onNext(nextLatestByCriteriaResponse);
            responseObserver.onCompleted();
        }

        @Override
        public void catchUpSubscribeToEvents(Eventstore.CatchUpSubscribeToEventStoreRequest request,
                                             StreamObserver<Eventstore.Event> responseObserver) {
            this.eventObserver = responseObserver;
            responseObserver.onNext(
                    Eventstore.Event.newBuilder()
                            .setEventId(UUID.randomUUID().toString())
                            .setEventType("UserCreated")
                            .setData("{\"username\":\"test\"}")
                            .setMetadata("{\"foo\":\"bar\"}")
                            .setDateCreated(Timestamp.newBuilder().setSeconds(Instant.now().getEpochSecond()).build())
                            .setPosition(Eventstore.Position.newBuilder()
                                    .setCommitPosition(1)
                                    .setPreparePosition(1))
                            .build());
        }

        @Override
        public void getEvents(GetEventsRequest request, StreamObserver<GetEventsResponse> responseObserver) {
            responseObserver.onNext(
                    GetEventsResponse
                            .newBuilder()
                            .addEvents(
                                    Eventstore.Event.newBuilder()
                                            .setEventId(UUID.randomUUID().toString())
                                            .setEventType("UserCreated")
                                            .setData("{\"username\":\"test\"}")
                                            .setMetadata("{\"foo\":\"bar\"}")
                                            .setDateCreated(Timestamp.newBuilder()
                                                    .setSeconds(Instant.now().getEpochSecond()).build())
                                            .setPosition(Eventstore.Position.newBuilder()
                                                    .setCommitPosition(1)
                                                    .setPreparePosition(1))
                                            .build())
                            .build());
            responseObserver.onCompleted();
        }

        @Override
        public void ping(Eventstore.PingRequest request, StreamObserver<Eventstore.PingResponse> responseObserver) {
            if (pingSuccess) {
                responseObserver.onNext(Eventstore.PingResponse.newBuilder().build());
                responseObserver.onCompleted();
            } else {
                responseObserver.onError(new RuntimeException("Ping failed"));
            }
        }

        @Override
        public void getServerInfo(Eventstore.GetServerInfoRequest request,
                                  StreamObserver<Eventstore.GetServerInfoResponse> responseObserver) {
            responseObserver.onNext(Eventstore.GetServerInfoResponse.newBuilder()
                    .setVersion("0.10.0")
                    .setGitCommit("abc123")
                    .setBuildTime("2026-07-25T12:00:00Z")
                    .setBackend(Eventstore.StorageBackend.STORAGE_BACKEND_SQLITE)
                    .setNodeId("node-1")
                    .addCapabilities(Eventstore.ServerCapability.SERVER_CAPABILITY_COMMAND_CONTEXT_CONSISTENCY)
                    .addCapabilities(Eventstore.ServerCapability.SERVER_CAPABILITY_GRPC_HEALTH)
                    .build());
            responseObserver.onCompleted();
        }
    }
}
