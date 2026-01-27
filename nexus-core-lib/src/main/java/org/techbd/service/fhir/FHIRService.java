package org.techbd.service.fhir;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;
import org.techbd.config.Configuration;
import org.techbd.config.Constants;
import org.techbd.config.CoreAppConfig;
import org.techbd.config.SourceType;
import org.techbd.exceptions.JsonValidationException;
import org.techbd.service.dataledger.CoreDataLedgerApiClient;
import org.techbd.service.dataledger.CoreDataLedgerApiClient.DataLedgerPayload;
import org.techbd.service.fhir.engine.OrchestrationEngine;
import org.techbd.service.fhir.http.HttpClientService;
import org.techbd.service.fhir.mtls.MTlsStrategy;
import org.techbd.service.fhir.mtls.MTlsStrategyFactory;
import org.techbd.service.fhir.payload.PayloadProcessor;
import org.techbd.util.AppLogger;
import org.techbd.util.TemplateLogger;
import org.techbd.util.fhir.CoreFHIRUtil;

import io.micrometer.common.util.StringUtils;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import jakarta.annotation.Nonnull;
import lombok.Getter;
import lombok.Setter;

/**
 * Refactored FHIR Service following SOLID principles.
 * This service now delegates to specialized services for specific concerns.
 */
@Service
@Getter
@Setter
public class FHIRService {
    
    private final TemplateLogger LOG;
    private final CoreAppConfig coreAppConfig;
    private final CoreDataLedgerApiClient coreDataLedgerApiClient;
    private final OrchestrationEngine engine;
    private final Tracer tracer;
    
    // Injected specialized services
    private final StateRegistrationService stateRegistrationService;
    private final PayloadProcessor payloadProcessor;
    private final MTlsStrategyFactory mtlsStrategyFactory;
    private final HttpClientService httpClientService;
    private final ValidationService validationService;
    
    public FHIRService (
            CoreAppConfig coreAppConfig,
            CoreDataLedgerApiClient coreDataLedgerApiClient,
            OrchestrationEngine engine,
            AppLogger appLogger,
            StateRegistrationService stateRegistrationService,
            PayloadProcessor payloadProcessor,
            MTlsStrategyFactory mtlsStrategyFactory,
            HttpClientService httpClientService,
            ValidationService validationService) {
        
        this.coreAppConfig = coreAppConfig;
        this.coreDataLedgerApiClient = coreDataLedgerApiClient;
        this.engine = engine;
        this.tracer = GlobalOpenTelemetry.get().getTracer("FHIRService");
        this.stateRegistrationService = stateRegistrationService;
        this.payloadProcessor = payloadProcessor;
        this.mtlsStrategyFactory = mtlsStrategyFactory;
        this.httpClientService = httpClientService;
        this.validationService = validationService;
        this.LOG = appLogger.getLogger(FHIRService.class);
    }
    
