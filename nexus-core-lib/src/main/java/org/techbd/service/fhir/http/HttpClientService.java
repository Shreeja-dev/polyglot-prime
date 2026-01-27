package org.techbd.service.fhir.http;

import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.techbd.config.Constants;
import org.techbd.config.CoreAppConfig.WithApiKeyAuth;
import org.techbd.service.dataledger.CoreDataLedgerApiClient;
import org.techbd.service.dataledger.CoreDataLedgerApiClient.DataLedgerPayload;
import org.techbd.service.fhir.StateRegistrationService;
import org.techbd.util.AWSUtil;
import org.techbd.util.AppLogger;
import org.techbd.util.TemplateLogger;

import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;

/**
 * Service for HTTP client operations.
 * Eliminates duplication between sendPostRequest and sendPostRequestWithApiKey.
 */
@Service
public class HttpClientService {
    
    private final CoreDataLedgerApiClient coreDataLedgerApiClient;
    private final StateRegistrationService stateRegistrationService;   
    private final TemplateLogger LOG;
    
    public HttpClientService(
            CoreDataLedgerApiClient coreDataLedgerApiClient,
            StateRegistrationService stateRegistrationService,
            AppLogger appLogger) {
        this.coreDataLedgerApiClient = coreDataLedgerApiClient;
        this.stateRegistrationService = stateRegistrationService;
        this.LOG = appLogger.getLogger(HttpClientService.class);
    }
    /**
     * Context for HTTP request execution
     */
    public record HttpRequestContext(
        WebClient webClient,
        String tenantId,
        Map<String, Object> bundlePayloadWithDisposition,
        String originalPayload,
        String dataLakeApiContentType,
        String interactionId,
        String provenance,
        String requestURI,
        String scoringEngineApiURL,
        String groupInteractionId,
        String masterInteractionId,
        String sourceType,
        String bundleId,
        Map<String, Object> requestParameters,
        boolean replay,
        WithApiKeyAuth apiKeyAuth  // null if not using API key auth
    ) {
        public Object getPayloadForRequest() {
            return bundlePayloadWithDisposition != null 
                ? bundlePayloadWithDisposition 
                : originalPayload;
        }
    }
    
    /**
     * Send POST request (with or without API key)
     */
    public void sendPostRequest(HttpRequestContext context) {
        LOG.debug("Sending POST request for interaction id: {} with API key: {}", 
            context.interactionId(), context.apiKeyAuth() != null);
        
        try {
            var requestSpec = context.webClient()
                .post()
                .uri("?processingAgent=" + context.tenantId())
                .body(BodyInserters.fromValue(context.getPayloadForRequest()))
                .header("Content-Type", Optional.ofNullable(context.dataLakeApiContentType())
                    .orElse(Constants.FHIR_CONTENT_TYPE_HEADER_VALUE));
            
            // Add API key header if configured
            if (context.apiKeyAuth() != null) {
                String apiKey = fetchApiKey(context.apiKeyAuth());
                requestSpec = requestSpec.header(
                    context.apiKeyAuth().apiKeyHeaderName(), 
                    apiKey);
                LOG.info("API key authentication configured for interaction id: {}", 
                    context.interactionId());
            }
            
            // Execute request
            requestSpec
                .retrieve()
                .bodyToMono(String.class)
                .doFinally(signalType -> {
                    recordDataLedger(context);
                })
                .subscribe(
                    response -> handleSuccess(context, response),
                    error -> handleError(context, error)
                );
            
            LOG.info("POST request sent successfully for interaction id: {}", 
                context.interactionId());
            
        } catch (Exception e) {
            LOG.error("Failed to send POST request for interaction id: {}", 
                context.interactionId(), e);
            handleError(context, e);
        }
    }
    
    /**
     * Fetch API key from AWS Secrets Manager
     */
    private String fetchApiKey(WithApiKeyAuth apiKeyAuth) {
        if (StringUtils.isEmpty(apiKeyAuth.apiKeySecretName())) {
            throw new IllegalArgumentException("API key secret name is not configured");
        }
        
        String apiKey = AWSUtil.getValue(apiKeyAuth.apiKeySecretName());
        
        if (StringUtils.isEmpty(apiKey)) {
            throw new IllegalStateException(
                "Failed to retrieve API key from secret: " + apiKeyAuth.apiKeySecretName());
        }
        
        return apiKey;
    }
    
    /**
     * Record interaction in data ledger
     */
    private void recordDataLedger(HttpRequestContext context) {
        try {
            DataLedgerPayload dataLedgerPayload = DataLedgerPayload.create(
                CoreDataLedgerApiClient.Actor.TECHBD.getValue(),
                CoreDataLedgerApiClient.Action.SENT.getValue(),
                CoreDataLedgerApiClient.Actor.NYEC.getValue(),
                context.bundleId());
            
            String dataLedgerProvenance = String.format("%s.sendPostRequest", 
                HttpClientService.class.getName());
            
            coreDataLedgerApiClient.processRequest(
                dataLedgerPayload,
                context.interactionId(),
                context.masterInteractionId(),
                context.groupInteractionId(),
                dataLedgerProvenance,
                org.techbd.config.SourceType.FHIR.name(),
                null);
                
        } catch (Exception e) {
            LOG.error("Failed to record data ledger for interaction id: {}", 
                context.interactionId(), e);
        }
    }
    
    /**
     * Handle successful response
     */
    private void handleSuccess(HttpRequestContext context, String response) {
        LOG.debug("Received successful response for interaction id: {}", context.interactionId());
        
        try {
            var responseMap = new com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(response, new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {});
            
            if ("Success".equalsIgnoreCase(responseMap.get("status"))) {
                LOG.info("Response status: SUCCESS for interaction id: {}", context.interactionId());
                stateRegistrationService.registerStateComplete(
                    context.interactionId(),
                    context.requestURI(),
                    context.tenantId(),
                    response,
                    context.provenance(),
                    context.groupInteractionId(),
                    context.masterInteractionId(),
                    context.sourceType(),
                    context.requestParameters(),
                    context.replay());
            } else {
                LOG.warn("Response status: FAILURE for interaction id: {}", context.interactionId());
                stateRegistrationService.registerStateFailed(
                    context.interactionId(),
                    context.requestURI(),
                    context.tenantId(),
                    response,
                    context.provenance(),
                    context.groupInteractionId(),
                    context.masterInteractionId(),
                    context.sourceType(),
                    context.requestParameters(),
                    context.replay());
            }
        } catch (Exception e) {
            LOG.error("Error processing response for interaction id: {}", 
                context.interactionId(), e);
            stateRegistrationService.registerStateFailed(
                context.interactionId(),
                context.requestURI(),
                context.tenantId(),
                e.getMessage(),
                context.provenance(),
                context.groupInteractionId(),
                context.masterInteractionId(),
                context.sourceType(),
                context.requestParameters(),
                context.replay());
        }
    }
    
    /**
     * Handle error response
     */
    private void handleError(HttpRequestContext context, Throwable error) {
        LOG.error("Error during HTTP request for interaction id: {}", 
            context.interactionId(), error);
        
        stateRegistrationService.registerStateFailure(
            context.interactionId(),
            context.requestURI(),
            context.tenantId(),
            error,
            context.scoringEngineApiURL(),
            context.provenance(),
            context.groupInteractionId(),
            context.masterInteractionId(),
            context.sourceType(),
            context.requestParameters(),
            context.replay());
    }
}
