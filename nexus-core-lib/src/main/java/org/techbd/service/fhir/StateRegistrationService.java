package org.techbd.service.fhir;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MimeTypeUtils;
import org.techbd.config.Configuration;
import org.techbd.config.Constants;
import org.techbd.config.Nature;
import org.techbd.config.State;
import org.techbd.udi.auto.jooq.ingress.routines.RegisterInteractionFhirRequest;
import org.techbd.util.AppLogger;
import org.techbd.util.TemplateLogger;
import org.techbd.util.fhir.CoreFHIRUtil;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;

/**
 * Service responsible for registering FHIR interaction states in the database.
 * Follows Single Responsibility Principle by handling only state registration.
 */
@Service
public class StateRegistrationService {
    
    private final DSLContext primaryDSLContext;
    private final AppLogger appLogger;
    private final String techbdVersion;
    private final ErrorPayloadBuilder errorPayloadBuilder;
    private final TemplateLogger LOG;
    
    public StateRegistrationService(
            DSLContext primaryDSLContext,
            AppLogger appLogger,
            String techbdVersion) {
        this.primaryDSLContext = primaryDSLContext;
        this.appLogger = appLogger;
        this.techbdVersion = techbdVersion;
        this.LOG = appLogger.getLogger(StateRegistrationService.class);
        this.errorPayloadBuilder = new ErrorPayloadBuilder(appLogger);
    }
    /**
     * Base configuration for all registration operations
     */
    private static class RegistrationContext {
        String interactionId;
        String groupInteractionId;
        String masterInteractionId;
        String requestURI;
        String tenantId;
        String sourceType;
        String provenance;
        Nature nature;
        State fromState;
        State toState;
        JsonNode payload;
        boolean isReplay;
        Map<String, Object> requestParameters;
        
        RegistrationContext(String interactionId, String tenantId) {
            this.interactionId = interactionId;
            this.tenantId = tenantId;
            this.requestParameters = new HashMap<>();
        }
    }
    
    /**
     * Register original payload state
     */
    @Transactional
    public void registerOriginalPayload(
            Map<String, Object> requestParameters,
            String payload,
            String interactionId,
            String groupInteractionId,
            String masterInteractionId,
            String sourceType,
            String requestUriToBeOverriden,
            String provenance) {
        
        LOG.info("REGISTER Original Payload BEGIN for interaction id: {}", interactionId);
        
        RegistrationContext context = new RegistrationContext(interactionId, 
            (String) requestParameters.get(Constants.TENANT_ID));
        context.groupInteractionId = groupInteractionId;
        context.masterInteractionId = masterInteractionId;
        context.sourceType = sourceType;
        context.requestURI = getRequestURI(requestParameters, requestUriToBeOverriden);
        context.provenance = provenance;
        context.nature = Nature.ORIGINAL_FHIR_PAYLOAD;
        context.fromState = State.NONE;
        context.toState = State.ACCEPT_FHIR_BUNDLE;
        context.requestParameters = requestParameters;
        
        try {
            context.payload = Configuration.objectMapper.readTree(payload);
        } catch (JsonProcessingException e) {
            LOG.error("Invalid JSON format. Storing raw payload. Error: {} for interactionID: {}", 
                e.getMessage(), interactionId, e);
            context.payload = Configuration.objectMapper.valueToTree(payload);
        }
        
        executeRegistration(context, "Original Payload");
    }
    
