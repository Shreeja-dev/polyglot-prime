package org.techbd.service.fhir.authentication;

import java.io.ByteArrayInputStream;
import java.time.Duration;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.techbd.config.CoreAppConfig.MTlsAwsSecrets;
import org.techbd.service.fhir.infrastructure.SecretsProvider;
import org.techbd.service.fhir.model.ScoringEngineRequest;
import org.techbd.service.fhir.model.ScoringEngineResponse;
import org.techbd.util.TemplateLogger;

import io.netty.handler.ssl.SslContextBuilder;
import lombok.RequiredArgsConstructor;
import reactor.netty.http.client.HttpClient;

/**
 * Authentication strategy that uses AWS Secrets Manager for mTLS credentials.
 * 
 * Updated to use ScoringEngineRequest and ScoringEngineResponse for
 * cleaner, more maintainable code.
 * 
 * This implementation:
 * - Follows Single Responsibility Principle (only handles AWS Secrets auth)
 * - Is Open for extension but Closed for modification
 * - Can be easily tested with mocked dependencies
 */
@Component("awsSecretsStrategy")
@RequiredArgsConstructor
public class AwsSecretsAuthenticationStrategy implements AuthenticationStrategy {
    
    private final SecretsProvider secretsProvider;
    private final MTlsAwsSecrets awsSecretsConfig;
    private final TemplateLogger logger;
    
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    
    @Override
    public ScoringEngineResponse sendToScoringEngine(ScoringEngineRequest request) {
        logger.info("Using AWS Secrets authentication for interaction id: {}", 
                    request.interactionId());
        
        try {
            validateConfiguration();
            
            // Fetch credentials from AWS Secrets Manager
            KeyDetails keyDetails = secretsProvider.getKeyDetails(
                awsSecretsConfig.mTlsKeySecretName(),
                awsSecretsConfig.mTlsCertSecretName()
            );
            
            logger.debug("Successfully retrieved AWS credentials for interaction id: {}", 
                        request.interactionId());
            
            // Create secure WebClient with mTLS
            WebClient webClient = createSecureWebClient(
                request.baseUrl(),
                request.contentType(),
                keyDetails
            );
            
            // Send request and return response
            return sendRequest(webClient, request);
            
        } catch (WebClientResponseException e) {
            logger.error("HTTP error sending to scoring engine for interaction id: {}, status: {}", 
                        request.interactionId(), e.getStatusCode(), e);
            
            return ScoringEngineResponse.error(
                request.interactionId(),
                e.getResponseBodyAsString(),
                e.getStatusCode().value(),
                e
            );
            
        } catch (Exception e) {
            logger.error("AWS Secrets authentication failed for interaction id: {}", 
                        request.interactionId(), e);
            
            return ScoringEngineResponse.error(
                request.interactionId(),
                "AWS Secrets authentication failed: " + e.getMessage(),
                e
            );
        }
    }
    
    @Override
    public String getStrategyName() {
        return "aws-secrets";
    }
    
    @Override
    public void validateConfiguration() {
        if (awsSecretsConfig == null) {
            throw new IllegalStateException("AWS Secrets configuration is not defined");
        }
        
        if (awsSecretsConfig.mTlsKeySecretName() == null || 
            awsSecretsConfig.mTlsKeySecretName().isBlank()) {
            throw new IllegalStateException(
                "mTlsKeySecretName is not configured in application.yml");
        }
        
        if (awsSecretsConfig.mTlsCertSecretName() == null || 
            awsSecretsConfig.mTlsCertSecretName().isBlank()) {
            throw new IllegalStateException(
                "mTlsCertSecretName is not configured in application.yml");
        }
        
        logger.debug("AWS Secrets configuration validated successfully");
    }
    
    /**
     * Creates a secure WebClient with mTLS using the provided credentials.
     */
    private WebClient createSecureWebClient(
            String baseUrl,
            String contentType,
            KeyDetails keyDetails) throws Exception {
        
        logger.debug("Creating SSL context for mTLS");
        
        var sslContext = SslContextBuilder.forClient()
            .keyManager(
                new ByteArrayInputStream(keyDetails.cert().getBytes()),
                new ByteArrayInputStream(keyDetails.key().getBytes())
            )
            .build();
        
        HttpClient httpClient = HttpClient.create()
            .secure(sslSpec -> sslSpec.sslContext(sslContext))
            .responseTimeout(REQUEST_TIMEOUT);
        
        return WebClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader("Content-Type", contentType)
            .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient))
            .build();
    }
    
    /**
     * Sends the request to the scoring engine and handles the response.
     */
    private ScoringEngineResponse sendRequest(
            WebClient webClient, 
            ScoringEngineRequest request) {
        
        logger.info("Sending request to scoring engine for interaction id: {}", 
                    request.interactionId());
        
        try {
            String response = webClient.post()
                .uri(uriBuilder -> uriBuilder
                    .queryParam("processingAgent", request.processingAgent())
                    .build())
                .bodyValue(request.payload())
                .retrieve()
                .bodyToMono(String.class)
                .block();
            
            logger.info("Received response from scoring engine for interaction id: {}", 
                        request.interactionId());
            
            // Check if response indicates success
            ScoringEngineResponse scoringResponse = ScoringEngineResponse.success(
                request.interactionId(),
                response,
                200
            );
            
            // Validate the response status
            if (!scoringResponse.hasSuccessStatus()) {
                logger.warn("Scoring engine returned non-success status for interaction id: {}", 
                           request.interactionId());
                return scoringResponse.withStatus(ScoringEngineResponse.ResponseStatus.FAILURE);
            }
            
            return scoringResponse;
            
        } catch (WebClientResponseException e) {
            // Re-throw to be caught by outer try-catch
            throw e;
            
        } catch (Exception e) {
            logger.error("Unexpected error sending request for interaction id: {}", 
                        request.interactionId(), e);
            
            return ScoringEngineResponse.error(
                request.interactionId(),
                "Unexpected error: " + e.getMessage(),
                e
            );
        }
    }
    
    /**
     * Value object for key details from AWS Secrets Manager.
     */
    public record KeyDetails(String key, String cert) {
        public KeyDetails {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("Private key cannot be null or empty");
            }
            if (cert == null || cert.isBlank()) {
                throw new IllegalArgumentException("Certificate cannot be null or empty");
            }
        }
    }
}