package org.techbd.ingest.service.soap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.doAnswer;

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

import java.io.ByteArrayOutputStream;

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
        // DefaultSoapResponseStrategy captures the SOAP response for logging purposes.
        // It does not write to the HttpServletResponse (that is handled by Spring-WS).
        doAnswer(invocation -> {
            ByteArrayOutputStream os = invocation.getArgument(0);
            os.write("<soap>response</soap>".getBytes());
            return null;
        }).when(soapMessage).writeTo(any(ByteArrayOutputStream.class));

        assertDoesNotThrow(() ->
                strategy.writeResponse("INT-1", messageContext, request, response, soapMessage));

        verifyNoInteractions(response);
        verifyNoInteractions(messageContext);
        verifyNoInteractions(request);
    }

    @Test
    void writeResponse_withNullInteractionId_doesNotThrow() {
        assertDoesNotThrow(() ->
                strategy.writeResponse(null, messageContext, request, response, soapMessage));
    }
}
