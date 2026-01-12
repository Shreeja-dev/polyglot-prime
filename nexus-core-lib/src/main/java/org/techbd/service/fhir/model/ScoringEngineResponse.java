package org.techbd.service.fhir.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Value object representing a response from the scoring engine.
 * 
 * Immutable and type-safe representation of scoring engine responses.
 * Provides convenient methods to check status and extract data.
 * 
 * Follows SOLID principles:
 * - Single Responsibility: Only represents response data
 * - Open/Closed: Can be extended with new factory methods
 */
public record ScoringEngineResponse(
    // Response Status
    ResponseStatus status,
    
    // Response Body
    String rawResponse,
    JsonNode jsonResponse,
    
    // Metadata
    String interactionId,
    Instant timestamp,
    int httpStatusCode,
    
    // Error Information (if applicable)
    String errorMessage,
    Throwable error,
    
    // Additional response metadata
    Map<String, String> responseHeaders
) {
    
    /**
     * Compact constructor with validation and defaults.
     */
    public ScoringEngineResponse {
        Objects.requireNonNull(status, "Status cannot be null");
        Objects.requireNonNull(interactionId, "Interaction ID cannot be null");
        
        if (timestamp == null) {
            timestamp = Instant.now();
        }
        
        if (responseHeaders != null) {
            responseHeaders = Map.copyOf(responseHeaders);
        } else {
            responseHeaders = Map.of();
        }
    }
    
    /**
     * Response status enum.
     */
    public enum ResponseStatus {
        SUCCESS,
        FAILURE,
        ERROR,
        TIMEOUT,
        UNKNOWN
    }
    
    // ========== Factory Methods ==========
    
    /**
     * Creates a successful response.
     */
    public static ScoringEngineResponse success(
            String interactionId,
            String rawResponse) {
        return success(interactionId, rawResponse, 200);
    }
    
    /**
     * Creates a successful response with status code.
     */
    public static ScoringEngineResponse success(
            String interactionId,
            String rawResponse,
            int httpStatusCode) {
        
        JsonNode jsonNode = parseJson(rawResponse);
        
        return new ScoringEngineResponse(
            ResponseStatus.SUCCESS,
            rawResponse,
            jsonNode,
            interactionId,
            Instant.now(),
            httpStatusCode,
            null,
            null,
            Map.of()
        );
    }
    
    /**
     * Creates a successful response with headers.
     */
    public static ScoringEngineResponse success(
            String interactionId,
            String rawResponse,
            int httpStatusCode,
            Map<String, String> headers) {
        
        JsonNode jsonNode = parseJson(rawResponse);
        
        return new ScoringEngineResponse(
            ResponseStatus.SUCCESS,
            rawResponse,
            jsonNode,
            interactionId,
            Instant.now(),
            httpStatusCode,
            null,
            null,
            headers
        );
    }
    
    /**
     * Creates a failure response (server returned failure status).
     */
    public static ScoringEngineResponse failure(
            String interactionId,
            String rawResponse,
            int httpStatusCode) {
        
        JsonNode jsonNode = parseJson(rawResponse);
        
        return new ScoringEngineResponse(
            ResponseStatus.FAILURE,
            rawResponse,
            jsonNode,
            interactionId,
            Instant.now(),
            httpStatusCode,
            extractErrorMessage(rawResponse),
            null,
            Map.of()
        );
    }
    
    /**
     * Creates an error response (exception occurred).
     */
    public static ScoringEngineResponse error(
            String interactionId,
            String errorMessage,
            Throwable error) {
        return new ScoringEngineResponse(
            ResponseStatus.ERROR,
            null,
            null,
            interactionId,
            Instant.now(),
            0,
            errorMessage,
            error,
            Map.of()
        );
    }
    
    /**
     * Creates an error response with HTTP status.
     */
    public static ScoringEngineResponse error(
            String interactionId,
            String rawResponse,
            int httpStatusCode,
            Throwable error) {
        
        JsonNode jsonNode = parseJson(rawResponse);
        
        return new ScoringEngineResponse(
            ResponseStatus.ERROR,
            rawResponse,
            jsonNode,
            interactionId,
            Instant.now(),
            httpStatusCode,
            error.getMessage(),
            error,
            Map.of()
        );
    }
    
    /**
     * Creates a timeout response.
     */
    public static ScoringEngineResponse timeout(
            String interactionId,
            String message) {
        return new ScoringEngineResponse(
            ResponseStatus.TIMEOUT,
            null,
            null,
            interactionId,
            Instant.now(),
            408,
            message,
            null,
            Map.of()
        );
    }
    
    // ========== Convenience Methods ==========
    
    /**
     * Checks if the response indicates success.
     */
    public boolean isSuccess() {
        return status == ResponseStatus.SUCCESS;
    }
    
    /**
     * Checks if the response indicates failure.
     */
    public boolean isFailure() {
        return status == ResponseStatus.FAILURE;
    }
    
    /**
     * Checks if the response indicates an error.
     */
    public boolean isError() {
        return status == ResponseStatus.ERROR;
    }
    
    /**
     * Checks if the response timed out.
     */
    public boolean isTimeout() {
        return status == ResponseStatus.TIMEOUT;
    }
    
    /**
     * Returns the response as a Map.
     */
    public Optional<Map<String, Object>> getResponseAsMap() {
        if (jsonResponse == null) {
            return Optional.empty();
        }
        
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> map = mapper.convertValue(
                jsonResponse,
                new TypeReference<Map<String, Object>>() {}
            );
            return Optional.of(map);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
    
    /**
     * Gets a specific field from the JSON response.
     */
    public Optional<String> getField(String fieldName) {
        if (jsonResponse == null) {
            return Optional.empty();
        }
        
        JsonNode field = jsonResponse.get(fieldName);
        return field != null && !field.isNull() 
            ? Optional.of(field.asText()) 
            : Optional.empty();
    }
    
    /**
     * Gets the "status" field from the response (e.g., "Success", "Failure").
     */
    public Optional<String> getStatusField() {
        return getField("status");
    }
    
    /**
     * Gets the "bundle_id" field from the response.
     */
    public Optional<String> getBundleId() {
        return getField("bundle_id");
    }
    
    /**
     * Checks if the response body contains "Success" status.
     * Many scoring engines return {"status": "Success"}.
     */
    public boolean hasSuccessStatus() {
        return getStatusField()
            .map(s -> "Success".equalsIgnoreCase(s))
            .orElse(false);
    }
    
    /**
     * Gets the complete error information if available.
     */
    public Optional<ErrorInfo> getErrorInfo() {
        if (status != ResponseStatus.ERROR && status != ResponseStatus.FAILURE) {
            return Optional.empty();
        }
        
        return Optional.of(new ErrorInfo(
            errorMessage,
            error,
            httpStatusCode,
            rawResponse
        ));
    }
    
    /**
     * Converts this response to a JSON string.
     */
    public String toJsonString() {
        if (rawResponse != null) {
            return rawResponse;
        }
        
        if (jsonResponse != null) {
            return jsonResponse.toString();
        }
        
        // Create a JSON representation of the error
        return String.format(
            "{\"status\":\"%s\",\"error\":\"%s\",\"interactionId\":\"%s\"}",
            status,
            errorMessage != null ? errorMessage.replace("\"", "\\\"") : "unknown",
            interactionId
        );
    }
    
    /**
     * Creates a new response with updated status.
     */
    public ScoringEngineResponse withStatus(ResponseStatus newStatus) {
        return new ScoringEngineResponse(
            newStatus,
            rawResponse,
            jsonResponse,
            interactionId,
            timestamp,
            httpStatusCode,
            errorMessage,
            error,
            responseHeaders
        );
    }
    
    // ========== Helper Methods ==========
    
    /**
     * Parses JSON string to JsonNode, returns null if invalid.
     */
    private static JsonNode parseJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        
        try {
            return new ObjectMapper().readTree(json);
        } catch (JsonProcessingException e) {
            // Not valid JSON, return null
            return null;
        }
    }
    
    /**
     * Attempts to extract an error message from the response.
     */
    private static String extractErrorMessage(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return "Unknown error";
        }
        
        try {
            JsonNode node = parseJson(rawResponse);
            if (node != null) {
                // Try common error field names
                for (String field : new String[]{"error", "message", "errorMessage", "details"}) {
                    JsonNode errorNode = node.get(field);
                    if (errorNode != null && !errorNode.isNull()) {
                        return errorNode.asText();
                    }
                }
            }
        } catch (Exception e) {
            // Ignore parsing errors
        }
        
        return rawResponse.length() > 200 
            ? rawResponse.substring(0, 200) + "..." 
            : rawResponse;
    }
    
    /**
     * Error information holder.
     */
    public record ErrorInfo(
        String message,
        Throwable throwable,
        int httpStatusCode,
        String rawResponse
    ) {
        public String getDetailedMessage() {
            StringBuilder sb = new StringBuilder();
            
            if (message != null) {
                sb.append("Error: ").append(message);
            }
            
            if (httpStatusCode > 0) {
                sb.append(" (HTTP ").append(httpStatusCode).append(")");
            }
            
            if (throwable != null) {
                sb.append("\nCause: ").append(throwable.getMessage());
            }
            
            return sb.toString();
        }
    }
    
    @Override
    public String toString() {
        return "ScoringEngineResponse{" +
            "status=" + status +
            ", interactionId='" + interactionId + '\'' +
            ", timestamp=" + timestamp +
            ", httpStatusCode=" + httpStatusCode +
            ", hasResponse=" + (rawResponse != null) +
            ", hasError=" + (error != null) +
            '}';
    }
}