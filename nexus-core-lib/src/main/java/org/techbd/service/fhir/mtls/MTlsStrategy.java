package org.techbd.service.fhir.mtls;

import java.util.Map;

import org.springframework.web.reactive.function.client.WebClient;

/**
 * Strategy interface for different mTLS authentication approaches.
 * Follows Open/Closed Principle - new strategies can be added without modifying existing code.
 */
public interface MTlsStrategy {
    
    /**
     * Context object containing all necessary information for mTLS processing
     */
    record MTlsContext(
        String interactionId,
        String tenantId,
        String dataLakeApiBaseURL,
        String dataLakeApiContentType,
        Map<String, Object> bundlePayloadWithDisposition,
        String originalPayload,
        String provenance,
        String requestURI,
        String groupInteractionId,
        String masterInteractionId,
        String sourceType,
        String bundleId,
        Map<String, Object> requestParameters,
        boolean replay
    ) {}
    
    /**
     * Result of mTLS strategy execution
     */
    record MTlsResult(
        boolean success,
        String message,
        WebClient webClient
    ) {
        public static MTlsResult success(WebClient webClient) {
            return new MTlsResult(true, null, webClient);
        }
        
        public static MTlsResult failure(String message) {
            return new MTlsResult(false, message, null);
        }
    }
    
    /**
     * Execute the mTLS strategy and return configured WebClient
     * 
     * @param context The context containing all necessary information
     * @return MTlsResult containing success status and WebClient if successful
     */
    MTlsResult execute(MTlsContext context);
    
    /**
     * Get the strategy name/identifier
     */
    String getStrategyName();
}