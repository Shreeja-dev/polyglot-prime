package org.techbd.service.fhir.mtls;

import java.io.ByteArrayInputStream;
import java.security.Security;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.techbd.config.CoreAppConfig.MTlsAwsSecrets;
import org.techbd.util.AWSUtil;
import org.techbd.util.AppLogger;
import org.techbd.util.TemplateLogger;

import io.micrometer.common.util.StringUtils;
import io.netty.handler.ssl.SslContextBuilder;
import lombok.RequiredArgsConstructor;
import reactor.netty.http.client.HttpClient;

/**
 * Strategy for mTLS using AWS Secrets Manager
 */
@Component("awsSecretsStrategy")
public class AwsSecretsStrategy implements MTlsStrategy {
    
    private final MTlsAwsSecrets awsSecretsConfig;
    private final TemplateLogger LOG;

    public AwsSecretsStrategy(MTlsAwsSecrets awsSecretsConfig, AppLogger appLogger) {
        this.awsSecretsConfig = awsSecretsConfig;
        this.LOG = appLogger.getLogger(AwsSecretsStrategy.class);
    }

    @Override
    public MTlsResult execute(MTlsContext context) {
        LOG.info("Executing AwsSecretsStrategy for interaction id: {}", context.interactionId());
        
        try {
            // Validate configuration
            validateConfiguration();
            
            // Setup BouncyCastle provider
            setupSecurityProvider();
            
            // Fetch secrets
            String certificate = AWSUtil.getValue(awsSecretsConfig.mTlsCertSecretName());
            String privateKey = AWSUtil.getValue(awsSecretsConfig.mTlsKeySecretName());
            
            // Validate secrets
            validateSecrets(certificate, privateKey);
            
            // Create SSL context
            var sslContext = SslContextBuilder.forClient()
                .keyManager(
                    new ByteArrayInputStream(certificate.getBytes()),
                    new ByteArrayInputStream(privateKey.getBytes()))
                .build();
            
            // Create HTTP client with SSL
            HttpClient httpClient = HttpClient.create()
                .secure(sslSpec -> sslSpec.sslContext(sslContext));
            
            ReactorClientHttpConnector connector = new ReactorClientHttpConnector(httpClient);
            
            // Build WebClient
            WebClient webClient = WebClient.builder()
                .baseUrl(context.dataLakeApiBaseURL())
                .defaultHeader("Content-Type", context.dataLakeApiContentType())
                .clientConnector(connector)
                .build();
            
            LOG.info("WebClient with AWS Secrets mTLS created successfully for interaction id: {}", 
                context.interactionId());
            
            return MTlsResult.success(webClient);
            
        } catch (Exception e) {
            LOG.error("Failed to execute AwsSecretsStrategy for interaction id: {}", 
                context.interactionId(), e);
            return MTlsResult.failure("AWS Secrets mTLS failed: " + e.getMessage());
        }
    }
    
    private void validateConfiguration() {
        if (awsSecretsConfig == null 
                || StringUtils.isEmpty(awsSecretsConfig.mTlsKeySecretName())
                || StringUtils.isEmpty(awsSecretsConfig.mTlsCertSecretName())) {
            throw new IllegalArgumentException(
                "AWS Secrets mTLS configuration is incomplete. " +
                "Both mTlsKeySecretName and mTlsCertSecretName must be configured.");
        }
    }
    
    private void setupSecurityProvider() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }
    
    private void validateSecrets(String certificate, String privateKey) {
        if (StringUtils.isEmpty(certificate)) {
            throw new IllegalArgumentException(
                "Certificate is empty from AWS Secrets Manager for secret: " + 
                awsSecretsConfig.mTlsCertSecretName());
        }
        
        if (StringUtils.isEmpty(privateKey)) {
            throw new IllegalArgumentException(
                "Private key is empty from AWS Secrets Manager for secret: " + 
                awsSecretsConfig.mTlsKeySecretName());
        }
    }
    
    @Override
    public String getStrategyName() {
        return "aws-secrets";
    }
}