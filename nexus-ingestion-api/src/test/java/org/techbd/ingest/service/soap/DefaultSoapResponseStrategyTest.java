package org.techbd.ingest.service.soap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.soap.SoapMessage;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.TemplateLogger;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@ExtendWith(MockitoExtension.class)
class DefaultSoapResponseStrategyTest {

    @Mock private AppLogger appLogger;
    @Mock private TemplateLogger templateLogger;
    @Mock private MessageContext messageContext;
    @Mock private SoapMessage soapMessage;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;

    private DefaultSoapResponseStrategy strategy;

    @BeforeEach
    void setup() {
        when(appLogger.getLogger(any())).thenReturn(templateLogger);
        strategy = new DefaultSoapResponseStrategy(appLogger);
    }

    @Test
    void writeResponse_isNoOp_doesNotTouchResponse() throws Exception {
        // DefaultSoapResponseStrategy is intentionally a no-op.
        // It must not write to response, set headers, or throw.
        assertDoesNotThrow(() ->
                strategy.writeResponse("INT-1", messageContext, request, response, soapMessage));

        verifyNoInteractions(response);
        verifyNoInteractions(messageContext);
        verifyNoInteractions(soapMessage);
        verifyNoInteractions(request);
    }

    @Test
    void writeResponse_withNullInteractionId_doesNotThrow() {
        assertDoesNotThrow(() ->
                strategy.writeResponse(null, messageContext, request, response, soapMessage));
    }
}
