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
        assertEquals(request, mockService.getLastSaveEventsRequest());
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
    void testSaveEventsAsync() throws Exception {
        // Prepare test data
        String eventId = UUID.randomUUID().toString();
        Eventstore.SaveEventsRequest request = Eventstore.SaveEventsRequest.newBuilder()
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
        CompletableFuture<Eventstore.WriteResult> future = client.saveEventsAsync(request);
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

    // Mock service implementation
    private static class MockEventStoreService extends EventStoreGrpc.EventStoreImplBase {
        private Eventstore.WriteResult nextWriteResult;
        private Eventstore.GetLatestByCriteriaResponse nextLatestByCriteriaResponse;
        private Eventstore.SaveEventsRequest lastSaveEventsRequest;
        private Eventstore.GetLatestByCriteriaRequest lastGetLatestByCriteriaRequest;
        private StreamObserver<Eventstore.Event> eventObserver;
        private boolean pingSuccess = true;

        void setNextWriteResult(Eventstore.WriteResult result) {
            this.nextWriteResult = result;
        }

        void setPingResponse(boolean success) {
            this.pingSuccess = success;
        }

        Eventstore.SaveEventsRequest getLastSaveEventsRequest() {
            return lastSaveEventsRequest;
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
        public void saveEvents(Eventstore.SaveEventsRequest request,
                               StreamObserver<Eventstore.WriteResult> responseObserver) {
            lastSaveEventsRequest = request;
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
    }
}
