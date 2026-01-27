package org.techbd.service.fhir.mtls;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;

/**
 * Factory for creating mTLS strategy instances.
 * Follows Open/Closed Principle - new strategies can be registered without modifying this class.
 */
@Component
@RequiredArgsConstructor
public class MTlsStrategyFactory {
    
    // Spring will inject all MTlsStrategy implementations
    private final java.util.List<MTlsStrategy> strategies;
    
    /**
     * Get strategy by name
     * 
     * @param strategyName The name of the strategy
     * @return The strategy implementation
     * @throws IllegalArgumentException if strategy not found
     */
    public MTlsStrategy getStrategy(String strategyName) {
        if (StringUtils.isEmpty(strategyName)) {
            throw new IllegalArgumentException("Strategy name cannot be null or empty");
        }
        
        return strategies.stream()
            .filter(strategy -> strategy.getStrategyName().equalsIgnoreCase(strategyName))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                String.format("No mTLS strategy found for name: %s. Available strategies: %s",
                    strategyName, getAvailableStrategyNames())));
    }
    
    /**
     * Get all available strategy names
     */
    public String getAvailableStrategyNames() {
        return strategies.stream()
            .map(MTlsStrategy::getStrategyName)
            .collect(Collectors.joining(", "));
    }
    
    /**
     * Check if a strategy exists
     */
    public boolean hasStrategy(String strategyName) {
        if (StringUtils.isEmpty(strategyName)) {
            return false;
        }
        
        return strategies.stream()
            .anyMatch(strategy -> strategy.getStrategyName().equalsIgnoreCase(strategyName));
    }
    
    /**
     * Get all registered strategies
     */
    public Map<String, MTlsStrategy> getAllStrategies() {
        return strategies.stream()
            .collect(Collectors.toMap(
                MTlsStrategy::getStrategyName,
                strategy -> strategy));
    }
}