package org.techbd.service.fhir.authentication;

import java.util.Map;
import org.techbd.service.fhir.model.ScoringEngineRequest;
import org.techbd.service.fhir.model.ScoringEngineResponse;

/**
 * Strategy interface for different authentication mechanisms
 * when sending data to the scoring engine.
 * 
 * This follows the Strategy Pattern and Open/Closed Principle.
 */
public interface AuthenticationStrategy {
    
    /**
     * Sends the payload to the scoring engine using the specific
     * authentication mechanism implemented by this strategy.
     * 
     * @param request The scoring engine request containing all necessary data
     * @return Response from the scoring engine
     * @throws ScoringEngineException if the request fails
     */
    ScoringEngineResponse sendToScoringEngine(ScoringEngineRequest request);
    
    /**
     * Returns the name/identifier of this strategy
     */
    String getStrategyName();
    
    /**
     * Validates that the strategy can be used with the current configuration
     * @throws IllegalStateException if configuration is invalid
     */
    void validateConfiguration();
}