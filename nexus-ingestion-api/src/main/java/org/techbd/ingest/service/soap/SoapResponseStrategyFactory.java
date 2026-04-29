package org.techbd.ingest.service.soap;

import org.springframework.stereotype.Component;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.SoapResponseUtil;
import org.techbd.ingest.util.TemplateLogger;

/**
 * Resolves the correct {@link SoapResponseStrategy} based on the 
 * responsetype specified in portconfig
 * Resolution rules:
 * <pre>
 *   absent / null / unsupported  →  DefaultSoapResponseStrategy   (no-op, existing behaviour)
 *   "mtom"                       →  MtomSoapResponseStrategy
 *   "mtom_trubridge"             →  TruBridgeMtomSoapResponseStrategy
 * </pre>
 *
 * Strategies are singletons reused across requests.
 */
@Component
public class SoapResponseStrategyFactory {

    /** Header name carrying the desired response mode. */
    public static final String RESPONSE_TYPE_HEADER = "X-Response-Type";

    public static final String MTOM            = "mtom";
    public static final String MTOM_TRUBRIDGE  = "mtom_trubridge";

    private final DefaultSoapResponseStrategy      defaultStrategy;
    private final MtomSoapResponseStrategy         mtomStrategy;
    private final TruBridgeMtomSoapResponseStrategy truBridgeStrategy;
    private final TemplateLogger                   log;

    public SoapResponseStrategyFactory(SoapResponseUtil soapResponseUtil, AppLogger appLogger) {
        this.log              = appLogger.getLogger(SoapResponseStrategyFactory.class);
        this.defaultStrategy  = new DefaultSoapResponseStrategy(appLogger);
        this.mtomStrategy     = new MtomSoapResponseStrategy(soapResponseUtil, appLogger);
        this.truBridgeStrategy = new TruBridgeMtomSoapResponseStrategy(soapResponseUtil, appLogger);
    }

    /**
     * Returns the strategy matching {@code responseType} (case-insensitive).
     * Falls back to {@link DefaultSoapResponseStrategy} for any unrecognised value.
     */
    public SoapResponseStrategy resolve(String responseType,String interactionId,String requestUri,boolean isPixRequest) {
        if (isPixRequest) {
            log.info("[SOAP_RESPONSE_STRATEGY]:: PIX request — using default strategy for interactionId={}", interactionId);
            return defaultStrategy;
        }
        if(requestUri != null && requestUri.equalsIgnoreCase("/xds/XDSbRepositoryWS")) {
           log.info("[SOAP_RESPONSE_STRATEGY]:: XDSbRepositoryWS — using default strategy for interactionId={}", interactionId);
            return defaultStrategy;
        }
        if (responseType == null || responseType.isBlank()) {
            log.info("[SOAP_RESPONSE_STRATEGY]:: no responseType — using default strategy for interactionId={}", interactionId);
            return defaultStrategy;
        }
        return switch (responseType.trim().toLowerCase()) {
            case MTOM           -> {
                log.info("[SOAP_RESPONSE_STRATEGY]:: resolved MtomSoapResponseStrategy for interactionId={}", interactionId);
                yield mtomStrategy;
            }
            case MTOM_TRUBRIDGE -> {
                log.info("[SOAP_RESPONSE_STRATEGY]:: resolved TruBridgeMtomSoapResponseStrategy for interactionId={}", interactionId);
                yield truBridgeStrategy;
            }
            default -> {
                log.warn("[SOAP_RESPONSE_STRATEGY]:: unknown responseType='{}' — falling back to default for interactionId={}", responseType, interactionId);
                yield defaultStrategy;
            }
        };
    }
}