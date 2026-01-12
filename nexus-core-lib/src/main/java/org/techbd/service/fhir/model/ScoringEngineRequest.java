package org.techbd.service.fhir.model;

import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Value object representing a request to the scoring engine.
 * 
 * Benefits of using this instead of raw parameters:
 * - Type safety
 * - Immutability (thread-safe)
 * - Self-documenting
 * - Validation in one place
 * - Easy to evolve (add new fields without breaking existing code)
 * 
 * Follows SOLID principles:
 * - Single Responsibility: Only represents request data
 * - Open/Closed: Can add new fields without modifying existing code
 */
public record ScoringEngineRequest(
    // Identity & Context
    String interactionId,
    String tenantId,
    String groupInteractionId,
    String masterInteractionId,
    String bundleId,
    
    // Request Configuration
    String baseUrl,
    String contentType,
    String processingAgent,
    
    // Payload
    Object payload,  // Can be String, Map, or JsonNode
    
    // Source Information
    String sourceType,
    String provenance,
    
    // State Information
    boolean isReplay,
    
    // Additional metadata
    Map<String, Object> additionalMetadata
) {
    
    /**
     * Compact constructor with validation.
     */
    public ScoringEngineRequest {
        Objects.requireNonNull(interactionId, "Interaction ID cannot be null");
        Objects.requireNonNull(tenantId, "Tenant ID cannot be null");
        Objects.requireNonNull(baseUrl, "Base URL cannot be null");
        Objects.requireNonNull(payload, "Payload cannot be null");
        
        // Provide defaults for optional fields
        if (contentType == null || contentType.isBlank()) {
            contentType = "application/json";
        }
        
        if (processingAgent == null || processingAgent.isBlank()) {
            processingAgent = tenantId;
        }
        
        // Make metadata map immutable
        if (additionalMetadata != null) {
            additionalMetadata = Map.copyOf(additionalMetadata);
        } else {
            additionalMetadata = Map.of();
        }
    }
    
    /**
     * Builder for convenient construction with many optional parameters.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Creates a basic request with minimal required fields.
     */
    public static ScoringEngineRequest basic(
            String interactionId,
            String tenantId,
            String baseUrl,
            Object payload) {
        return new ScoringEngineRequest(
            interactionId,
            tenantId,
            null, // groupInteractionId
            null, // masterInteractionId
            null, // bundleId
            baseUrl,
            "application/json",
            tenantId, // processingAgent defaults to tenantId
            payload,
            "FHIR",
            null,
            false,
            Map.of()
        );
    }
    
    /**
     * Creates a request from FHIRProcessingContext.
     */
    public static ScoringEngineRequest fromContext(
            FHIRProcessingContext context,
            String baseUrl,
            Object payload,
            String contentType) {
        return new ScoringEngineRequest(
            context.effectiveInteractionId(),
            context.tenantId(),
            context.groupInteractionId(),
            context.masterInteractionId(),
            context.bundleId(),
            baseUrl,
            contentType,
            context.tenantId(),
            payload,
            context.sourceType(),
            context.provenance(),
            context.isReplay(),
            context.requestParameters()
        );
    }
    
    /**
     * Returns the payload as a String (for JSON payloads).
     */
    public String getPayloadAsString() {
        if (payload instanceof String) {
            return (String) payload;
        }
        
        if (payload instanceof JsonNode) {
            return payload.toString();
        }
        
        throw new IllegalStateException(
            "Payload is not a String or JsonNode: " + payload.getClass()
        );
    }
    
    /**
     * Returns the payload as a Map (for structured payloads).
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getPayloadAsMap() {
        if (payload instanceof Map) {
            return (Map<String, Object>) payload;
        }
        
        throw new IllegalStateException(
            "Payload is not a Map: " + payload.getClass()
        );
    }
    
    /**
     * Creates a new request with updated processing agent.
     */
    public ScoringEngineRequest withProcessingAgent(String newProcessingAgent) {
        return new ScoringEngineRequest(
            interactionId,
            tenantId,
            groupInteractionId,
            masterInteractionId,
            bundleId,
            baseUrl,
            contentType,
            newProcessingAgent,
            payload,
            sourceType,
            provenance,
            isReplay,
            additionalMetadata
        );
    }
    
    /**
     * Creates a new request with additional metadata.
     */
    public ScoringEngineRequest withMetadata(String key, Object value) {
        Map<String, Object> newMetadata = new java.util.HashMap<>(additionalMetadata);
        newMetadata.put(key, value);
        
        return new ScoringEngineRequest(
            interactionId,
            tenantId,
            groupInteractionId,
            masterInteractionId,
            bundleId,
            baseUrl,
            contentType,
            processingAgent,
            payload,
            sourceType,
            provenance,
            isReplay,
            Map.copyOf(newMetadata)
        );
    }
    
    /**
     * Builder class for fluent construction.
     */
    public static class Builder {
        private String interactionId;
        private String tenantId;
        private String groupInteractionId;
        private String masterInteractionId;
        private String bundleId;
        private String baseUrl;
        private String contentType = "application/json";
        private String processingAgent;
        private Object payload;
        private String sourceType = "FHIR";
        private String provenance;
        private boolean isReplay = false;
        private Map<String, Object> additionalMetadata = Map.of();
        
        public Builder interactionId(String interactionId) {
            this.interactionId = interactionId;
            return this;
        }
        
        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }
        
        public Builder groupInteractionId(String groupInteractionId) {
            this.groupInteractionId = groupInteractionId;
            return this;
        }
        
        public Builder masterInteractionId(String masterInteractionId) {
            this.masterInteractionId = masterInteractionId;
            return this;
        }
        
        public Builder bundleId(String bundleId) {
            this.bundleId = bundleId;
            return this;
        }
        
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }
        
        public Builder contentType(String contentType) {
            this.contentType = contentType;
            return this;
        }
        
        public Builder processingAgent(String processingAgent) {
            this.processingAgent = processingAgent;
            return this;
        }
        
        public Builder payload(Object payload) {
            this.payload = payload;
            return this;
        }
        
        public Builder sourceType(String sourceType) {
            this.sourceType = sourceType;
            return this;
        }
        
        public Builder provenance(String provenance) {
            this.provenance = provenance;
            return this;
        }
        
        public Builder isReplay(boolean isReplay) {
            this.isReplay = isReplay;
            return this;
        }
        
        public Builder additionalMetadata(Map<String, Object> metadata) {
            this.additionalMetadata = metadata;
            return this;
        }
        
        public Builder fromContext(FHIRProcessingContext context) {
            this.interactionId = context.effectiveInteractionId();
            this.tenantId = context.tenantId();
            this.groupInteractionId = context.groupInteractionId();
            this.masterInteractionId = context.masterInteractionId();
            this.bundleId = context.bundleId();
            this.sourceType = context.sourceType();
            this.provenance = context.provenance();
            this.isReplay = context.isReplay();
            this.additionalMetadata = context.requestParameters();
            return this;
        }
        
        public ScoringEngineRequest build() {
            // Set processingAgent default if not provided
            if (processingAgent == null || processingAgent.isBlank()) {
                processingAgent = tenantId;
            }
            
            return new ScoringEngineRequest(
                interactionId,
                tenantId,
                groupInteractionId,
                masterInteractionId,
                bundleId,
                baseUrl,
                contentType,
                processingAgent,
                payload,
                sourceType,
                provenance,
                isReplay,
                additionalMetadata
            );
        }
    }
    
    @Override
    public String toString() {
        return "ScoringEngineRequest{" +
            "interactionId='" + interactionId + '\'' +
            ", tenantId='" + tenantId + '\'' +
            ", bundleId='" + bundleId + '\'' +
            ", baseUrl='" + baseUrl + '\'' +
            ", contentType='" + contentType + '\'' +
            ", processingAgent='" + processingAgent + '\'' +
            ", sourceType='" + sourceType + '\'' +
            ", isReplay=" + isReplay +
            ", payloadType=" + (payload != null ? payload.getClass().getSimpleName() : "null") +
            '}';
    }
}