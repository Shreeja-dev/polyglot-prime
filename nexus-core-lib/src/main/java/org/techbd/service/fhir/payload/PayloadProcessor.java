package org.techbd.service.fhir.payload;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.techbd.config.Configuration;
import org.techbd.config.Constants;
import org.techbd.config.CoreAppConfig;
import org.techbd.util.AppLogger;
import org.techbd.util.TemplateLogger;

import com.fasterxml.jackson.core.type.TypeReference;

import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;

/**
 * Service for processing and transforming FHIR payloads.
 * Centralizes payload manipulation logic with proper type safety.
 */
@Service
public class PayloadProcessor {
    
    private final CoreAppConfig coreAppConfig;
    
    private final TemplateLogger LOG;

    public PayloadProcessor(final CoreAppConfig coreAppConfig,AppLogger appLogger) {
        this.coreAppConfig = coreAppConfig;
        this.LOG = appLogger.getLogger(PayloadProcessor.class);
    }
    
    /**
     * Prepare payload by merging bundle with validation results
     */
    public Map<String, Object> preparePayloadWithValidation(
            Map<String, Object> requestParameters,
            String bundlePayload,
            Map<String, Object> payloadWithDisposition,
            String interactionId) {
        
        LOG.debug("Preparing payload with validation for interaction id: {}", interactionId);
        
        try {
            Map<String, Object> extractedOutcome = extractIssueAndDisposition(
                interactionId, payloadWithDisposition, requestParameters);
            
            if (extractedOutcome == null || extractedOutcome.isEmpty()) {
                LOG.warn("No valid outcome extracted for interaction id: {}", interactionId);
                return payloadWithDisposition;
            }
            
            Map<String, Object> bundleMap = Configuration.objectMapper.readValue(
                bundlePayload, new TypeReference<Map<String, Object>>() {});
            
            if (bundleMap == null || bundleMap.isEmpty()) {
                LOG.warn("Bundle map is empty for interaction id: {}", interactionId);
                return payloadWithDisposition;
            }
            
            Map<String, Object> result = appendToBundlePayload(interactionId, bundleMap, extractedOutcome);
            
            LOG.info("Successfully prepared payload with validation for interaction id: {}", interactionId);
            return result;
            
        } catch (Exception ex) {
            LOG.error("Error preparing payload for interaction id: {}", interactionId, ex);
            return payloadWithDisposition;
        }
    }
    
