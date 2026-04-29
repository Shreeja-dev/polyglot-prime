package org.techbd.ingest.service.soap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.soap.SoapMessage;
import org.techbd.ingest.commons.Constants;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.SoapResponseUtil;
import org.techbd.ingest.util.TemplateLogger;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@ExtendWith(MockitoExtension.class)
class MtomSoapResponseStrategyTest {

    @Mock private SoapResponseUtil soapResponseUtil;
    @Mock private AppLogger appLogger;
    @Mock private TemplateLogger templateLogger;
    @Mock private MessageContext messageContext;
    @Mock private SoapMessage soapMessage;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;

    private MtomSoapResponseStrategy strategy;

    @BeforeEach
    void setup() {
        when(appLogger.getLogger(any())).thenReturn(templateLogger);
        strategy = new MtomSoapResponseStrategy(soapResponseUtil, appLogger);
    }

    // ── SOAP 1.2 (default / application/soap+xml) ─────────────────────────────

    @Test
    void writeResponse_soap12_setsCorrectContentTypeHeader() throws Exception {
        when(soapResponseUtil.generateMimeBoundary()).thenReturn("BOUNDARY_123");
        when(soapResponseUtil.generateUuid()).thenReturn("uuid-456");
        when(soapResponseUtil.serializeSoapMessage(any(), any())).thenReturn("<soap:Envelope/>");
        when(request.getContentType()).thenReturn("application/soap+xml");

        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(outputStreamOf(captured));

        strategy.writeResponse("INT-1", messageContext, request, response, soapMessage);

        verify(response).setHeader(eq("Content-Type"), contains("multipart/related"));
        verify(response).setHeader(eq("Content-Type"), contains("boundary=\"BOUNDARY_123\""));
        verify(response).setHeader(eq("Content-Type"), contains("type=\"application/xop+xml\""));
        verify(response).setHeader(eq("Content-Type"), contains("start-info=\"application/soap+xml\""));
        verify(response).setHeader(eq("Content-Type"),
                contains("action=\"urn:ihe:iti:2007:ProvideAndRegisterDocumentSet-b\""));
    }

    @Test
    void writeResponse_soap12_innerPartTypeIsApplicationSoapXml() throws Exception {
        when(soapResponseUtil.generateMimeBoundary()).thenReturn("TESTBOUNDARY");
        when(soapResponseUtil.generateUuid()).thenReturn("test-uuid");
        when(soapResponseUtil.serializeSoapMessage(any(), any()))
                .thenReturn("<soap:Envelope>body</soap:Envelope>");
        when(request.getContentType()).thenReturn("application/soap+xml");

        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(outputStreamOf(captured));

        strategy.writeResponse("INT-1", messageContext, request, response, soapMessage);

        String body = captured.toString();
        assertTrue(body.contains("--TESTBOUNDARY"));
        assertTrue(body.contains("<soap:Envelope>body</soap:Envelope>"));
        assertTrue(body.contains("Content-Type: application/xop+xml; charset=UTF-8; type=\"application/soap+xml\""));
        assertTrue(body.contains("--TESTBOUNDARY--"));
    }

    // ── SOAP 1.1 (text/xml via Content-Type) ──────────────────────────────────

    @Test
    void writeResponse_soap11_viaContentType_outerStartInfoIsTextXml() throws Exception {
        when(soapResponseUtil.generateMimeBoundary()).thenReturn("BOUNDARY_11");
        when(soapResponseUtil.generateUuid()).thenReturn("uuid-11");
        when(soapResponseUtil.serializeSoapMessage(any(), any())).thenReturn("<s11:Envelope/>");
        when(request.getContentType()).thenReturn("text/xml");

        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(outputStreamOf(captured));

        strategy.writeResponse("INT-11", messageContext, request, response, soapMessage);

        verify(response).setHeader(eq("Content-Type"), contains("start-info=\"text/xml\""));
        // action and other outer params must still be present
        verify(response).setHeader(eq("Content-Type"),
                contains("action=\"urn:ihe:iti:2007:ProvideAndRegisterDocumentSet-b\""));
    }

    @Test
    void writeResponse_soap11_viaContentType_innerPartTypeIsTextXml() throws Exception {
        when(soapResponseUtil.generateMimeBoundary()).thenReturn("BND11");
        when(soapResponseUtil.generateUuid()).thenReturn("uuid-11");
        when(soapResponseUtil.serializeSoapMessage(any(), any()))
                .thenReturn("<s11:Envelope>11</s11:Envelope>");
        when(request.getContentType()).thenReturn("text/xml");

        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(outputStreamOf(captured));

        strategy.writeResponse("INT-11", messageContext, request, response, soapMessage);

        String body = captured.toString();
        assertTrue(body.contains("Content-Type: application/xop+xml; charset=UTF-8; type=\"text/xml\""),
                "Inner MIME part must use text/xml for SOAP 1.1");
        assertFalse(body.contains("type=\"application/soap+xml\""),
                "Inner MIME part must NOT use application/soap+xml for SOAP 1.1");
    }

