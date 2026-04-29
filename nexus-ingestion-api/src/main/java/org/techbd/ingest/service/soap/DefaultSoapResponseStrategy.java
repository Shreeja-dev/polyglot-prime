package org.techbd.ingest.service.soap;

import org.springframework.ws.context.MessageContext;
import org.springframework.ws.soap.SoapMessage;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.TemplateLogger;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Default strategy — preserves the existing behaviour exactly.
 *
 * Spring-WS has already written the SOAP response; nothing extra is done here.
 * Content-Type remains {@code application/soap+xml} as set by the framework.
 */
public class DefaultSoapResponseStrategy implements SoapResponseStrategy {

    private final TemplateLogger log;

    public DefaultSoapResponseStrategy(AppLogger appLogger) {
        this.log = appLogger.getLogger(DefaultSoapResponseStrategy.class);
    }

    @Override
    public void writeResponse(
            String interactionId,
            MessageContext messageContext,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse,
            SoapMessage builtResponse) {

        // Intentionally a no-op: Spring-WS handles serialisation in its normal pipeline.
        log.debug("DefaultSoapResponseStrategy:: no-op for interactionId={}", interactionId);
    }
}