    /**
     * Main entry point for processing FHIR bundles
     */
    public Object processBundle(
            final @RequestBody @Nonnull String payload,
            final Map<String, Object> requestParameters,
            final Map<String, Object> responseParameters) {
        
        final Span span = tracer.spanBuilder("FHIRService.processBundle").startSpan();
        
        try {
            final var start = Instant.now();
            
            // Extract and validate parameters
            BundleProcessingContext context = createProcessingContext(payload, requestParameters);
            
            LOG.info("Bundle processing start for interaction id: {}", context.interactionId());
            
            // Record data ledger if not CSV/CCDA/HL7V2
            if (!isNonFhirSource(context.source())) {
                recordDataLedger(context);
            }
            
            // Register original payload (unless health check)
            if (!context.isHealthCheck()) {
                stateRegistrationService.registerOriginalPayload(
                    requestParameters, payload, context.interactionId(),
                    context.groupInteractionId(), context.masterInteractionId(),
                    context.source(), context.requestUriToBeOverriden(), context.coRrelationId());
            }
            
            // Validate and process
            Map<String, Object> payloadWithDisposition = null;
            
            try {
                validationService.validateJson(payload, context.interactionId());
                validationService.validateBundleProfileUrl(payload, context.interactionId());
                
                String dataLakeApiContentType = Optional.ofNullable(context.dataLakeApiContentType())
                    .orElse(MediaType.APPLICATION_JSON_VALUE);
                
                Map<String, Object> immediateResult = validationService.validate(
                    requestParameters, payload, context.interactionId(), 
                    context.provenance(), context.source());
                
                Map<String, Object> result = Map.of("OperationOutcome", immediateResult);
                
                // Register validation results (unless health check)
                if (!context.isHealthCheck()) {
                    payloadWithDisposition = stateRegistrationService.registerValidationResults(
                        requestParameters, result, context.interactionId(),
                        context.groupInteractionId(), context.masterInteractionId(),
                        context.source(), context.requestUriToBeOverriden());
                }
                
                // Return early if validation-only or health check
                if (isValidationOnlyRequest(context.requestUri()) || context.isHealthCheck()) {
                    logProcessingComplete(context.interactionId(), start);
                    return result;
                }
                
                // Check for discard action
                if (payloadWithDisposition != null && payloadProcessor.isActionDiscard(payloadWithDisposition)) {
                    LOG.info("Action discard detected for interaction id: {}", context.interactionId());
                    logProcessingComplete(context.interactionId(), start);
                    return payloadWithDisposition;
                }
                
                // Send to scoring engine
                sendToScoringEngine(context, payload, payloadWithDisposition, false, null);
                
                logProcessingComplete(context.interactionId(), start);
                return payloadWithDisposition != null ? payloadWithDisposition : result;
                
            } catch (JsonValidationException ex) {
                payloadWithDisposition = stateRegistrationService.registerValidationResults(
                    requestParameters, buildOperationOutcome(ex, context.interactionId()),
                    context.interactionId(), context.groupInteractionId(),
                    context.masterInteractionId(), context.source(), context.requestUriToBeOverriden());
                
                LOG.info("Validation exception for interaction id: {}", context.interactionId(), ex);
            }
            
            logProcessingComplete(context.interactionId(), start);
            return payloadWithDisposition;
            
        } finally {
            span.end();
        }
    }
    
    /**
     * Send payload to scoring engine
     */
    public void sendToScoringEngine(
            BundleProcessingContext context,
            String payload,
            Map<String, Object> validationPayloadWithDisposition,
            boolean replay,
            Map<String, Object> replayPayload) {
        
        final Span span = tracer.spanBuilder("FhirService.sentToScoringEngine").startSpan();
        
        try {
            LOG.info("sendToScoringEngine BEGIN | interaction id: {} | replay: {}", 
                context.interactionId(), replay);
            
            // Prepare payload
            Map<String, Object> bundlePayloadWithDisposition = preparePayloadForScoring(
                context, payload, validationPayloadWithDisposition, replay, replayPayload);
            
            // Get Data Lake API URL
            String dataLakeApiBaseURL = Optional.ofNullable(context.customDataLakeApi())
                .filter(s -> !s.isEmpty())
                .orElse(coreAppConfig.getDefaultDatalakeApiUrl());
            
            // Get mTLS strategy
            String strategyName = determineMTlsStrategy(context);
            
            // Execute strategy
            executeMTlsStrategy(context, payload, bundlePayloadWithDisposition, 
                dataLakeApiBaseURL, strategyName, replay);
            
        } catch (Exception e) {
            LOG.error("Error in sendToScoringEngine for interaction id: {}", 
                context.interactionId(), e);
        } finally {
            span.end();
        }
    }
    
