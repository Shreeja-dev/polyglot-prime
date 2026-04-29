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
class TruBridgeMtomSoapResponseStrategyTest {

    @Mock private SoapResponseUtil soapResponseUtil;
    @Mock private AppLogger appLogger;
    @Mock private TemplateLogger templateLogger;
    @Mock private MessageContext messageContext;
    @Mock private SoapMessage soapMessage;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;

    private TruBridgeMtomSoapResponseStrategy strategy;

    @BeforeEach
    void setup() {
        when(appLogger.getLogger(any())).thenReturn(templateLogger);
        strategy = new TruBridgeMtomSoapResponseStrategy(soapResponseUtil, appLogger);
    }

    // ── TruBridge wire-format invariants (same regardless of SOAP version) ────

    @Test
    void writeResponse_setsUnquotedContentTypeHeader() throws Exception {
        when(soapResponseUtil.generateMimeBoundary()).thenReturn("TBBOUNDARY");
        when(soapResponseUtil.generateUuid()).thenReturn("tb-uuid");
        when(soapResponseUtil.serializeSoapMessage(any(), any())).thenReturn("<env/>");

        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(outputStreamOf(captured));

        strategy.writeResponse("INT-TB", messageContext, request, response, soapMessage);

        // TruBridge wire format: unquoted boundary and type values
        verify(response).setHeader(eq("Content-Type"), contains("boundary=TBBOUNDARY"));
        verify(response).setHeader(eq("Content-Type"), contains("type=application/xop+xml"));
    }

    @Test
    void writeResponse_outerContentType_doesNotContainStartOrStartInfoOrAction() throws Exception {
        when(soapResponseUtil.generateMimeBoundary()).thenReturn("B");
        when(soapResponseUtil.generateUuid()).thenReturn("u");
        when(soapResponseUtil.serializeSoapMessage(any(), any())).thenReturn("<env/>");

        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(outputStreamOf(captured));

        strategy.writeResponse("INT-TB", messageContext, request, response, soapMessage);

        org.mockito.ArgumentCaptor<String> captor =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(response).setHeader(eq("Content-Type"), captor.capture());
        String ct = captor.getValue();
        assertFalse(ct.contains("start="),      "Outer Content-Type must not contain 'start='");
        assertFalse(ct.contains("start-info="), "Outer Content-Type must not contain 'start-info='");
        assertFalse(ct.contains("action="),     "Outer Content-Type must not contain 'action='");
    }

    @Test
    void writeResponse_setsBinaryTransferEncodingHeader() throws Exception {
        when(soapResponseUtil.generateMimeBoundary()).thenReturn("B");
        when(soapResponseUtil.generateUuid()).thenReturn("u");
        when(soapResponseUtil.serializeSoapMessage(any(), any())).thenReturn("<env/>");

        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(outputStreamOf(captured));

        strategy.writeResponse("INT-TB", messageContext, request, response, soapMessage);

        verify(response).setHeader("Content-Transfer-Encoding", "binary");
    }

    @Test
    void writeResponse_bodyContainsMimeBoundaryAndSoapXml() throws Exception {
        when(soapResponseUtil.generateMimeBoundary()).thenReturn("TBBND");
        when(soapResponseUtil.generateUuid()).thenReturn("tb-u");
        when(soapResponseUtil.serializeSoapMessage(any(), any()))
                .thenReturn("<soap:Envelope>tb</soap:Envelope>");

        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(outputStreamOf(captured));

        strategy.writeResponse("INT-TB", messageContext, request, response, soapMessage);

        String body = captured.toString();
        assertTrue(body.contains("--TBBND"));
        assertTrue(body.contains("<soap:Envelope>tb</soap:Envelope>"));
        assertTrue(body.contains("Content-Transfer-Encoding: binary"));
        assertTrue(body.contains("--TBBND--"));
    }

    // ── SOAP 1.2 (application/soap+xml) ──────────────────────────────────────

