package org.techbd.ingest.endpoint;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.ws.context.MessageContext;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;
import org.springframework.ws.transport.context.TransportContextHolder;
import org.springframework.ws.transport.http.HttpServletConnection;
import org.techbd.ingest.commons.AppLogger;
import org.techbd.ingest.commons.Constants;
import org.techbd.ingest.commons.TemplateLogger;
import org.techbd.ingest.config.AppConfig;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.service.MessageProcessorService;
import org.techbd.ingest.service.iti.AcknowledgementService;
import org.techbd.iti.schema.ObjectFactory;
import org.techbd.iti.schema.ProvideAndRegisterDocumentSetRequestType;
import org.techbd.iti.schema.RegistryResponseType;

import io.micrometer.common.util.StringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.xml.bind.JAXBElement;

/**n
 * XDS.b Provide and Register Document Set-b Endpoint (ITI-41).
 *
 * Handles incoming SOAP requests containing document metadata and binary content
 * (MTOM/XOP attachments) according to IHE ITI-41 transaction specification.
 *
 * Responsibilities:
 * <ul>
 *   <li>Parse incoming ProvideAndRegisterDocumentSet-b requests</li>
 *   <li>Extract metadata and binary documents</li>
 *   <li>Pass to {@link MessageProcessorService} for processing</li>
 *   <li>Return {@link RegistryResponseType} acknowledgements</li>
 * </ul>
 *
 * This endpoint is separate from {@link PixEndpoint} to adhere to the
 * Single Responsibility Principle (SRP) — PIX (ITI-8) and XDS.b PnR (ITI-41)
 * are fundamentally different transactions.
 */
@Endpoint
public class PnrEndpoint {
    private static final String NAMESPACE_URI = "urn:ihe:iti:xds-b:2007";

    private final AcknowledgementService ackService;
    private final AppConfig appConfig;
    private final TemplateLogger log;
    public PnrEndpoint(AcknowledgementService ackService, AppConfig appConfig, AppLogger appLogger) {
        this.ackService = ackService;
        this.appConfig = appConfig;
        this.log = appLogger.getLogger(PnrEndpoint.class);
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "ProvideAndRegisterDocumentSetRequest")
    @ResponsePayload
    public JAXBElement<RegistryResponseType> handleProvideAndRegister(@RequestPayload JAXBElement<ProvideAndRegisterDocumentSetRequestType> request,
                                                                     MessageContext messageContext) {
        var transportContext = TransportContextHolder.getTransportContext();
        var connection = (HttpServletConnection) transportContext.getConnection();
        HttpServletRequest httpRequest = connection.getHttpServletRequest();
        var interactionId = (String) httpRequest.getAttribute(Constants.INTERACTION_ID);
        if (StringUtils.isEmpty(interactionId)) {
            interactionId = UUID.randomUUID().toString();
        }
        try {
            log.info(interactionId,"PnrEndpoint::handleProvideAndRegister","Received ProvideAndRegisterDocumentSet-b (ITI-41) request");
            // Get raw SOAP message and build context
            String rawSoapMessage = (String) messageContext.getProperty("RAW_SOAP_MESSAGE");
            RequestContext context = buildRequestContext(rawSoapMessage, interactionId);        
            // Create response using ObjectFactory
            RegistryResponseType response = ackService.createPnrAcknowledgement("Success", interactionId);
            ObjectFactory factory = new ObjectFactory();
            JAXBElement<RegistryResponseType> jaxbResponse = factory.createRegistryResponse(response);
            httpRequest.setAttribute(Constants.REQUEST_CONTEXT, context);
            return jaxbResponse;
        } catch (Exception e) {
            log.error(interactionId,"PnrEndpoint::handleProvideAndRegister","Exception processing ITI-41 request error={}", e.getMessage(), e);
            RegistryResponseType response = ackService.createPnrAcknowledgement("Failure", interactionId);
            ObjectFactory factory = new ObjectFactory();
            return factory.createRegistryResponse(response);
        }
    }

    private RequestContext buildRequestContext(String rawSoapMessage, String interactionId) {
        ZonedDateTime uploadTime = ZonedDateTime.now();
        String timestamp = String.valueOf(uploadTime.toInstant().toEpochMilli());
        var transportContext = TransportContextHolder.getTransportContext();
        var connection = (HttpServletConnection) transportContext.getConnection();
        HttpServletRequest request = connection.getHttpServletRequest();
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String header = headerNames.nextElement();
            String value = request.getHeader(header);
            headers.put(header, value);
        }

        String tenantId = headers.getOrDefault("X-Tenant-ID", "default-tenant");
        String sourceIp = request.getRemoteAddr();
        String destinationIp = request.getLocalAddr();
        String destinationPort = String.valueOf(request.getLocalPort());
        String protocol = request.getProtocol();
        String userAgent = headers.getOrDefault("User-Agent", "");
        String datePath = uploadTime.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));

        // Keep same file naming convention as PixEndpoint
        // String fileBaseName = "soap-message";
        // String fileExtension = "xml";
        // String originalFileName = fileBaseName + "." + fileExtension;
        String objectKey = String.format("data/%s/%s_%s",
                datePath, interactionId, timestamp);
        String ackObjectKey = String.format("data/%s/%s_%s_ack",
                datePath, interactionId, timestamp);
        String metadataKey = String.format("metadata/%s/%s_%s_metadata.json",
                datePath, interactionId, timestamp);
        String fullS3DataPath = Constants.S3_PREFIX + appConfig.getAws().getS3().getBucket() + "/" + objectKey;
        String fullS3AckMessagePath = Constants.S3_PREFIX + appConfig.getAws().getS3().getBucket() + "/" + ackObjectKey;
        String fullS3MetadataPath = Constants.S3_PREFIX + appConfig.getAws().getS3().getMetadataBucket() + "/" + metadataKey;
        log.debug("PnrEndpoint:: Request context built with source IP {}, destination port {}, user-agent: {} for interactionId :{}",
                sourceIp, destinationPort, userAgent, interactionId);

        return new RequestContext(
                headers,
                request.getRequestURI(),
                tenantId,
                interactionId,
                uploadTime,
                timestamp,
                null,
                rawSoapMessage != null ? rawSoapMessage.length() : 0, // Payload size
                objectKey,
                metadataKey,
                fullS3MetadataPath,
                fullS3DataPath,
                userAgent,
                request.getRequestURL().toString(),
                request.getQueryString() == null ? "" : request.getQueryString(),
                protocol,
                destinationIp,
                sourceIp,
                sourceIp,
                destinationIp,
                destinationPort,                
                ackObjectKey,
                fullS3AckMessagePath
        );
    }
}
