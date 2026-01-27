package org.techbd.service.fhir;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import org.techbd.config.Configuration;
import org.techbd.config.Constants;
import org.techbd.config.CoreAppConfig;
import org.techbd.config.SourceType;
import org.techbd.exceptions.ErrorCode;
import org.techbd.exceptions.JsonValidationException;
import org.techbd.service.fhir.engine.OrchestrationEngine;
import org.techbd.service.fhir.engine.OrchestrationEngine.Device;
import org.techbd.util.AppLogger;
import org.techbd.util.TemplateLogger;
import org.techbd.util.fhir.CoreFHIRUtil;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import io.micrometer.common.util.StringUtils;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;

/**
 * Service responsible for FHIR validation operations.
 * Follows Single Responsibility Principle.
 */
@Service
public class ValidationService {
    
    private final CoreAppConfig coreAppConfig;
    private final OrchestrationEngine engine;
    private final AppLogger appLogger;
    
    private final Tracer tracer = GlobalOpenTelemetry.get().getTracer("ValidationService");
    private final TemplateLogger LOG;
    
    public ValidationService(
            CoreAppConfig coreAppConfig,
            OrchestrationEngine engine,
            AppLogger appLogger) {
        this.coreAppConfig = coreAppConfig;
        this.engine = engine;
        this.appLogger = appLogger;
        this.LOG = appLogger.getLogger(ValidationService.class);
    }
    
    /**
     * Validate JSON structure
     */
    public void validateJson(String jsonString, String interactionId) {
        final Span span = tracer.spanBuilder("ValidationService.validateJson").startSpan();
        
        try {
            try {
                Configuration.objectMapper.readTree(jsonString);
            } catch (Exception e) {
                LOG.error("Invalid JSON for interaction id: {}", interactionId, e);
                throw new JsonValidationException(ErrorCode.INVALID_JSON);
            }
        } finally {
            span.end();
        }
    }
    
    /**
     * Validate bundle profile URL
     */
    public void validateBundleProfileUrl(String jsonString, String interactionId) {
        final Span span = tracer.spanBuilder("ValidationService.validateBundleProfileUrl").startSpan();
        
        try {
            JsonNode rootNode;
            
            try {
                rootNode = Configuration.objectMapper.readTree(jsonString);
                JsonNode metaNode = rootNode.path("meta").path("profile");
                
                List<String> profileList = Optional.ofNullable(metaNode)
                    .filter(JsonNode::isArray)
                    .map(node -> StreamSupport.stream(node.spliterator(), false)
                        .map(JsonNode::asText)
                        .collect(Collectors.toList()))
                    .orElse(List.of());
                
                if (CollectionUtils.isEmpty(profileList)) {
                    LOG.error("Bundle profile not provided for interaction id: {}", interactionId);
                    throw new JsonValidationException(ErrorCode.BUNDLE_PROFILE_URL_IS_NOT_PROVIDED);
                }
                
                List<String> allowedProfileUrls = CoreFHIRUtil.getAllowedProfileUrls(coreAppConfig);
                
                if (profileList.stream().noneMatch(allowedProfileUrls::contains)) {
                    LOG.error("Invalid bundle profile URL for interaction id: {}", interactionId);
                    throw new JsonValidationException(ErrorCode.INVALID_BUNDLE_PROFILE);
                }
                
            } catch (JsonProcessingException e) {
                LOG.error("JSON processing exception while extracting profile URL for interaction id: {}", 
                    interactionId, e);
                throw new JsonValidationException(ErrorCode.INVALID_JSON);
            }
            
        } finally {
            span.end();
        }
    }
    
    /**
     * Perform full FHIR validation
     */
    public Map<String, Object> validate(
            Map<String, Object> requestParameters,
            String payload,
            String interactionId,
            String provenance,
            String sourceType) {
        
        final Span span = tracer.spanBuilder("ValidationService.validate").startSpan();
        
        try {
            var start = Instant.now();
            
            LOG.info("Validation BEGIN for interaction id: {}", interactionId);
            
            // Build validation session
            var igPackages = coreAppConfig.getIgPackages();
            var requestedIgVersion = (String) requestParameters.get(Constants.SHIN_NY_IG_VERSION);
            
            LOG.debug("Requested IG version: {}", requestedIgVersion);
            
            var sessionBuilder = engine.session()
                .withSessionId(UUID.randomUUID().toString())
                .onDevice(Device.createDefault())
                .withInteractionId(interactionId)
                .withPayloads(List.of(payload))
                .withFhirProfileUrl(CoreFHIRUtil.getBundleProfileUrl())
                .withTracer(tracer)
                .withFhirIGPackages(igPackages)
                .withRequestedIgVersion(requestedIgVersion)
                .addHapiValidationEngine();
            
            var session = sessionBuilder.build();
            
            try {
                // Execute validation
                engine.orchestrate(session);
                
                // Build result
                var immediateResult = new HashMap<>(Map.of(
                    "resourceType", "OperationOutcome",
                    "help", "If you need help understanding how to decipher OperationOutcome please see "
                        + coreAppConfig.getOperationOutcomeHelpUrl(),
                    "bundleSessionId", interactionId,
                    Constants.TECHBD_VERSION, coreAppConfig.getVersion(),
                    "isAsync", true,
                    "validationResults", session.getValidationResults(),
                    "statusUrl", "/Bundle/$status/" + interactionId,
                    "device", session.getDevice()));
                
                // Add provenance for CSV sources
                if (SourceType.CSV.name().equals(sourceType) && StringUtils.isNotEmpty(provenance)) {
                    try {
                        immediateResult.put("provenance", 
                            Configuration.objectMapper.readTree(provenance));
                    } catch (JsonProcessingException e) {
                        LOG.warn("Could not parse provenance for interaction id: {}", interactionId, e);
                    }
                }
                
                logValidationComplete(interactionId, start);
                
                return immediateResult;
                
            } catch (Exception e) {
                LOG.error("Validation FAILED for interaction id: {}", interactionId, e);
                
                return Map.of(
                    "resourceType", "OperationOutcome",
                    "interactionId", interactionId,
                    "error", "Validation failed: " + e.getMessage());
                    
            } finally {
                engine.clear(session);
            }
            
        } finally {
            span.end();
        }
    }
    
    private void logValidationComplete(String interactionId, Instant start) {
        Duration timeElapsed = Duration.between(start, Instant.now());
        LOG.info("Validation END for interaction id: {} | Time taken: {} ms",
            interactionId, timeElapsed.toMillis());
    }
}