package org.techbd.fhir.controller;

import static org.techbd.udi.auto.jooq.ingress.Tables.INTERACTION_HTTP_REQUEST;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.techbd.corelib.config.Configuration;
import org.techbd.corelib.config.Constants;
import org.techbd.corelib.config.CoreUdiPrimeJpaConfig;
import org.techbd.corelib.config.Helpers;
import org.techbd.corelib.service.dataledger.DataLedgerApiClient;
import org.techbd.corelib.util.CoreFHIRUtil;
import org.techbd.fhir.config.AppConfig;
import org.techbd.fhir.service.FHIRService;
import org.techbd.fhir.service.engine.OrchestrationEngine;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nonnull;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Controller
@Tag(name = "Tech by Design Hub FHIR Endpoints", description = "Tech by Design Hub FHIR Endpoints")
public class FhirController {

        private static final Logger LOG = LoggerFactory.getLogger(FhirController.class.getName());
        private final OrchestrationEngine engine;
        private final AppConfig appConfig;
        private final DataLedgerApiClient dataLedgerApiClient;
        private final CoreUdiPrimeJpaConfig coreUdiPrimeJpaConfig;
        private final FHIRService fhirService;
        private final Tracer tracer;

        public FhirController(final OrchestrationEngine engine,
        final AppConfig appConfig ,final DataLedgerApiClient dataLedgerApiClient,final FHIRService fhirService
        ,final CoreUdiPrimeJpaConfig coreUdiPrimeJpaConfig) throws IOException {
                this.appConfig = appConfig;
                this.engine = engine;
                this.fhirService = fhirService;
                this.dataLedgerApiClient = dataLedgerApiClient;
                 this.tracer = GlobalOpenTelemetry.get().getTracer("FhirController");
                this.coreUdiPrimeJpaConfig = coreUdiPrimeJpaConfig;
        }

