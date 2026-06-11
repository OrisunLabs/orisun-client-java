package com.orisunlabs.orisun.client;

import com.orisun.eventstore.Eventstore;

import java.util.UUID;

/**
 * Utility class for validating requests before sending to the server
 */
public class RequestValidator {

    /**
     * Validate a SaveEventsRequest
     *
     * @param request The request to validate
     * @throws OrisunException if validation fails
     */
    public static void validateSaveEventsRequest(Eventstore.SaveEventsRequest request) {
        if (request == null) {
            throw new OrisunException("SaveEventsRequest cannot be null");
        }

        // Validate boundary
        if (request.getBoundary() == null || request.getBoundary().trim().isEmpty()) {
            throw new OrisunException("Boundary is required")
                    .addContext("operation", "saveEvents")
                    .addContext("request", "SaveEventsRequest");
        }

        // Validate events
        if (request.getEventsCount() == 0) {
            throw new OrisunException("At least one event is required")
                    .addContext("operation", "saveEvents")
                    .addContext("boundary", request.getBoundary());
        }

        // Validate each event
        for (int i = 0; i < request.getEventsCount(); i++) {
            Eventstore.EventToSave event = request.getEvents(i);
            validateEventToSave(event, i, request.getBoundary());
        }
    }

    /**
     * Validate an EventToSave
     *
     * @param event    The event to validate
     * @param index    The event index for error context
     * @param boundary The boundary for error context
     * @throws OrisunException if validation fails
     */
    private static void validateEventToSave(Eventstore.EventToSave event, int index, String boundary) {
        if (event.getEventId() == null || event.getEventId().trim().isEmpty()) {
            throw new OrisunException("Event at index " + index + " is missing eventId")
                    .addContext("operation", "saveEvents")
                    .addContext("eventIndex", index)
                    .addContext("boundary", boundary);
        }

        // Validate UUID format
        try {
            UUID.fromString(event.getEventId());
        } catch (IllegalArgumentException e) {
            throw new OrisunException("Event at index " + index + " has invalid eventId format")
                    .addContext("operation", "saveEvents")
                    .addContext("eventIndex", index)
                    .addContext("eventId", event.getEventId())
                    .addContext("boundary", boundary);
        }

        if (event.getEventType() == null || event.getEventType().trim().isEmpty()) {
            throw new OrisunException("Event at index " + index + " is missing eventType")
                    .addContext("operation", "saveEvents")
                    .addContext("eventIndex", index)
                    .addContext("boundary", boundary);
        }

        if (event.getData() == null || event.getData().trim().isEmpty()) {
            throw new OrisunException("Event at index " + index + " is missing data")
                    .addContext("operation", "saveEvents")
                    .addContext("eventIndex", index)
                    .addContext("boundary", boundary);
        }
    }

    /**
     * Validate a GetEventsRequest
     *
     * @param request The request to validate
     * @throws OrisunException if validation fails
     */
    public static void validateGetEventsRequest(Eventstore.GetEventsRequest request) {
        if (request == null) {
            throw new OrisunException("GetEventsRequest cannot be null")
                    .addContext("operation", "getEvents");
        }

        // Validate boundary
        if (request.getBoundary() == null || request.getBoundary().trim().isEmpty()) {
            throw new OrisunException("Boundary is required")
                    .addContext("operation", "getEvents")
                    .addContext("request", "GetEventsRequest");
        }

        // Validate count if provided
        if (request.getCount() <= 0) {
            throw new OrisunException("Count must be greater than 0")
                    .addContext("operation", "getEvents")
                    .addContext("count", request.getCount())
                    .addContext("boundary", request.getBoundary());
        }
    }

    /**
     * Validate a GetLatestByCriteriaRequest
     *
     * @param request The request to validate
     * @throws OrisunException if validation fails
     */
    public static void validateGetLatestByCriteriaRequest(Eventstore.GetLatestByCriteriaRequest request) {
        if (request == null) {
            throw new OrisunException("GetLatestByCriteriaRequest cannot be null")
                    .addContext("operation", "getLatestByCriteria");
        }

        if (request.getBoundary() == null || request.getBoundary().trim().isEmpty()) {
            throw new OrisunException("Boundary is required")
                    .addContext("operation", "getLatestByCriteria")
                    .addContext("request", "GetLatestByCriteriaRequest");
        }

        if (request.getCriteriaCount() == 0) {
            throw new OrisunException("At least one criterion is required")
                    .addContext("operation", "getLatestByCriteria")
                    .addContext("boundary", request.getBoundary());
        }

        for (int i = 0; i < request.getCriteriaCount(); i++) {
            if (request.getCriteria(i).getTagsCount() == 0) {
                throw new OrisunException("Criterion at index " + i + " must include at least one tag")
                        .addContext("operation", "getLatestByCriteria")
                        .addContext("criterionIndex", i)
                        .addContext("boundary", request.getBoundary());
            }
        }
    }

    /**
     * Validate a CatchUpSubscribeToEventStoreRequest
     *
     * @param request The request to validate
     * @throws OrisunException if validation fails
     */
    public static void validateSubscribeRequest(Eventstore.CatchUpSubscribeToEventStoreRequest request) {
        if (request == null) {
            throw new OrisunException("SubscribeRequest cannot be null")
                    .addContext("operation", "subscribeToEvents");
        }

        // Validate boundary
        if (request.getBoundary() == null || request.getBoundary().trim().isEmpty()) {
            throw new OrisunException("Boundary is required")
                    .addContext("operation", "subscribeToEvents")
                    .addContext("request", "CatchUpSubscribeToEventStoreRequest");
        }

        // Validate subscriber name
        if (request.getSubscriberName() == null || request.getSubscriberName().trim().isEmpty()) {
            throw new OrisunException("Subscriber name is required")
                    .addContext("operation", "subscribeToEvents")
                    .addContext("boundary", request.getBoundary());
        }
    }

    /**
     * Validate a CreateIndexRequest
     */
    public static void validateCreateIndexRequest(Eventstore.CreateIndexRequest request) {
        if (request == null) {
            throw new OrisunException("CreateIndexRequest cannot be null")
                    .addContext("operation", "createIndex");
        }

        if (request.getBoundary() == null || request.getBoundary().trim().isEmpty()) {
            throw new OrisunException("Boundary is required")
                    .addContext("operation", "createIndex");
        }

        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new OrisunException("Index name is required")
                    .addContext("operation", "createIndex")
                    .addContext("boundary", request.getBoundary());
        }

        if (request.getFieldsCount() == 0) {
            throw new OrisunException("At least one field is required")
                    .addContext("operation", "createIndex")
                    .addContext("boundary", request.getBoundary())
                    .addContext("indexName", request.getName());
        }
    }

    /**
     * Validate a DropIndexRequest
     */
    public static void validateDropIndexRequest(Eventstore.DropIndexRequest request) {
        if (request == null) {
            throw new OrisunException("DropIndexRequest cannot be null")
                    .addContext("operation", "dropIndex");
        }

        if (request.getBoundary() == null || request.getBoundary().trim().isEmpty()) {
            throw new OrisunException("Boundary is required")
                    .addContext("operation", "dropIndex");
        }

        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new OrisunException("Index name is required")
                    .addContext("operation", "dropIndex")
                    .addContext("boundary", request.getBoundary());
        }
    }
}
