package org.techbd.ingest.util;

import java.util.Iterator;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;

import org.springframework.stereotype.Component;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.soap.SoapHeader;
import org.springframework.ws.soap.SoapHeaderElement;
import org.springframework.ws.soap.SoapMessage;
import org.springframework.ws.transport.context.TransportContextHolder;
import org.springframework.ws.transport.http.HttpServletConnection;
import org.techbd.ingest.commons.Constants;
import org.techbd.ingest.config.AppConfig;
import org.techbd.ingest.feature.FeatureEnum;
import org.techbd.ingest.model.RequestContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;

@Component
public class SoapResponseUtil {

    private final AppConfig appConfig;
    private TemplateLogger log;

    public SoapResponseUtil(AppConfig appConfig, AppLogger appLogger) {
        this.appConfig = appConfig;
        this.log = appLogger.getLogger(SoapResponseUtil.class);
        log.info("SoapResponseUtil initialized with AppConfig");
    }

    public SoapMessage buildSoapResponse(String interactionId, MessageContext messageContext) {
        log.info("Building SOAP response for interactionId={}", interactionId);

        try {
            SoapMessage soapResponse = (SoapMessage) messageContext.getResponse();
            SoapMessage soapRequest  = (SoapMessage) messageContext.getRequest();
            SoapHeader  header       = soapResponse.getSoapHeader();

            String messageId = "urn:uuid:" + UuidUtil.generateUuid();
            String relatesTo = extractRelatesTo(soapRequest);

            if (relatesTo == null) {
                relatesTo = "urn:uuid:unknown-incoming-message-id";
                log.warn("RelatesTo header not found in request. Using fallback: {}", relatesTo);
            }

            var wsa    = appConfig.getSoap().getWsa();
            String action;
            var transportContext = TransportContextHolder.getTransportContext();
            var connection = (HttpServletConnection) transportContext.getConnection();
            HttpServletRequest httpRequest = connection.getHttpServletRequest();

            RequestContext context = (RequestContext) httpRequest.getAttribute(Constants.REQUEST_CONTEXT);
            if (context.isPixRequest()) {
                action = wsa.getAction();
                log.info("PIX request detected. Using Action={} interactionId={}", action, interactionId);
            } else {
                action = wsa.getPnrAction();
                log.info("PNR request detected. Using Action={} interactionId={}", action, interactionId);
            }
            var techbd = appConfig.getSoap().getTechbd();

            // ── Standard WS-Addressing headers ────────────────────────────────
            header.addHeaderElement(new QName(wsa.getNamespace(), "Action", wsa.getPrefix()))
                  .setText(action);
            header.addHeaderElement(new QName(wsa.getNamespace(), "MessageID", wsa.getPrefix()))
                  .setText(messageId);
            header.addHeaderElement(new QName(wsa.getNamespace(), "RelatesTo", wsa.getPrefix()))
                  .setText(relatesTo);
            header.addHeaderElement(new QName(wsa.getNamespace(), "To", wsa.getPrefix()))
                  .setText(wsa.getTo());

            // ── TechBD custom segment (feature-flag guarded) ──────────────────
            if (org.techbd.ingest.feature.FeatureEnum.isEnabled(FeatureEnum.INCLUDE_TECHBD_INTERACTION_ID_IN_SOAP_RESPONSE)) {
                log.info("Feature {} enabled — appending TechBD custom segment. interactionId={} version={}",
                        FeatureEnum.INCLUDE_TECHBD_INTERACTION_ID_IN_SOAP_RESPONSE.name(),
                        interactionId, appConfig.getVersion());

                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                dbf.setNamespaceAware(true);
                Document doc = dbf.newDocumentBuilder().newDocument();

                Element interactionElement = doc.createElementNS(
                        techbd.getNamespace(), techbd.getPrefix() + ":Interaction");
                interactionElement.setAttribute("InteractionID", interactionId);
                interactionElement.setAttribute("TechBDIngestionApiVersion", appConfig.getVersion());

                Transformer transformer = TransformerFactory.newInstance().newTransformer();
                transformer.transform(new DOMSource(interactionElement), header.getResult());

                log.info("TechBD custom segment added to SOAP response header. interactionId={}", interactionId);
            } else {
                log.debug("Feature {} disabled — skipping TechBD custom segment. interactionId={}",
                        FeatureEnum.INCLUDE_TECHBD_INTERACTION_ID_IN_SOAP_RESPONSE.name(), interactionId);
            }

            log.info("SOAP response built successfully for interactionId={} version={}",
                    interactionId, appConfig.getVersion());
            return soapResponse;

        } catch (Exception e) {
            log.error("Failed to build SOAP response for interactionId={} version={}",
                    interactionId, appConfig.getVersion(), e);
            throw new RuntimeException("Error creating SOAP response", e);
        }
    }

    private String extractRelatesTo(SoapMessage soapRequest) {
        String wsaNs = appConfig.getSoap().getWsa().getNamespace();
        Iterator<?> it = soapRequest.getSoapHeader().examineAllHeaderElements();
        while (it.hasNext()) {
            SoapHeaderElement element = (SoapHeaderElement) it.next();
            if ("MessageID".equals(element.getName().getLocalPart()) &&
                wsaNs.equals(element.getName().getNamespaceURI())) {
                return element.getText();
            }
        }
        return null;
    }

    /**
     * Generates a random MIME boundary token (UUID-based, no hyphens).
     * Safe for use as a multipart boundary.
     */
    public String generateMimeBoundary() {
        return "MIMEBoundary_" + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Generates a plain UUID string (with hyphens).
     * Used for Content-ID and MessageID generation in MTOM strategies.
     */
    public String generateUuid() {
        return UUID.randomUUID().toString();
    }

    /**
     * Serializes a {@link SoapMessage} to its canonical XML string representation.
     * Used by MTOM strategies to obtain the SOAP envelope bytes.
     *
     * @param message       the SOAP message to serialize
     * @param interactionId for logging
     * @return XML string
     */
    public String serializeSoapMessage(SoapMessage message, String interactionId) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            message.writeTo(baos);
            return baos.toString(java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("SoapResponseUtil:: Failed to serialize SOAP message. interactionId={} error={}",
                    interactionId, e.getMessage(), e);
            throw new RuntimeException("Failed to serialize SOAP message", e);
        }
    }

    /**
     * Extracts the inbound WS-Addressing MessageID from the SOAP request header.
     * Returns a fallback string if not found.
     *
     * @param soapRequest the inbound SOAP message
     * @return the MessageID value, or a fallback URN
     */
    public String extractInboundMessageId(SoapMessage soapRequest) {
        return extractRelatesTo(soapRequest); // reuse existing private method logic
    }
}