    /**
     * Execute mTLS strategy and send request
     */
    private void executeMTlsStrategy(
            BundleProcessingContext context,
            String payload,
            Map<String, Object> bundlePayloadWithDisposition,
            String dataLakeApiBaseURL,
            String strategyName,
            boolean replay) {
        
        try {
            // Get strategy
            MTlsStrategy strategy = mtlsStrategyFactory.getStrategy(strategyName);
            
            // Create strategy context
            MTlsStrategy.MTlsContext mtlsContext = new MTlsStrategy.MTlsContext(
                context.interactionId(),
                context.tenantId(),
                dataLakeApiBaseURL,
                context.dataLakeApiContentType(),
                bundlePayloadWithDisposition,
                payload,
                context.provenance(),
                context.requestUri(),
                context.groupInteractionId(),
                context.masterInteractionId(),
                context.source(),
                context.bundleId(),
                context.requestParameters(),
                replay
            );
            
            // Execute strategy
            MTlsStrategy.MTlsResult result = strategy.execute(mtlsContext);
            
            if (!result.success()) {
                LOG.error("mTLS strategy failed for interaction id: {} - {}", 
                    context.interactionId(), result.message());
                // Register failure
                stateRegistrationService.registerStateFailed(
                    context.interactionId(),
                    context.requestUri(),
                    context.tenantId(),
                    result.message(),
                    context.provenance(),
                    context.groupInteractionId(),
                    context.masterInteractionId(),
                    context.source(),
                    context.requestParameters(),
                    replay);
                return;
            }
            
            // Register forward state
            stateRegistrationService.registerStateForward(
                context.provenance(),
                context.interactionId(),
                context.requestUri(),
                context.tenantId(),
                bundlePayloadWithDisposition,
                payload,
                context.groupInteractionId(),
                context.masterInteractionId(),
                context.source(),
                replay);
            
            // Send HTTP request
            HttpClientService.HttpRequestContext httpContext = new HttpClientService.HttpRequestContext(
                result.webClient(),
                context.tenantId(),
                bundlePayloadWithDisposition,
                payload,
                context.dataLakeApiContentType(),
                context.interactionId(),
                context.provenance(),
                context.requestUri(),
                dataLakeApiBaseURL,
                context.groupInteractionId(),
                context.masterInteractionId(),
                context.source(),
                context.bundleId(),
                context.requestParameters(),
                replay,
                determineApiKeyAuth(context)
            );
            
            httpClientService.sendPostRequest(httpContext);
            
        } catch (Exception e) {
            LOG.error("Error executing mTLS strategy for interaction id: {}", 
                context.interactionId(), e);
            
            stateRegistrationService.registerStateFailure(
                context.interactionId(),
                context.requestUri(),
                context.tenantId(),
                e,
                dataLakeApiBaseURL,
                context.provenance(),
                context.groupInteractionId(),
                context.masterInteractionId(),
                context.source(),
                context.requestParameters(),
                replay);
        }
    }
    
    // ========== Helper Methods ==========
    
    private BundleProcessingContext createProcessingContext(
            String payload, 
            Map<String, Object> requestParameters) {
        
        String interactionId = (String) requestParameters.get(Constants.INTERACTION_ID);
        String correlationId = (String) requestParameters.get(Constants.CORRELATION_ID);
        
        if (correlationId != null) {
            interactionId = correlationId;
        }
        
        String tenantId = (String) requestParameters.get(Constants.TENANT_ID);
        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant ID must be provided in request headers");
        }
        
        if (interactionId == null) {
            throw new IllegalArgumentException("Interaction ID must be provided in request parameters");
        }
        
        String bundleId = CoreFHIRUtil.extractBundleId(payload, tenantId);
        
