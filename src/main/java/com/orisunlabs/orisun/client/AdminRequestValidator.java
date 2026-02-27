package com.orisunlabs.orisun.client;

import com.orisun.admin.AdminOuterClass.*;

import java.util.UUID;

/**
 * Utility class for validating admin requests before sending to the server
 */
public class AdminRequestValidator {

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

    /**
     * Validate a CreateIndexRequest
     */
    public static void validateCreateIndexRequest(CreateIndexRequest request) {
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
    public static void validateDropIndexRequest(DropIndexRequest request) {
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