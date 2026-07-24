package com.orisunlabs.orisun.client;

import com.orisun.admin.AdminOuterClass.*;

import java.util.UUID;

/**
 * Utility class for validating admin requests before sending to the server
 */
public class AdminRequestValidator {

    /**
     * Validate a CreateBoundaryRequest.
     */
    public static void validateCreateBoundaryRequest(CreateBoundaryRequest request) {
        if (request == null) {
            throw new OrisunException("CreateBoundaryRequest cannot be null")
                    .addContext("operation", "createBoundary");
        }
        validateBoundaryDefinition(request.getName(), request.hasPlacement(), request.getPlacement(), "createBoundary");
    }

    private static void validateBoundaryDefinition(
            String name,
            boolean hasPlacement,
            BoundaryPlacementInput placement,
            String operation) {
        if (name == null || name.trim().isEmpty()) {
            throw new OrisunException("Boundary name is required")
                    .addContext("operation", operation);
        }
        if (!hasPlacement) {
            throw new OrisunException("Boundary placement is required")
                    .addContext("operation", operation)
                    .addContext("boundary", name);
        }
        if (placement.getBackend().trim().isEmpty()) {
            throw new OrisunException("Boundary placement backend is required")
                    .addContext("operation", operation)
                    .addContext("boundary", name);
        }
        if (placement.getNamespace().trim().isEmpty()) {
            throw new OrisunException("Boundary placement namespace is required")
                    .addContext("operation", operation)
                    .addContext("boundary", name);
        }
    }

    /**
     * Validate a ListBoundariesRequest.
     */
    public static void validateListBoundariesRequest(ListBoundariesRequest request) {
        if (request == null) {
            throw new OrisunException("ListBoundariesRequest cannot be null")
                    .addContext("operation", "listBoundaries");
        }
    }

    /**
     * Validate a GetBoundaryRequest.
     */
    public static void validateGetBoundaryRequest(GetBoundaryRequest request) {
        if (request == null) {
            throw new OrisunException("GetBoundaryRequest cannot be null")
                    .addContext("operation", "getBoundary");
        }
        if (request.getName().trim().isEmpty()) {
            throw new OrisunException("Boundary name is required")
                    .addContext("operation", "getBoundary");
        }
    }

    /**
     * Validate a CreateUserRequest
     */
    public static void validateCreateUserRequest(CreateUserRequest request) {
        if (request == null) {
            throw new OrisunException("CreateUserRequest cannot be null")
                    .addContext("operation", "createUser");
        }

        request.getName();
        if (request.getName().trim().isEmpty()) {
            throw new OrisunException("Name is required")
                    .addContext("operation", "createUser");
        }

        request.getUsername();
        if (request.getUsername().trim().isEmpty()) {
            throw new OrisunException("Username is required")
                    .addContext("operation", "createUser");
        }

        request.getPassword();
        if (request.getPassword().trim().isEmpty()) {
            throw new OrisunException("Password is required")
                    .addContext("operation", "createUser");
        }

        if (request.getPassword().length() < 8) {
            throw new OrisunException("Password must be at least 8 characters")
                    .addContext("operation", "createUser")
                    .addContext("username", request.getUsername());
        }
    }

    /**
     * Validate a DeleteUserRequest
     */
    public static void validateDeleteUserRequest(DeleteUserRequest request) {
        if (request == null) {
            throw new OrisunException("DeleteUserRequest cannot be null")
                    .addContext("operation", "deleteUser");
        }

        if (request.getUserId() == null || request.getUserId().trim().isEmpty()) {
            throw new OrisunException("User ID is required")
                    .addContext("operation", "deleteUser");
        }

        // Validate UUID format
        try {
            UUID.fromString(request.getUserId());
        } catch (IllegalArgumentException e) {
            throw new OrisunException("Invalid user ID format")
                    .addContext("operation", "deleteUser")
                    .addContext("userId", request.getUserId());
        }
    }

    /**
     * Validate a ChangePasswordRequest
     */
    public static void validateChangePasswordRequest(ChangePasswordRequest request) {
        if (request == null) {
            throw new OrisunException("ChangePasswordRequest cannot be null")
                    .addContext("operation", "changePassword");
        }

        if (request.getUserId() == null || request.getUserId().trim().isEmpty()) {
            throw new OrisunException("User ID is required")
                    .addContext("operation", "changePassword");
        }

        // Validate UUID format
        try {
            UUID.fromString(request.getUserId());
        } catch (IllegalArgumentException e) {
            throw new OrisunException("Invalid user ID format")
                    .addContext("operation", "changePassword")
                    .addContext("userId", request.getUserId());
        }

        if (request.getCurrentPassword() == null || request.getCurrentPassword().trim().isEmpty()) {
            throw new OrisunException("Current password is required")
                    .addContext("operation", "changePassword")
                    .addContext("userId", request.getUserId());
        }

        if (request.getNewPassword() == null || request.getNewPassword().trim().isEmpty()) {
            throw new OrisunException("New password is required")
                    .addContext("operation", "changePassword")
                    .addContext("userId", request.getUserId());
        }

        if (request.getNewPassword().length() < 8) {
            throw new OrisunException("New password must be at least 8 characters")
                    .addContext("operation", "changePassword")
                    .addContext("userId", request.getUserId());
        }

        if (request.getCurrentPassword().equals(request.getNewPassword())) {
            throw new OrisunException("New password must be different from current password")
                    .addContext("operation", "changePassword")
                    .addContext("userId", request.getUserId());
        }
    }

    /**
     * Validate a ListUsersRequest
     */
    public static void validateListUsersRequest(ListUsersRequest request) {
        if (request == null) {
            throw new OrisunException("ListUsersRequest cannot be null")
                    .addContext("operation", "listUsers");
        }
        // ListUsersRequest has no required fields, so just check for null
    }

    /**
     * Validate a ValidateCredentialsRequest
     */
    public static void validateValidateCredentialsRequest(ValidateCredentialsRequest request) {
        if (request == null) {
            throw new OrisunException("ValidateCredentialsRequest cannot be null")
                    .addContext("operation", "validateCredentials");
        }

        if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
            throw new OrisunException("Username is required")
                    .addContext("operation", "validateCredentials");
        }

        if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            throw new OrisunException("Password is required")
                    .addContext("operation", "validateCredentials")
                    .addContext("username", request.getUsername());
        }
    }

    /**
     * Validate a GetUserCountRequest
     */
    public static void validateGetUserCountRequest(GetUserCountRequest request) {
        if (request == null) {
            throw new OrisunException("GetUserCountRequest cannot be null")
                    .addContext("operation", "getUserCount");
        }
        // GetUserCountRequest has no required fields, so just check for null
    }

    /**
     * Validate a GetEventCountRequest
     */
    public static void validateGetEventCountRequest(GetEventCountRequest request) {
        if (request == null) {
            throw new OrisunException("GetEventCountRequest cannot be null")
                    .addContext("operation", "getEventCount");
        }

        if (request.getBoundary() == null || request.getBoundary().trim().isEmpty()) {
            throw new OrisunException("Boundary is required")
                    .addContext("operation", "getEventCount");
        }
    }

}
