package org.techbd.service.fhir.model;

import java.util.Map;

/**
 * Value object representing the context for FHIR bundle processing.
 * 
 * Benefits:
 * - Immutable (thread-safe)
 * - Type-safe (no more Map<String, Object>)
 * - Self-documenting
 * - Easier to test
 * - Reduces parameter lists
 */
public record FHIRProcessingContext(
    String interactionId,
    String tenantId,
    String groupInteractionId,
    String masterInteractionId,
    String correlationId,
    String sourceType,
    String requestUri,
    String bundleId,
    boolean isHealthCheck,
    boolean isReplay,
    String provenance,
    Map<String, Object> requestParameters
) {
    
    /**
     * Creates a context from raw request parameters.
     */
    public static FHIRProcessingContext fromRequestParameters(
            Map<String, Object> params, 
            String bundleId) {
        
        return new FHIRProcessingContext(
            extractString(params, "interactionId"),
            extractString(params, "tenantId"),
            extractString(params, "groupInteractionId"),
            extractString(params, "masterInteractionId"),
            extractString(params, "correlationId"),
            extractString(params, "sourceType"),
            extractString(params, "requestUri"),
            bundleId,
            extractBoolean(params, "healthCheck"),
            extractBoolean(params, "replay"),
            extractString(params, "provenance"),
            Map.copyOf(params) // Immutable copy
        );
    }
    
    /**
     * Returns the effective interaction ID (correlation ID takes precedence).
     */
    public String effectiveInteractionId() {
        return correlationId != null && !correlationId.isBlank() 
            ? correlationId 
            : interactionId;
    }
    
    /**
     * Checks if this is a validation-only request.
     */
    public boolean isValidationOnly() {
        return requestUri != null && 
               (requestUri.equals("/Bundle/$validate") || 
                requestUri.equals("/Bundle/$validate/"));
    }
    
    private static String extractString(Map<String, Object> params, String key) {
        Object value = params.get(key);
        return value != null ? value.toString() : null;
    }
    
    private static boolean extractBoolean(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value == null) return false;
        
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        
        String strValue = value.toString().trim();
        return "true".equalsIgnoreCase(strValue);
    }
    
    /**
     * Creates a new context with updated interaction ID.
     */
    public FHIRProcessingContext withInteractionId(String newInteractionId) {
        return new FHIRProcessingContext(
            newInteractionId,
            tenantId,
            groupInteractionId,
            masterInteractionId,
            correlationId,
            sourceType,
            requestUri,
            bundleId,
            isHealthCheck,
            isReplay,
            provenance,
            requestParameters
        );
    }
}
