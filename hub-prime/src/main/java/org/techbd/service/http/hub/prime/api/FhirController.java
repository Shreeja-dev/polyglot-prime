package org.techbd.service.http.hub.prime.api;

import static org.techbd.udi.auto.jooq.ingress.Tables.INTERACTION_HTTP_REQUEST;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
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
import org.techbd.conf.Configuration;
import org.techbd.config.Constants;
import org.techbd.config.CoreAppConfig;
import org.techbd.config.CoreUdiPrimeJpaConfig;
import org.techbd.config.CoreUdiSecondaryJpaConfig;
import org.techbd.service.dataledger.CoreDataLedgerApiClient;
import org.techbd.service.fhir.FHIRService;
import org.techbd.service.fhir.FhirReplayService;
import org.techbd.service.fhir.engine.OrchestrationEngine;
import org.techbd.service.http.Helpers;
import org.techbd.service.http.hub.CustomRequestWrapper;
import org.techbd.util.FHIRUtil;
import org.techbd.util.fhir.CoreFHIRUtil;

import io.micrometer.common.util.StringUtils;
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
        private final CoreAppConfig appConfig;
        private final CoreDataLedgerApiClient dataLedgerApiClient;
        private final DSLContext primaryDslContext;
        private final DSLContext secondaryDslContext;
        private final FHIRService fhirService;
        private final FhirReplayService fhirReplayService;
        private final Tracer tracer;

        public FhirController(final Tracer tracer, final OrchestrationEngine engine,
                        final CoreAppConfig appConfig, final CoreDataLedgerApiClient dataLedgerApiClient,
                        final FHIRService fhirService, final FhirReplayService fhirReplayService,

                        @Qualifier("primaryDslContext") DSLContext primaryDslContext,
                        @Qualifier("secondaryDslContext") DSLContext secondaryDslContext) throws IOException {
                // String activeProfile = System.getenv("SPRING_PROFILES_ACTIVE");
                // appConfig = ConfigLoader.loadConfig(activeProfile);
                // this.fhirService = new FHIRService();
                // fhirService.setAppConfig(appConfig);
                // org.techbd.util.fhir.FHIRUtil.initialize(appConfig);
                // dataLedgerApiClient = new DataLedgerApiClient(appConfig);
                // OrchestrationEngine engine = new OrchestrationEngine(appConfig);
                // fhirService.setDataLedgerApiClient(dataLedgerApiClient);
                // fhirService.setEngine(engine);
                this.appConfig = appConfig;
                this.engine = engine;
                this.fhirService = fhirService;
                this.dataLedgerApiClient = dataLedgerApiClient;
                this.tracer = tracer;
                this.primaryDslContext = primaryDslContext;
                this.secondaryDslContext = secondaryDslContext;
                this.fhirReplayService = fhirReplayService;
        }

        @GetMapping(value = "/metadata", produces = { MediaType.APPLICATION_XML_VALUE })
        @Operation(summary = "FHIR server's conformance statement")
        public String metadata(final Model model, HttpServletRequest request) {
                final var baseUrl = Helpers.getBaseUrl(request);
                final var jooqCfg = primaryDslContext.configuration();
                final var jooqCfg1 = secondaryDslContext.configuration();
                model.addAttribute("version", appConfig.getVersion());
                model.addAttribute("implUrlValue", baseUrl);
                model.addAttribute("opDefnValue", baseUrl + "/OperationDefinition/Bundle--validate");

                return "metadata.xml";
        }
     
        @GetMapping(value = "/Bundle/$statusDsl2/{bundleSessionId}", produces = { "application/json", "text/html" })
        @ResponseBody
        @Operation(summary = "Check the state/status of async operation")
        public Object bundleStatus2(
                        @Parameter(description = "<b>mandatory</b> path variable to specify the bundle session ID.", required = true) @PathVariable String bundleSessionId,
                        final Model model, HttpServletRequest request) {
                final var jooqCfg = secondaryDslContext.configuration();
                try {
                        final var result = jooqCfg.dsl().select()
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

        
        @GetMapping(value = "/Bundle/$statusDsl1/{bundleSessionId}", produces = { "application/json", "text/html" })
        @ResponseBody
        @Operation(summary = "Check the state/status of async operation")
        public Object bundleStatus(
                        @Parameter(description = "<b>mandatory</b> path variable to specify the bundle session ID.", required = true) @PathVariable String bundleSessionId,
                        final Model model, HttpServletRequest request) {
                final var jooqCfg = primaryDslContext.configuration();
                try {
                        final var result = jooqCfg.dsl().select()
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

}