    @Test
    void writeResponse_soap12_innerPartTypeIsApplicationSoapXml() throws Exception {
        when(soapResponseUtil.generateMimeBoundary()).thenReturn("BND12");
        when(soapResponseUtil.generateUuid()).thenReturn("uuid-12");
        when(soapResponseUtil.serializeSoapMessage(any(), any()))
                .thenReturn("<s12:Envelope>12</s12:Envelope>");
        when(request.getContentType()).thenReturn("application/soap+xml");

        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(outputStreamOf(captured));

        strategy.writeResponse("INT-12", messageContext, request, response, soapMessage);

        String body = captured.toString();
        assertTrue(body.contains("Content-Type: application/xop+xml; charset=UTF-8; type=\"application/soap+xml\""),
                "Inner MIME part must use application/soap+xml for SOAP 1.2");
        assertFalse(body.contains("type=\"text/xml\""),
                "Inner MIME part must NOT use text/xml for SOAP 1.2");
    }

    // ── SOAP 1.1 (text/xml via Content-Type) ──────────────────────────────────

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

    // ── SOAP 1.1 (text/xml via SOAPAction header, no Content-Type) ────────────

    @Test
    void writeResponse_soap11_viaSoapActionHeader_innerPartTypeIsTextXml() throws Exception {
        when(soapResponseUtil.generateMimeBoundary()).thenReturn("BNDSA");
        when(soapResponseUtil.generateUuid()).thenReturn("uuid-sa");
        when(soapResponseUtil.serializeSoapMessage(any(), any())).thenReturn("<env/>");
        when(request.getContentType()).thenReturn(null);
        when(request.getHeader("SOAPAction")).thenReturn("\"urn:ihe:iti:2007:ProvideAndRegisterDocumentSet-b\"");

        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(outputStreamOf(captured));

        strategy.writeResponse("INT-SA", messageContext, request, response, soapMessage);

        String body = captured.toString();
        assertTrue(body.contains("type=\"text/xml\""),
                "Inner MIME part must use text/xml when SOAPAction header signals SOAP 1.1");
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

        String body = captured.toString();
        assertTrue(body.contains("type=\"application/soap+xml\""),
                "Must default to application/soap+xml when no SOAP version signals are present");
    }

    // ── Shared behaviour ──────────────────────────────────────────────────────

    @Test
    void writeResponse_setsAckContentTypeAttribute() throws Exception {
        when(soapResponseUtil.generateMimeBoundary()).thenReturn("B");
        when(soapResponseUtil.generateUuid()).thenReturn("u");
        when(soapResponseUtil.serializeSoapMessage(any(), any())).thenReturn("<env/>");

        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(outputStreamOf(captured));

        strategy.writeResponse("INT-TB", messageContext, request, response, soapMessage);

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
                strategy.writeResponse("INT-TB", messageContext, request, response, soapMessage));
        assertTrue(ex.getMessage().contains("Client disconnected"));
    }

    @Test
    void writeResponse_swallowsClientAbortException() throws Exception {
        when(soapResponseUtil.generateMimeBoundary()).thenReturn("B");
        when(soapResponseUtil.generateUuid()).thenReturn("u");
        when(soapResponseUtil.serializeSoapMessage(any(), any())).thenReturn("<env/>");

        IOException clientAbort = new IOException("Connection reset by peer") {
            @Override public String toString() {
                return "org.apache.catalina.connector.ClientAbortException: " + getMessage();
            }
        };
        when(response.getOutputStream()).thenThrow(clientAbort);

        // The strategy logs a warning and throws RuntimeException with "Client disconnected" message
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                strategy.writeResponse("INT-TB", messageContext, request, response, soapMessage));
        assertTrue(ex.getMessage().contains("Client disconnected"));
    }

    @Test
    void writeResponse_rethrowsNonBrokenPipeIOException() throws Exception {
        when(soapResponseUtil.generateMimeBoundary()).thenReturn("B");
        when(soapResponseUtil.generateUuid()).thenReturn("u");
        when(soapResponseUtil.serializeSoapMessage(any(), any())).thenReturn("<env/>");

        when(response.getOutputStream()).thenThrow(new IOException("Disk full"));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                strategy.writeResponse("INT-TB", messageContext, request, response, soapMessage));
        assertTrue(ex.getMessage().contains("Failed to write TruBridge MTOM response"));
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