    /**
     * Register validation results state
     */
    @Transactional
    public Map<String, Object> registerValidationResults(
            Map<String, Object> requestParameters,
            Map<String, Object> immediateResult,
            String interactionId,
            String groupInteractionId,
            String masterInteractionId,
            String sourceType,
            String requestUriToBeOverriden,
            String provenance) {
        
        LOG.info("REGISTER Validation Results BEGIN for interaction id: {}", interactionId);
        
        RegistrationContext context = new RegistrationContext(interactionId,
            (String) requestParameters.get(Constants.TENANT_ID));
        context.groupInteractionId = groupInteractionId;
        context.masterInteractionId = masterInteractionId;
        context.sourceType = sourceType;
        context.requestURI = getRequestURI(requestParameters, requestUriToBeOverriden);
        context.provenance = provenance;
        context.nature = Nature.TECH_BY_DISPOSITION;
        context.fromState = State.ACCEPT_FHIR_BUNDLE;
        context.toState = State.DISPOSITION;
        context.payload = Configuration.objectMapper.valueToTree(immediateResult);
        context.requestParameters = requestParameters;
        
        JsonNode response = executeRegistration(context, "Validation Results");
        
        if (response != null) {
            Map<String, Object> responseAttributes = CoreFHIRUtil.extractFields(response);
            JsonNode jsonNode = (JsonNode) responseAttributes.get(Constants.KEY_PAYLOAD);
            return Configuration.objectMapper.convertValue(jsonNode, 
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        }
        return null;
    }
    
    /**
     * Register forward state
     */
    @Transactional
    public void registerStateForward(
            String provenance,
            String interactionId,
            String requestURI,
            String tenantId,
            Map<String, Object> payloadWithDisposition,
            String payload,
            String groupInteractionId,
            String masterInteractionId,
            String sourceType,
            boolean replay) {
        
        LOG.info("{} BEGIN for interaction id: {}", 
            replay ? "FHIR-REPLAY" : "REGISTER State Forward", interactionId);
        
        RegistrationContext context = new RegistrationContext(interactionId, tenantId);
        context.groupInteractionId = groupInteractionId;
        context.masterInteractionId = masterInteractionId;
        context.sourceType = sourceType;
        context.requestURI = requestURI;
        context.provenance = buildProvenance(provenance, tenantId);
        context.isReplay = replay;
        
        if (replay) {
            context.nature = Nature.FORWARDED_HTTP_REQUEST_REPLAY;
            context.fromState = State.FAIL;
            context.toState = State.FORWARD;
        } else {
            context.nature = Nature.FORWARD_HTTP_REQUEST;
            context.fromState = State.DISPOSITION;
            context.toState = State.FORWARD;
        }
        
        context.payload = Configuration.objectMapper.valueToTree(payloadWithDisposition);
        
        executeRegistration(context, replay ? "FHIR-REPLAY State Forward" : "State Forward");
    }
    
    /**
     * Register complete state
     */
    @Transactional
    public void registerStateComplete(
            String interactionId,
            String requestURI,
            String tenantId,
            String response,
            String provenance,
            String groupInteractionId,
            String masterInteractionId,
            String sourceType,
            Map<String, Object> requestParameters,
            boolean replay) {
        
        LOG.info("REGISTER State Complete BEGIN for interaction id: {}", interactionId);
        
        RegistrationContext context = new RegistrationContext(interactionId, tenantId);
        context.groupInteractionId = groupInteractionId;
        context.masterInteractionId = masterInteractionId;
        context.sourceType = sourceType;
        context.requestURI = requestURI;
        context.provenance = provenance;
        context.isReplay = replay;
        context.fromState = State.FORWARD;
        context.toState = State.COMPLETE;
        context.requestParameters = requestParameters;
        
        if (replay) {
            context.nature = Nature.FORWARDED_HTTP_RESPONSE_REPLAY;
        } else {
            context.nature = Nature.FORWARDED_HTTP_RESPONSE;
            requestParameters.put(Constants.OBSERVABILITY_METRIC_INTERACTION_FINISH_TIME, 
                Instant.now().toString());
        }
        
        context.payload = parseResponsePayload(response, interactionId);
        
        executeRegistration(context, "State Complete");
    }
    
    /**
     * Register failed state
     */
    @Transactional
    public void registerStateFailed(
            String interactionId,
            String requestURI,
            String tenantId,
            String response,
            String provenance,
            String groupInteractionId,
            String masterInteractionId,
            String sourceType,
            Map<String, Object> requestParameters,
            boolean replay) {
        
        LOG.info("{} BEGIN for interaction id: {}", 
            replay ? "FHIR-REPLAY State Fail" : "REGISTER State Fail", interactionId);
        
        RegistrationContext context = new RegistrationContext(interactionId, tenantId);
        context.groupInteractionId = groupInteractionId;
        context.masterInteractionId = masterInteractionId;
        context.sourceType = sourceType;
        context.requestURI = requestURI;
        context.provenance = provenance;
        context.isReplay = replay;
        context.fromState = State.FORWARD;
        context.toState = State.FAIL;
        context.requestParameters = requestParameters;
        
        if (replay) {
            context.nature = Nature.FORWARDED_HTTP_RESPONSE_REPLAY_ERROR;
        } else {
            context.nature = Nature.FORWARDED_HTTP_RESPONSE_ERROR;
            requestParameters.put(Constants.OBSERVABILITY_METRIC_INTERACTION_FINISH_TIME,
                Instant.now().toString());
        }
        
        context.payload = parseResponsePayload(response, interactionId);
        
        executeRegistration(context, replay ? "FHIR-REPLAY State Fail" : "State Fail");
    }
    
    /**
     * Register failure with error details
     */
    @Transactional
    public void registerStateFailure(
            String interactionId,
            String requestURI,
            String tenantId,
            Throwable error,
            String dataLakeApiBaseURL,
            String provenance,
            String groupInteractionId,
            String masterInteractionId,
            String sourceType,
            Map<String, Object> requestParameters,
            boolean replay) {
        
        LOG.error("{} Exception for interaction id: {}", 
            replay ? "FHIR-REPLAY" : "REGISTER State Failure", interactionId, error);
        
        RegistrationContext context = new RegistrationContext(interactionId, tenantId);
        context.groupInteractionId = groupInteractionId;
        context.masterInteractionId = masterInteractionId;
        context.sourceType = sourceType;
        context.requestURI = requestURI;
        context.provenance = provenance;
        context.isReplay = replay;
        context.fromState = State.FORWARD;
        context.toState = State.FAIL;
        context.requestParameters = requestParameters;
        
        if (replay) {
            context.nature = Nature.FORWARDED_HTTP_RESPONSE_REPLAY_ERROR;
        } else {
            context.nature = Nature.FORWARDED_HTTP_RESPONSE_ERROR;
            requestParameters.put(Constants.OBSERVABILITY_METRIC_INTERACTION_FINISH_TIME,
                Instant.now().toString());
        }
        
        context.payload = Configuration.objectMapper.valueToTree(
            errorPayloadBuilder.buildErrorMap(error, dataLakeApiBaseURL, tenantId));
        
        executeRegistration(context, replay ? "FHIR-REPLAY State Failure" : "State Failure");
    }
    
    /**
     * Core registration execution method - Template Method Pattern
     */
    private JsonNode executeRegistration(RegistrationContext context, String operationType) {
        var jooqCfg = primaryDSLContext.configuration();
        var rihr = new RegisterInteractionFhirRequest();
        
        try {
            var start = Instant.now();
            
            // Configure the request
            configureRequest(rihr, context);
            
            // Execute
            int execResult = rihr.execute(jooqCfg);
            var end = Instant.now();
            
            // Log results
            JsonNode response = rihr.getReturnValue();
            logExecutionResult(context, response, operationType, start, end, execResult);
            
            return response;
            
        } catch (Exception e) {
            LOG.error("ERROR:: REGISTER {} for interaction id: {} tenant id: {}", 
                operationType, context.interactionId, context.tenantId, e);
            return null;
        }
    }
    
    /**
     * Configure the JOOQ request object
     */
    private void configureRequest(RegisterInteractionFhirRequest rihr, RegistrationContext context) {
        rihr.setPInteractionId(context.interactionId);
        rihr.setPGroupHubInteractionId(context.groupInteractionId);
        rihr.setPSourceHubInteractionId(context.masterInteractionId);
        rihr.setPInteractionKey(context.requestURI);
        rihr.setPContentType(MimeTypeUtils.APPLICATION_JSON_VALUE);
        rihr.setPPayload(context.payload);
        rihr.setPSourceType(context.sourceType);
        rihr.setPCreatedAt(OffsetDateTime.now());
        rihr.setPCreatedBy(StateRegistrationService.class.getName());
        rihr.setPProvenance(context.provenance);
        rihr.setPFromState(context.fromState.name());
        rihr.setPToState(context.toState.name());
        rihr.setPTechbdVersionNumber(techbdVersion);
        
        rihr.setPNature((JsonNode)Configuration.objectMapper.valueToTree(
            Map.of("nature", context.nature.getDescription(), "tenant_id", context.tenantId)));
        
        if (!context.isReplay && context.requestParameters != null && !context.requestParameters.isEmpty()) {
            rihr.setPAdditionalDetails((JsonNode)Configuration.objectMapper.valueToTree(
                Map.of("request", context.requestParameters)));
        }
        
        // Handle elaboration if present
        if (context.requestParameters != null && context.requestParameters.get(Constants.ELABORATION) != null) {
            try {
                JsonNode elaborationNode = Configuration.objectMapper.readTree(
                    (String) context.requestParameters.get(Constants.ELABORATION));
                rihr.setPElaboration(elaborationNode);
            } catch (JsonProcessingException e) {
                LOG.error("Invalid elaboration JSON for interactionID: {}", context.interactionId, e);
            }
        }
        
        // Set user details
        setUserDetails(rihr, context.requestParameters);
    }
    
    /**
     * Set user details on the request
     */
    private void setUserDetails(RegisterInteractionFhirRequest rihr, Map<String, Object> requestParameters) {
        if (requestParameters == null) {
            requestParameters = new HashMap<>();
        }
        
        rihr.setPUserName(requestParameters.get(Constants.USER_NAME) != null 
            ? (String) requestParameters.get(Constants.USER_NAME) 
            : Constants.DEFAULT_USER_NAME);
        rihr.setPUserId(requestParameters.get(Constants.USER_ID) != null 
            ? (String) requestParameters.get(Constants.USER_ID) 
            : Constants.DEFAULT_USER_ID);
        rihr.setPUserSession(java.util.UUID.randomUUID().toString());
        rihr.setPUserRole(requestParameters.get(Constants.USER_ROLE) != null 
            ? (String) requestParameters.get(Constants.USER_ROLE) 
            : Constants.DEFAULT_USER_ROLE);
    }
    
    /**
     * Log execution results
     */
    private void logExecutionResult(RegistrationContext context, JsonNode response, 
            String operationType, Instant start, Instant end, int execResult) {
        
        Map<String, Object> responseAttributes = CoreFHIRUtil.extractFields(response);
        
        LOG.info(
            "REGISTER {} END for interaction id: {} tenant id: {}. " +
            "Time taken: {} ms | error: {}, hub_nexus_interaction_id: {} | execResult: {}",
            operationType,
            context.interactionId,
            context.tenantId,
            Duration.between(start, end).toMillis(),
            responseAttributes.getOrDefault(Constants.KEY_ERROR, "N/A"),
            responseAttributes.getOrDefault(Constants.KEY_HUB_NEXUS_INTERACTION_ID, "N/A"),
            execResult);
    }
    
    /**
     * Get request URI with override support
     */
    private String getRequestURI(Map<String, Object> requestParameters, String requestUriToBeOverriden) {
        return StringUtils.isNotEmpty(requestUriToBeOverriden) 
            ? requestUriToBeOverriden 
            : (String) requestParameters.get(Constants.REQUEST_URI);
    }
    
    /**
     * Build provenance JSON
     */
    private String buildProvenance(String provenance, String tenantId) {
        try {
            Map<String, Object> provenanceMap = Map.of(
                "provenance", provenance,
                "processingAgent", tenantId);
            return Configuration.objectMapper.writeValueAsString(provenanceMap);
        } catch (Exception ex) {
            LOG.error("Failed to construct provenance JSON", ex);
            return provenance;
        }
    }
    
    /**
     * Parse response payload with error handling
     */
    private JsonNode parseResponsePayload(String response, String interactionId) {
        try {
            return Configuration.objectMapper.readTree(response);
        } catch (JsonProcessingException jpe) {
            LOG.warn("Response is not JSON for interaction {}, storing as string", interactionId);
            return Configuration.objectMapper.valueToTree(response);
        }
    }
}