    /**
     * Extract issues and disposition from operation outcome with proper type safety
     */
    public Map<String, Object> extractIssueAndDisposition(
            String interactionId,
            Map<String, Object> operationOutcomePayload,
            Map<String, Object> requestParameters) {
        
        LOG.debug("Extracting issue and disposition for interaction id: {}", interactionId);
        
        if (operationOutcomePayload == null) {
            LOG.warn("OperationOutcome payload is null for interaction id: {}", interactionId);
            return null;
        }
        
        // Type-safe extraction of OperationOutcome
        Object operationOutcomeObj = operationOutcomePayload.get("OperationOutcome");
        if (!(operationOutcomeObj instanceof Map)) {
            LOG.warn("OperationOutcome is not a Map for interaction id: {}", interactionId);
            return null;
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> operationOutcomeMap = (Map<String, Object>) operationOutcomeObj;
        
        // Type-safe extraction of validationResults
        Object validationResultsObj = operationOutcomeMap.get("validationResults");
        if (!(validationResultsObj instanceof List)) {
            LOG.warn("validationResults is not a List for interaction id: {}", interactionId);
            return null;
        }
        
        @SuppressWarnings("unchecked")
        List<Object> validationResults = (List<Object>) validationResultsObj;
        
        if (validationResults.isEmpty()) {
            LOG.warn("validationResults is empty for interaction id: {}", interactionId);
            return null;
        }
        
        // Type-safe extraction of first validation result
        Object firstValidationResultObj = validationResults.get(0);
        if (!(firstValidationResultObj instanceof Map)) {
            LOG.warn("First validation result is not a Map for interaction id: {}", interactionId);
            return null;
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> validationResult = (Map<String, Object>) firstValidationResultObj;
        
        // Type-safe extraction of operationOutcome from validation result
        Object operationOutcomeInnerObj = validationResult.get("operationOutcome");
        Map<String, Object> operationOutcomeInner = null;
        
        if (operationOutcomeInnerObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> temp = (Map<String, Object>) operationOutcomeInnerObj;
            operationOutcomeInner = temp;
        }
        
        // Type-safe extraction of issues
        List<Map<String, Object>> issuesRaw = null;
        if (operationOutcomeInner != null) {
            Object issuesObj = operationOutcomeInner.get("issue");
            if (issuesObj instanceof List) {
                issuesRaw = castToMapList(issuesObj);
            }
        }
        
        // Filter issues by severity
        List<Map<String, Object>> filteredIssues = filterIssuesBySeverity(
            issuesRaw, requestParameters, interactionId);
        
        // Build result
        Map<String, Object> result = new HashMap<>();
        result.put("resourceType", operationOutcomeMap.get("resourceType"));
        result.put("issue", filteredIssues);
        
        // Add techByDesignDisposition if present
        Object dispositionObj = operationOutcomeMap.get("techByDesignDisposition");
        if (dispositionObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> disposition = (List<Object>) dispositionObj;
            if (!disposition.isEmpty()) {
                result.put("techByDesignDisposition", disposition);
            }
        }
        
        return result;
    }
    
    /**
     * Safely cast Object to List<Map<String, Object>>
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castToMapList(Object obj) {
        if (!(obj instanceof List)) {
            return new ArrayList<>();
        }
        
        List<?> list = (List<?>) obj;
        List<Map<String, Object>> result = new ArrayList<>();
        
        for (Object item : list) {
            if (item instanceof Map) {
                result.add((Map<String, Object>) item);
            }
        }
        
        return result;
    }
    
    /**
     * Filter issues based on severity level
     */
    private List<Map<String, Object>> filterIssuesBySeverity(
            List<Map<String, Object>> issuesRaw,
            Map<String, Object> requestParameters,
            String interactionId) {
        
        List<Map<String, Object>> filteredIssues = new ArrayList<>();
        
        if (issuesRaw == null || issuesRaw.isEmpty()) {
            return addInformationalIssue(filteredIssues, 
                getSeverityLevel(requestParameters));
        }
        
        String severityLevel = getSeverityLevel(requestParameters);
        Set<String> allowedSeverities = getAllowedSeverities(severityLevel);
        
        for (Map<String, Object> issue : issuesRaw) {
            Object severityObj = issue.get("severity");
            if (severityObj instanceof String) {
                String severity = (String) severityObj;
                if (allowedSeverities.contains(severity.toLowerCase())) {
                    filteredIssues.add(issue);
                }
            }
        }
        
        if (filteredIssues.isEmpty()) {
            return addInformationalIssue(filteredIssues, severityLevel);
        }
        
        return filteredIssues;
    }
    
    /**
     * Get severity level from request parameters or config
     */
    private String getSeverityLevel(Map<String, Object> requestParameters) {
        Object headerSeverityLevelObj = requestParameters.get(Constants.VALIDATION_SEVERITY_LEVEL);
        
        if (headerSeverityLevelObj instanceof String) {
            String headerSeverityLevel = (String) headerSeverityLevelObj;
            if (StringUtils.isNotEmpty(headerSeverityLevel)) {
                return headerSeverityLevel.toLowerCase();
            }
        }
        
        return Optional.ofNullable(coreAppConfig.getValidationSeverityLevel())
            .orElse("error")
            .toLowerCase();
    }
    
    /**
     * Get allowed severities based on level
     */
    private Set<String> getAllowedSeverities(String severityLevel) {
        return switch (severityLevel.toLowerCase()) {
            case "fatal" -> Set.of("fatal");
            case "error" -> Set.of("fatal", "error");
            case "warning" -> Set.of("fatal", "error", "warning");
            case "information" -> Set.of("fatal", "error", "warning", "information");
            default -> Set.of("fatal", "error");
        };
    }
    
    /**
     * Add informational issue when no issues match criteria
     */
    private List<Map<String, Object>> addInformationalIssue(
            List<Map<String, Object>> issues, 
            String severityLevel) {
        
        Map<String, Object> infoIssue = new HashMap<>();
        infoIssue.put("severity", "information");
        infoIssue.put("diagnostics",
            "Validation successful. No issues found at or above severity level: " + severityLevel);
        infoIssue.put("code", "informational");
        issues.add(infoIssue);
        
        return issues;
    }
    
    /**
     * Append extracted outcome to bundle payload
     */
    private Map<String, Object> appendToBundlePayload(
            String interactionId,
            Map<String, Object> payload,
            Map<String, Object> extractedOutcome) {
        
        LOG.debug("Appending to bundle payload for interaction id: {}", interactionId);
        
        if (payload == null) {
            LOG.warn("Payload is null for interaction id: {}", interactionId);
            return payload;
        }
        
        if (extractedOutcome == null || extractedOutcome.isEmpty()) {
            LOG.warn("ExtractedOutcome is null or empty for interaction id: {}", interactionId);
            return payload;
        }
        
        // Type-safe extraction of entries
        List<Map<String, Object>> entries = new ArrayList<>();
        Object entriesObj = payload.get("entry");
        
        if (entriesObj instanceof List) {
            List<Map<String, Object>> existingEntries = castToMapList(entriesObj);
            entries.addAll(existingEntries);
        } else {
            LOG.warn("'entry' field is missing or invalid for interaction id: {}", interactionId);
        }
        
        // Add new entry
        Map<String, Object> newEntry = Map.of("resource", extractedOutcome);
        entries.add(newEntry);
        
        // Create final payload
        Map<String, Object> finalPayload = new HashMap<>(payload);
        finalPayload.put("entry", List.copyOf(entries));
        
        LOG.debug("Successfully appended to bundle payload for interaction id: {}", interactionId);
        return finalPayload;
    }
    
    /**
     * Check if action is discard with proper type safety
     */
    public boolean isActionDiscard(Map<String, Object> payloadWithDisposition) {
        if (payloadWithDisposition == null) {
            return false;
        }
        
        // Type-safe extraction
        Object operationOutcomeObj = payloadWithDisposition.get("OperationOutcome");
        if (!(operationOutcomeObj instanceof Map)) {
            return false;
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> operationOutcome = (Map<String, Object>) operationOutcomeObj;
        
        Object dispositionObj = operationOutcome.get("techByDesignDisposition");
        if (!(dispositionObj instanceof List)) {
            return false;
        }
        
        @SuppressWarnings("unchecked")
        List<Object> dispositions = (List<Object>) dispositionObj;
        
        for (Object dispositionItem : dispositions) {
            if (dispositionItem instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> disposition = (Map<String, Object>) dispositionItem;
                Object actionObj = disposition.get("action");
                
                if ("discard".equals(actionObj)) {
                    return true;
                }
            }
        }
        
        return false;
    }
}