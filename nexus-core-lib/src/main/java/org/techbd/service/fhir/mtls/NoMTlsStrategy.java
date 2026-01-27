package org.techbd.service.fhir.mtls;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.techbd.util.AppLogger;
import org.techbd.util.TemplateLogger;

import lombok.RequiredArgsConstructor;

/**
 * Strategy for no mTLS authentication
 */
@Component("noMTlsStrategy")
public class NoMTlsStrategy implements MTlsStrategy {
    
    private final TemplateLogger LOG;
    
    public NoMTlsStrategy(AppLogger appLogger) {
        this.LOG = appLogger.getLogger(NoMTlsStrategy.class);
    }
    @Override
    public MTlsResult execute(MTlsContext context) {
        LOG.info("Executing NoMTlsStrategy for interaction id: {}", context.interactionId());
        
        try {
            WebClient webClient = WebClient.builder()
                .baseUrl(context.dataLakeApiBaseURL())
                .defaultHeader("Content-Type", context.dataLakeApiContentType())
                .build();
            
            LOG.debug("WebClient created successfully without mTLS for interaction id: {}", 
                context.interactionId());
            
            return MTlsResult.success(webClient);
            
        } catch (Exception e) {
            LOG.error("Failed to create WebClient for interaction id: {}", 
                context.interactionId(), e);
            return MTlsResult.failure("Failed to create WebClient: " + e.getMessage());
        }
    }
    
    @Override
    public String getStrategyName() {
        return "no-mTls";
    }
}