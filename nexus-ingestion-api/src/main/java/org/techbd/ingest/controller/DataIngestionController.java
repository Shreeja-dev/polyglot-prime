package org.techbd.ingest.controller;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.techbd.ingest.commons.AppLogger;
import org.techbd.ingest.commons.Constants;
import org.techbd.ingest.commons.TemplateLogger;
import org.techbd.ingest.config.AppConfig;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.service.MessageProcessorService;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Controller for handling data ingestion requests.
 * This controller processes file uploads and string content ingestion.
 */
@RestController
public class DataIngestionController {
    private static final Logger LOG = LoggerFactory.getLogger(DataIngestionController.class.getName()); 
    private static final DateTimeFormatter DATE_PATH_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private final MessageProcessorService messageProcessorService;
    private final ObjectMapper objectMapper;
    private final AppConfig appConfig;
    private final TemplateLogger log;

    public DataIngestionController(MessageProcessorService messageProcessorService, ObjectMapper objectMapper, AppConfig appConfig,AppLogger appLogger) {
        this.messageProcessorService = messageProcessorService;
        this.objectMapper = objectMapper;
        this.appConfig = appConfig;
        this.log = appLogger.getLogger(DataIngestionController.class);
        LOG.info("DataIngestionController initialized");
    }

    /**
     * Health check endpoint using HEAD method on /ingest endpoint.
     *
     * @return A response entity indicating service health status.
     */
    @RequestMapping(value = "/", method = RequestMethod.GET)
    public ResponseEntity<Void> healthCheck() {
        try {
            LOG.info("Health check requested via HEAD /ingest");
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            LOG.error("Health check failed", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }

    /**
     * Endpoint to handle ingestion requests.
     *
     * This endpoint can accept either:
     * - a file upload (multipart/form-data)
     * - raw data in the body (JSON, XML, plain text, HL7, etc.)
     *
     * If raw body data is provided, a filename is generated
     * based on the Content-Type header.
     *
     * @param file    The optional file to be ingested (when multipart/form-data).
     * @param body    The optional raw payload (when Content-Type is JSON/XML/Text).
     * @param headers The request headers containing metadata.
     * @param request The HTTP servlet request.
     * @return A response entity containing the result of the ingestion process.
     * @throws Exception If an error occurs during processing.
     */
    @PostMapping(value = "/ingest", consumes = { MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.ALL_VALUE })
    public ResponseEntity<String> ingest(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestBody(required = false) String body,
            @RequestHeader Map<String, String> headers,
            HttpServletRequest request) throws Exception {
        String interactionId = (String) request.getAttribute(Constants.INTERACTION_ID);
        log.info(interactionId ,"DataIngestionController::ingest","Received ingest request");

        Map<String, String> responseMap;

        if (file != null && !file.isEmpty()) {
            log.info(interactionId ,"DataIngestionController::ingest","File received: {} ({} bytes)",
                    file.getOriginalFilename(), file.getSize());
            RequestContext context = createRequestContext(interactionId,
                    headers, request, file.getSize(), file.getOriginalFilename());
            responseMap = messageProcessorService.processMessage(context, file);

        } else if (body != null && !body.isBlank()) {
            String contentType = request.getContentType();
           // String extension = resolveExtension(contentType);
           // String generatedFileName = interactionId + extension;
           log.info(interactionId,"DataIngestionController::ingest","Raw body received (Content-Type={}): {}...",
                   contentType);
            RequestContext context = createRequestContext(interactionId,
                    headers, request, body.length(), null);
            responseMap = messageProcessorService.processMessage(context, body);

        } else {
            log.warn(interactionId, "DataIngestionController::ingest", "Neither file nor body provided.");

            throw new IllegalArgumentException("Request must contain either a file or body data");
        }
        log.info(interactionId, "DataIngestionController::ingest", "Ingestion processed successfully.");
        String responseJson = objectMapper.writeValueAsString(responseMap);
        log.info(interactionId, "DataIngestionController::ingest","Returning response ");
        return ResponseEntity.ok(responseJson);
    }

/**
 * Resolve file extension based on Content-Type.
 */
private String resolveExtension(String contentType) {
    if (contentType == null) return ".dat";

    return switch (contentType.toLowerCase()) {
        case MediaType.APPLICATION_JSON_VALUE -> ".json";
        case MediaType.APPLICATION_XML_VALUE, MediaType.TEXT_XML_VALUE -> ".xml";
        case MediaType.TEXT_PLAIN_VALUE -> ".txt";
        case "application/hl7-v2" -> ".hl7";
        default -> ".dat"; // fallback
    };
}


    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleException(Exception e) {
        // Try to extract interactionId from exception or context if possible
        String interactionId = "unknown";
       log.error(interactionId, "DataIngestionController::handleException", 
          "Error processing request.", e);
        Map<String, String> error = Map.of("error", e.getMessage());
        try {
            String errorJson = objectMapper.writeValueAsString(error);
            log.info(interactionId, "DataIngestionController::handleException","Returning BAD_REQUEST");
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(errorJson);
        } catch (Exception ex) {
            log.error(interactionId, "DataIngestionController::handleException","Error serializing error response.", ex);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\":\"Internal server error\"}");
        }
    }
    /**
     * Function to handle string content ingestion requests.
     *
     * @param content The string content to be ingested.
     * @param headers The request headers containing metadata.
     * @param request The HTTP servlet request.
     * @return A response entity containing the result of the ingestion process.
     * @throws Exception If an error occurs during processing.
     */
    private RequestContext createRequestContext(
            String interactionId,
            Map<String, String> headers,
            HttpServletRequest request,
            long fileSize,
            String originalFileName) {
        log.info(interactionId, "DataIngestionController::createRequestContext", "Creating RequestContext.");
        String sourceIp = null;
        var tenantId = headers.get(Constants.REQ_HEADER_TENANT_ID);
        final var xForwardedFor = headers.get(Constants.REQ_HEADER_X_FORWARDED_FOR);
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            sourceIp = xForwardedFor.split(",")[0].trim();
        } else {
            sourceIp = headers.get(Constants.REQ_HEADER_X_REAL_IP);
        }

        // Extract destination IP and port
        final var destinationIp = headers.get(Constants.REQ_X_SERVER_IP);
        final var destinationPort = headers.get(Constants.REQ_X_SERVER_PORT);
        if (tenantId == null || tenantId.trim().isEmpty()) {
            tenantId = Constants.TENANT_ID;
        }
        if (tenantId == null || tenantId.trim().isEmpty()) {
            tenantId = Constants.DEFAULT_TENANT_ID;
        }
        log.info(interactionId, "DataIngestionController::createRequestContext", "Request Headers - tenantId: {}, xForwardedFor: {}, xRealIp: {}, sourceIp: {}, destinationIp: {}, destinationPort: {}, interactionId: {}",
        headers.get(Constants.REQ_HEADER_TENANT_ID),
        headers.get(Constants.REQ_HEADER_X_FORWARDED_FOR),
        headers.get(Constants.REQ_HEADER_X_REAL_IP),
        sourceIp,
        destinationIp,
        destinationPort,interactionId);

        Instant now = Instant.now();
        String timestamp = String.valueOf(now.toEpochMilli());
        ZonedDateTime uploadTime = now.atZone(ZoneOffset.UTC);
        String datePath = uploadTime.format(DATE_PATH_FORMATTER);
        String objectKey = String.format("data/%s/%s_%s",
                datePath, interactionId, timestamp);
        String metadataKey = String.format("metadata/%s/%s_%s_metadata.json",
                datePath, interactionId, timestamp);
        String fullS3Path = Constants.S3_PREFIX + appConfig.getAws().getS3().getBucket() + "/" + objectKey;
        String fullMetaDataObjectPath = Constants.S3_PREFIX + appConfig.getAws().getS3().getMetadataBucket() + "/" + metadataKey;
        String userAgent = headers.getOrDefault(Constants.REQ_HEADER_USER_AGENT, Constants.DEFAULT_USER_AGENT);
        String fullRequestUrl = request.getRequestURL().toString();
        String queryParams = request.getQueryString();
        String protocol = request.getProtocol();
        String localAddress = request.getLocalAddr();
        String remoteAddress = request.getRemoteAddr();

        log.info(interactionId,"DataIngestionController::RequestContext" ,"RequestContext built");

        return new RequestContext(
                headers,
                request.getRequestURI(),
                tenantId,
                interactionId,
                uploadTime,
                timestamp,
                originalFileName,
                fileSize,
                objectKey,
                metadataKey,
                fullMetaDataObjectPath,
                fullS3Path,
                userAgent,
                fullRequestUrl,
                queryParams,
                protocol,
                localAddress,
                remoteAddress,
                sourceIp,
                destinationIp,
                destinationPort,null,null
        );
    }
}