        return new BundleProcessingContext(
            interactionId,
            tenantId,
            (String) requestParameters.get(Constants.SOURCE_TYPE),
            (String) requestParameters.get(Constants.DATA_LAKE_API_CONTENT_TYPE),
            (String) requestParameters.get(Constants.CUSTOM_DATA_LAKE_API),
            (String) requestParameters.get(Constants.HEALTH_CHECK),
            (String) requestParameters.get(Constants.PROVENANCE),
            (String) requestParameters.get(Constants.MTLS_STRATEGY),
            (String) requestParameters.get(Constants.GROUP_INTERACTION_ID),
            (String) requestParameters.get(Constants.MASTER_INTERACTION_ID),
            (String) requestParameters.get(Constants.OVERRIDE_REQUEST_URI),
            (String) requestParameters.get(Constants.CORRELATION_ID),
            (String) requestParameters.get(Constants.REQUEST_URI),
            bundleId,
            requestParameters
        );
    }
    
    private record BundleProcessingContext(
        String interactionId,
        String tenantId,
        String source,
        String dataLakeApiContentType,
        String customDataLakeApi,
        String healthCheckStr,
        String provenance,
        String mtlsStrategy,
        String groupInteractionId,
        String masterInteractionId,
        String requestUriToBeOverriden,
        String coRrelationId,
        String requestUri,
        String bundleId,
        Map<String, Object> requestParameters
    ) {
        boolean isHealthCheck() {
            return "true".equalsIgnoreCase(healthCheckStr != null ? healthCheckStr.trim() : null);
        }
    }
    
    private boolean isNonFhirSource(String source) {
        return SourceType.CSV.name().equalsIgnoreCase(source)
            || SourceType.CCDA.name().equalsIgnoreCase(source)
            || SourceType.HL7V2.name().equalsIgnoreCase(source);
    }
    
    private boolean isValidationOnlyRequest(String requestUri) {
        return StringUtils.isNotEmpty(requestUri)
            && (requestUri.equals("/Bundle/$validate") || requestUri.equals("/Bundle/$validate/"));
    }
    
    private void recordDataLedger(BundleProcessingContext context) {
        DataLedgerPayload dataLedgerPayload;
        
        if (StringUtils.isNotEmpty(context.bundleId())) {
            dataLedgerPayload = DataLedgerPayload.create(
                CoreDataLedgerApiClient.Actor.TECHBD.getValue(),
                CoreDataLedgerApiClient.Action.RECEIVED.getValue(),
                CoreDataLedgerApiClient.Actor.TECHBD.getValue(),
                context.bundleId());
        } else {
            dataLedgerPayload = DataLedgerPayload.create(
                CoreDataLedgerApiClient.Actor.TECHBD.getValue(),
                CoreDataLedgerApiClient.Action.RECEIVED.getValue(),
                CoreDataLedgerApiClient.Actor.TECHBD.getValue(),
                context.interactionId());
        }
        
        String dataLedgerProvenance = String.format("%s.processBundle", 
            FHIRServiceRefactored.class.getName());
        
        coreDataLedgerApiClient.processRequest(dataLedgerPayload,
            context.interactionId(), dataLedgerProvenance,
            SourceType.FHIR.name(), null);
    }
    
    private Map<String, Object> preparePayloadForScoring(
            BundleProcessingContext context,
            String payload,
            Map<String, Object> validationPayloadWithDisposition,
            boolean replay,
            Map<String, Object> replayPayload) {
        
        if (replay) {
            return replayPayload;
        }
        
        if (validationPayloadWithDisposition != null) {
            return payloadProcessor.preparePayloadWithValidation(
                context.requestParameters(), payload, 
                validationPayloadWithDisposition, context.interactionId());
        }
        
        try {
            return Configuration.objectMapper.readValue(payload,
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            LOG.error("Error parsing payload for interaction id: {}", 
                context.interactionId(), e);
            return validationPayloadWithDisposition;
        }
    }
    
    private String determineMTlsStrategy(BundleProcessingContext context) {
        // Priority: explicit strategy > config default > no-mTls
        if (StringUtils.isNotEmpty(context.mtlsStrategy())) {
            return context.mtlsStrategy();
        }
        
        var defaultAuthn = coreAppConfig.getDefaultDataLakeApiAuthn();
        if (defaultAuthn != null && StringUtils.isNotEmpty(defaultAuthn.mTlsStrategy())) {
            return defaultAuthn.mTlsStrategy();
        }
        
        return "no-mTls";
    }
    
    private CoreAppConfig.WithApiKeyAuth determineApiKeyAuth(BundleProcessingContext context) {
        var defaultAuthn = coreAppConfig.getDefaultDataLakeApiAuthn();
        
        if (defaultAuthn != null && "with-api-key-auth".equalsIgnoreCase(context.mtlsStrategy())) {
            return defaultAuthn.withApiKeyAuth();
        }
        
        return null;
    }
    
    private Map<String, Object> buildOperationOutcome(
            JsonValidationException ex, 
            String interactionId) {
        
        var validationResult = Map.of(
            "valid", false,
            "issues", List.of(Map.of(
                "message", ex.getErrorCode() + ": " + ex.getMessage(),
                "severity", "FATAL")));
        
        var immediateResult = Map.of(
            "resourceType", "OperationOutcome",
            "bundleSessionId", interactionId,
            Constants.TECHBD_VERSION, coreAppConfig.getVersion(),
            "validationResults", List.of(validationResult));
        
        return Map.of("OperationOutcome", immediateResult);
    }
    
    private void logProcessingComplete(String interactionId, Instant start) {
        Duration timeElapsed = Duration.between(start, Instant.now());
        LOG.info("Bundle processing end for interaction id: {} | Time taken: {} ms",
            interactionId, timeElapsed.toMillis());
    }
}