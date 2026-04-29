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
    public SoapResponseStrategy resolve(String responseType) {
        if (responseType == null || responseType.isBlank()) {
            log.info("SoapResponseStrategyFactory:: no responseType — using default strategy");
            return defaultStrategy;
        }
        return switch (responseType.trim().toLowerCase()) {
            case MTOM           -> {
                log.info("SoapResponseStrategyFactory:: resolved MtomSoapResponseStrategy");
                yield mtomStrategy;
            }
            case MTOM_TRUBRIDGE -> {
                log.info("SoapResponseStrategyFactory:: resolved TruBridgeMtomSoapResponseStrategy");
                yield truBridgeStrategy;
            }
            default -> {
                log.warn("SoapResponseStrategyFactory:: unknown responseType='{}' — falling back to default", responseType);
                yield defaultStrategy;
            }
        };
    }
}