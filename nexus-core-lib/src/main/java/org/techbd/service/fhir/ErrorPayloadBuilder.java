package org.techbd.service.fhir;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.core.NestedExceptionUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.techbd.config.Configuration;
import org.techbd.util.AppLogger;
import org.techbd.util.TemplateLogger;

import com.fasterxml.jackson.databind.JsonNode;

@Component
public class ErrorPayloadBuilder {
    private final TemplateLogger LOG;

    public ErrorPayloadBuilder(AppLogger appLogger) {
        this.LOG = appLogger.getLogger(ErrorPayloadBuilder.class);
    }

    
    /**
     * Build comprehensive error map from throwable
     */
    public  Map<String, Object> buildErrorMap(
            Throwable error, 
            String dataLakeApiBaseURL, 
            String tenantId) {
        
        var rootCauseThrowable = NestedExceptionUtils.getRootCause(error);
        var rootCause = rootCauseThrowable != null ? rootCauseThrowable.toString() : "null";
        var mostSpecificCause = NestedExceptionUtils.getMostSpecificCause(error).toString();
        
        var errorMap = new HashMap<String, Object>();
        errorMap.put("dataLakeApiBaseURL", dataLakeApiBaseURL);
        errorMap.put("error", error.toString());
        errorMap.put("message", error.getMessage());
        errorMap.put("rootCause", rootCause);
        errorMap.put("mostSpecificCause", mostSpecificCause);
        errorMap.put("tenantId", tenantId);
        
        // Add WebClient specific details if applicable
        if (error instanceof WebClientResponseException webClientResponseException) {
            enrichWithWebClientDetails(errorMap, webClientResponseException);
        }
        
        return errorMap;
    }
    
    /**
     * Enrich error map with WebClient response details
     */
    private  void enrichWithWebClientDetails(
            Map<String, Object> errorMap, 
            WebClientResponseException exception) {
        
        String responseBody = exception.getResponseBodyAsString();
        errorMap.put("responseBody", responseBody);
        errorMap.put("statusCode", exception.getStatusCode().value());
        errorMap.put("statusText", exception.getStatusText());
        
        // Extract bundle ID if present
        String bundleId = extractBundleId(responseBody);
        if (bundleId != null) {
            errorMap.put("bundleId", bundleId);
        }
        
        // Add response headers
        var responseHeaders = exception.getHeaders()
            .entrySet()
            .stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> String.join(",", entry.getValue())));
        errorMap.put("headers", responseHeaders);
    }
    
    /**
     * Extract bundle ID from response body if present
     */
    private  String extractBundleId(String responseBody) {
        try {
            JsonNode rootNode = Configuration.objectMapper.readTree(responseBody);
            JsonNode bundleIdNode = rootNode.path("bundle_id");
            
            if (!bundleIdNode.isMissingNode()) {
                return bundleIdNode.asText();
            }
        } catch (Exception e) {
            LOG.debug("Could not extract bundle_id from response body", e);
        }
        return null;
    }
}