        @GetMapping(value = "/metadata", produces = { MediaType.APPLICATION_XML_VALUE })
        @Operation(summary = "FHIR server's conformance statement")
        public String metadata(final Model model, HttpServletRequest request) {
                final var baseUrl = Helpers.getBaseUrl(request);

                model.addAttribute("version", appConfig.getVersion());
                model.addAttribute("implUrlValue", baseUrl);
                model.addAttribute("opDefnValue", baseUrl + "/OperationDefinition/Bundle--validate");

                return "metadata.xml";
        }
        @PostMapping(value = { "/Bundle/$validate", "/Bundle/$validate/" }, consumes = {
                        MediaType.APPLICATION_JSON_VALUE,
                        Constants.FHIR_CONTENT_TYPE_HEADER_VALUE })
        @Operation(summary = "Endpoint to validate but not store or forward a payload to SHIN-NY. If you want to validate a payload, store it and then forward it to SHIN-NY, use /Bundle not /Bundle/$validate.", description = "Endpoint to validate but not store or forward a payload to SHIN-NY.")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Request processed successfully", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\n"
                                        + "  \"OperationOutcome\": {\n"
                                        + "    \"validationResults\": [\n"
                                        + "      {\n"
                                        + "        \"operationOutcome\": {\n"
                                        + "          \"resourceType\": \"OperationOutcome\",\n"
                                        + "          \"issue\": [\n"
                                        + "            {\n"
                                        + "              \"severity\": \"error\",\n"
                                        + "              \"diagnostics\": \"Error Message\",\n"
                                        + "              \"location\": [\n"
                                        + "                \"Bundle.entry[0].resource/*Patient/PatientExample*/.extension[0].extension[0].value.ofType(Coding)\",\n"
                                        + "                \"Line[1] Col[5190]\"\n"
                                        + "              ]\n"
                                        + "            }\n"
                                        + "          ]\n"
                                        + "        }\n"
                                        + "      }\n"
                                        + "    ]\n"
                                        + "  }\n"
                                        + "}"))),
                        @ApiResponse(responseCode = "400", description = "Validation Error: Missing or invalid parameter", content = @Content(mediaType = "application/json", examples = {
                                        @ExampleObject(value = "{\n"
                                                        + "  \"status\": \"Error\",\n"
                                                        + "  \"message\": \"Validation Error: Required request body is missing.\"\n"
                                                        + "}"),
                                        @ExampleObject(value = "{\n"
                                                        + "  \"status\": \"Error\",\n"
                                                        + "  \"message\": \"Validation Error: Required request header 'X-TechBD-Tenant-ID' for method parameter type String is not present.\"\n"
                                                        + "}")
                        })),
                        @ApiResponse(responseCode = "500", description = "An unexpected system error occurred", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\n"
                                        + "  \"status\": \"Error\",\n"
                                        + "  \"message\": \"An unexpected system error occurred.\"\n"
                                        + "}")))
        })
        @ResponseBody
        public Object validateBundle(
                        @Parameter(description = "Payload for the API. This <b>must not</b> be <code>null</code>.", required = true) final @RequestBody @Nonnull String payload,
                        @Parameter(description = "Parameter to specify the Tenant ID. This is a <b>mandatory</b> parameter.", required = true) @RequestHeader(value = Configuration.Servlet.HeaderName.Request.TENANT_ID, required = true) String tenantId,
                        // "profile" is the same name that HL7 validator uses
                        @Parameter(hidden = true, description = "Optional parameter to decide whether the session cookie (JSESSIONID) should be deleted.", required = false) @RequestParam(value = "delete-session-cookie", required = false) Boolean deleteSessionCookie,
                        @Parameter(description = "Optional header to specify the base FHIR URL. If provided, it will be used in the generated FHIR; otherwise, the default value will be used.", required = false) @RequestHeader(value = "X-TechBD-Base-FHIR-URL", required = false) String baseFHIRURL,
                        @Parameter(description = "Optional header to specify IG version.", required = false) @RequestHeader(value = "X-SHIN-NY-IG-Version", required = false) String requestedIgVersion,
                        @Parameter(description = "Optional header to specify source type.", required = false) @RequestHeader(value = "X-TechBD-Source-Type", required = false) String sourceType,
                        @Parameter(description = "Optional header to specify master interaction ID.", required = false) @RequestHeader(value = "X-TechBD-Master-Interaction-ID", required = false) String masterInteractionId,
                        @Parameter(description = "Optional header to specify group interaction ID.", required = false) @RequestHeader(value = "X-TechBD-Group-Interaction-ID", required = false) String groupInteractionId,
                        @Parameter(description = "Optional header to specify interaction id otherwise, the default value will be used.", required = false) @RequestHeader(value = "X-TechBD-Interaction-ID", required = false) String interactionId,
                        HttpServletRequest request, HttpServletResponse response) throws IOException {
                Span span = tracer.spanBuilder("FhirController.validateBundle").startSpan();
                try {

                        if (tenantId == null || tenantId.trim().isEmpty()) {
                                LOG.error("FHIRController:Bundle Validate:: Tenant ID is missing or empty");
                                throw new IllegalArgumentException("Tenant ID must be provided");
                        }

                        if (Boolean.TRUE.equals(deleteSessionCookie)) {
                                deleteJSessionCookie(request, response);
                        }
                        // request = new CustomRequestWrapper(request, payload);
                        Map<String, Object> headers = CoreFHIRUtil.buildHeaderParametersMap(tenantId, null, null,
                                        null, null, null, null, null,requestedIgVersion );
                        Map <String,Object> requestDetailsMap = CoreFHIRUtil.extractRequestDetails(request);            
                        LOG.debug("FhirController.validateBundle - sourceType: '{}', groupInteractionId: '{}', masterInteractionId: '{}'", 
                                sourceType, groupInteractionId, masterInteractionId);
                        CoreFHIRUtil.buildRequestParametersMap(requestDetailsMap,deleteSessionCookie,
                                        null, sourceType,
                                        groupInteractionId, masterInteractionId, request.getRequestURI());
                        if (StringUtils.isEmpty(interactionId)) {
                                interactionId = UUID.randomUUID().toString();
                        }
                        requestDetailsMap.put(Constants.INTERACTION_ID,interactionId);
                        requestDetailsMap.put(Constants.OBSERVABILITY_METRIC_INTERACTION_START_TIME, Instant.now().toString());
                        requestDetailsMap.putAll(headers);
                        Map<String, Object> responseParameters = new HashMap<>();
                        final var result = fhirService.processBundle(payload, requestDetailsMap,  responseParameters);
                        CoreFHIRUtil.addCookieAndHeadersToResponse(response, responseParameters, requestDetailsMap);
                        return result;
                } finally {
                        span.end();
                }
        }

        @GetMapping(value = "/Bundle/$status/{bundleSessionId}", produces = { "application/json", "text/html" })
        @ResponseBody
        @Operation(summary = "Check the state/status of async operation")
        public Object bundleStatus(
                        @Parameter(description = "<b>mandatory</b> path variable to specify the bundle session ID.", required = true) @PathVariable String bundleSessionId,
                        final Model model, HttpServletRequest request) {
                final var jooqDSL = coreUdiPrimeJpaConfig.dsl();
                try {
                        final var result = jooqDSL.select()
                                        .from(INTERACTION_HTTP_REQUEST)
                                        .where(INTERACTION_HTTP_REQUEST.INTERACTION_ID.eq(bundleSessionId))
                                        .fetch();
                        return Configuration.objectMapper.writeValueAsString(result.intoMaps());
                } catch (Exception e) {
                        LOG.error("Error executing JOOQ query for retrieving SAT_INTERACTION_HTTP_REQUEST.HUB_INTERACTION_ID for "
                                        + bundleSessionId, e);
                        return String.format("""
                                          "error": "%s",
                                          "bundleSessionId": "%s"
                                        """.replace("\n", "%n"), e.toString(), bundleSessionId);
                }
        }
        private void deleteJSessionCookie(HttpServletRequest request, HttpServletResponse response) {
                // Delete the JSESSIONID cookie
                Cookie cookie = new Cookie("JSESSIONID", null); // Set the cookie name
                cookie.setMaxAge(0); // Make it expire immediately
                cookie.setPath("/"); // Set the same path as the original cookie
                response.addCookie(cookie); // Add it to the response to delete
        }

}