    @Test
    void writeResponse_soap11_viaContentType_notContainApplicationSoapXmlInStartInfo() throws Exception {
        when(soapResponseUtil.generateMimeBoundary()).thenReturn("B");
        when(soapResponseUtil.generateUuid()).thenReturn("u");
        when(soapResponseUtil.serializeSoapMessage(any(), any())).thenReturn("<env/>");
        when(request.getContentType()).thenReturn("text/xml");

        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(outputStreamOf(captured));

        strategy.writeResponse("INT-11", messageContext, request, response, soapMessage);

        // Capture the exact Content-Type header value set on the response
        org.mockito.ArgumentCaptor<String> captor =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(response).setHeader(eq("Content-Type"), captor.capture());
        assertFalse(captor.getValue().contains("start-info=\"application/soap+xml\""),
                "start-info must NOT be application/soap+xml for SOAP 1.1");
    }

    // ── SOAP 1.1 (text/xml via SOAPAction header, no Content-Type) ────────────

    @Test
    void writeResponse_soap11_viaSoapActionHeader_usesTextXml() throws Exception {
        when(soapResponseUtil.generateMimeBoundary()).thenReturn("BSOAP");
        when(soapResponseUtil.generateUuid()).thenReturn("u-soap");
        when(soapResponseUtil.serializeSoapMessage(any(), any())).thenReturn("<env/>");
        // No Content-Type — detection falls through to SOAPAction header
        when(request.getContentType()).thenReturn(null);
        when(request.getHeader("SOAPAction")).thenReturn("\"urn:ihe:iti:2007:ProvideAndRegisterDocumentSet-b\"");

        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(outputStreamOf(captured));

        strategy.writeResponse("INT-SA", messageContext, request, response, soapMessage);

        verify(response).setHeader(eq("Content-Type"), contains("start-info=\"text/xml\""));
        String body = captured.toString();
        assertTrue(body.contains("type=\"text/xml\""),
                "Inner MIME part must use text/xml when SOAPAction header is present");
    }

    // ── SOAP 1.2 (no Content-Type, no SOAPAction) — defaults to SOAP 1.2 ──────

    @Test
    void writeResponse_noContentTypeNoSoapAction_defaultsToSoap12() throws Exception {
        when(soapResponseUtil.generateMimeBoundary()).thenReturn("BD");
        when(soapResponseUtil.generateUuid()).thenReturn("ud");
        when(soapResponseUtil.serializeSoapMessage(any(), any())).thenReturn("<env/>");
        when(request.getContentType()).thenReturn(null);
        when(request.getHeader("SOAPAction")).thenReturn(null);

        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(outputStreamOf(captured));

        strategy.writeResponse("INT-D", messageContext, request, response, soapMessage);

        verify(response).setHeader(eq("Content-Type"), contains("start-info=\"application/soap+xml\""));
    }

    // ── Shared behaviour ──────────────────────────────────────────────────────

    @Test
    void writeResponse_setsAckContentTypeAttribute() throws Exception {
        when(soapResponseUtil.generateMimeBoundary()).thenReturn("B");
        when(soapResponseUtil.generateUuid()).thenReturn("u");
        when(soapResponseUtil.serializeSoapMessage(any(), any())).thenReturn("<env/>");

        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(outputStreamOf(captured));

        strategy.writeResponse("INT-1", messageContext, request, response, soapMessage);

        verify(request).setAttribute(eq(Constants.ACK_CONTENT_TYPE), anyString());
    }

    @Test
    void writeResponse_swallowsBrokenPipeException() throws Exception {
        when(soapResponseUtil.generateMimeBoundary()).thenReturn("B");
        when(soapResponseUtil.generateUuid()).thenReturn("u");
        when(soapResponseUtil.serializeSoapMessage(any(), any())).thenReturn("<env/>");

        when(response.getOutputStream()).thenThrow(new IOException("Broken pipe"));

        // The strategy logs a warning and throws RuntimeException with "Client disconnected" message
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                strategy.writeResponse("INT-1", messageContext, request, response, soapMessage));
        assertTrue(ex.getMessage().contains("Client disconnected"));
    }

    @Test
    void writeResponse_rethrowsNonBrokenPipeIOException() throws Exception {
        when(soapResponseUtil.generateMimeBoundary()).thenReturn("B");
        when(soapResponseUtil.generateUuid()).thenReturn("u");
        when(soapResponseUtil.serializeSoapMessage(any(), any())).thenReturn("<env/>");

        when(response.getOutputStream()).thenThrow(new IOException("Disk full"));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                strategy.writeResponse("INT-1", messageContext, request, response, soapMessage));
        assertTrue(ex.getMessage().contains("Failed to write MTOM response"));
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private ServletOutputStream outputStreamOf(ByteArrayOutputStream baos) {
        return new ServletOutputStream() {
            @Override public void write(int b) { baos.write(b); }
            @Override public void write(byte[] b, int off, int len) { baos.write(b, off, len); }
            @Override public boolean isReady() { return true; }
            @Override public void setWriteListener(WriteListener wl) {}
        };
    }
}