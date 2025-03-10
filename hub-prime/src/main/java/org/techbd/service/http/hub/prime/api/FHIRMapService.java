package org.techbd.service.http.hub.prime.api;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.rmi.UnexpectedException;
import java.security.Security;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.commons.collections4.CollectionUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.techbd.conf.Configuration;
import org.techbd.orchestrate.fhir.OrchestrationEngine;
import org.techbd.orchestrate.fhir.OrchestrationEngine.Device;
import org.techbd.service.constants.ErrorCode;
import org.techbd.service.constants.SourceType;
import org.techbd.service.exception.JsonValidationException;
import org.techbd.service.http.InteractionsFilter;
import org.techbd.service.http.InteractionsMap;
//import org.techbd.service.http.Interactions;
import org.techbd.service.http.InteractionsMap.RequestEncountered;
import org.techbd.service.http.InteractionsMap.RequestResponseEncountered;
import org.techbd.service.http.hub.prime.AppConfig;
import org.techbd.service.http.hub.prime.AppConfig.DefaultDataLakeApiAuthn;
import org.techbd.service.http.hub.prime.AppConfig.MTlsAwsSecrets;
import org.techbd.service.http.hub.prime.AppConfig.MTlsResources;
import org.techbd.service.http.hub.prime.AppConfig.PostStdinPayloadToNyecDataLakeExternal;
import org.techbd.udi.MirthJooqConfig;
import org.techbd.udi.auto.jooq.ingress.routines.RegisterInteractionHttpRequest;
import org.techbd.util.FHIRUtil;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.common.util.StringUtils;
import io.netty.handler.ssl.SslContextBuilder;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import jakarta.annotation.Nonnull;
import lombok.Setter;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;

@Service
@Setter
public class FHIRMapService {
	private static final String START_TIME_ATTRIBUTE = "startTime";
	private static final Logger LOG = LoggerFactory.getLogger(FHIRMapService.class.getName());
	private AppConfig appConfig;
    private Tracer tracer;
    private final OrchestrationEngine engine = new OrchestrationEngine(tracer);;
	@Value("${org.techbd.service.http.interactions.default-persist-strategy:#{null}}")
	private String defaultPersistStrategy;

	@Value("${org.techbd.service.http.interactions.saveUserDataToInteractions:true}")
	private boolean saveUserDataToInteractions;
    
    //private Map<String,String> requestParameters;
    // private Map<String,String> responseParameters;

    public FHIRMapService() {
        try {
            this.tracer = GlobalOpenTelemetry.getTracer("FHIRMapService");
        } catch (NoClassDefFoundError | Exception e) {
            System.err.println("Warning: OpenTelemetry not available, using No-Op tracer.");
            this.tracer = GlobalOpenTelemetry.get().getTracer("no-op-tracer");  // ✅ Fix: Use OpenTelemetry's No-Op tracer
        }
    }



	public Object processBundle(final @RequestBody @Nonnull String payload,
                            String tenantId,
                            String customDataLakeApi,
                            String dataLakeApiContentType,
                            String healthCheck,
                            boolean isSync,
                            Map<String, String> requestParameters,
                            Map<String, String> responseParameters,
                            String provenance,
                            String mtlsStrategy,
                            String interactionId,
                            String groupInteractionId,
                            String masterInteractionId,
                            String sourceType,
                            String requestUriToBeOverriden,
                            String coRrelationId)
        throws IOException {
    Span span = tracer.spanBuilder("FHIRMapService.processBundle").startSpan();
    try {
        final var start = Instant.now();
        LOG.info("Bundle processing start at {} for interaction id {}.",
                 start, getBundleInteractionId(requestParameters, coRrelationId));
        if (null == interactionId) {
            interactionId = getBundleInteractionId(requestParameters, coRrelationId);
        }
        final var dslContext = MirthJooqConfig.dsl();
        final var jooqCfg = dslContext.configuration();
        Map<String, Object> payloadWithDisposition = null;

        try {
            validateJson(payload, interactionId);
            validateBundleProfileUrl(payload, interactionId);
            if (null == dataLakeApiContentType) {
                dataLakeApiContentType = MediaType.APPLICATION_JSON_VALUE;
            }

            Map<String, Object> immediateResult = validate(requestParameters, payload, 
						
                                                interactionId, provenance, sourceType);
            final var result = Map.of("OperationOutcome", immediateResult);
            if ("true".equals(healthCheck)) {
                LOG.info("%s is true, skipping Scoring Engine submission."
							.formatted(AppConfig.Servlet.HeaderName.Request.HEALTH_CHECK_HEADER));
                return result; // Return without proceeding to scoring engine submission
            }
if (!SourceType.CSV.name().equals(sourceType)) {
            addObservabilityHeadersToResponse(requestParameters, responseParameters);
}
            payloadWithDisposition = registerBundleInteraction(jooqCfg, requestParameters,
            responseParameters, payload, result, interactionId, groupInteractionId,
						masterInteractionId, sourceType, requestUriToBeOverriden,
						coRrelationId);
            if (isActionDiscard(payloadWithDisposition)) {
                return payloadWithDisposition;
            }
            if (null == payloadWithDisposition) {
                LOG.warn(
							"FHIRMapService:: ERROR:: Disposition payload is not available.Send Bundle payload to scoring engine for interaction id {}.",
                         getBundleInteractionId(requestParameters, coRrelationId));
                
                // TODO: UNCOMMENT folllowing method call  sendToScoringEngine 
                // sendToScoringEngine(jooqCfg, requestParameters, customDataLakeApi, dataLakeApiContentType,
                                    
                //                                         tenantId, payload,
				// 			provenance, null, 
                //                                          mtlsStrategy,
                //                     interactionId, groupInteractionId, masterInteractionId,
                //                     sourceType, requestUriToBeOverriden, coRrelationId);

                Instant end = Instant.now();
                Duration timeElapsed = Duration.between(start, end);
                LOG.info("Bundle processing end for interaction id: {} Time Taken: {} milliseconds",
                         interactionId, timeElapsed.toMillis());
                return result;
            } else {
                LOG.info("FHIRMapService:: Received Disposition payload. " +
                         "Sending Disposition payload to scoring engine for interaction id {}.",
                         interactionId);

                 sendToScoringEngine(jooqCfg, requestParameters, customDataLakeApi, dataLakeApiContentType,
                                    tenantId, payload, provenance, payloadWithDisposition,
                                    mtlsStrategy, interactionId, groupInteractionId,
                                    masterInteractionId, sourceType, requestUriToBeOverriden, coRrelationId);

                Instant end = Instant.now();
                Duration timeElapsed = Duration.between(start, end);
                LOG.info("Bundle processing end for interaction id: {} Time Taken: {} milliseconds",
                         interactionId, timeElapsed.toMillis());
                return payloadWithDisposition;
            }
        } catch (JsonValidationException ex) {            
            payloadWithDisposition = registerBundleInteraction(jooqCfg, requestParameters,responseParameters,
                                                               payload, buildOperationOutcome(ex, interactionId),
                                                               interactionId, groupInteractionId, masterInteractionId,
                                                               sourceType, requestUriToBeOverriden, coRrelationId);
                                    
        }

        Instant end = Instant.now();
        Duration timeElapsed = Duration.between(start, end);
        LOG.info("Bundle processing end for interaction id: {} Time Taken: {} milliseconds",
                 interactionId, timeElapsed.toMillis());
        return payloadWithDisposition;
    } finally {
        span.end();
    }
}

	@SuppressWarnings("unchecked")
	public static boolean isActionDiscard(Map<String, Object> payloadWithDisposition) {
		return Optional.ofNullable(payloadWithDisposition)
				.map(map -> (Map<String, Object>) map.get("OperationOutcome"))
				.map(outcome -> (List<Map<String, Object>>) outcome.get("techByDesignDisposition"))
				.flatMap(dispositions -> dispositions.stream()
						.map(disposition -> (String) disposition.get("action"))
						.filter(TechByDesignDisposition.DISCARD.action::equals)
						.findFirst())
				.isPresent();
	}

	public void validateJson(String jsonString, String interactionId) {
		Span validateJsonSpan = tracer.spanBuilder("FHIRMapService.validateJson").startSpan();
		try {
			try {
				Configuration.objectMapper.readTree(jsonString);
			} catch (Exception e) {
				throw new JsonValidationException(ErrorCode.INVALID_JSON);
			}
		} finally {
			validateJsonSpan.end();
		}

	}

	public void validateBundleProfileUrl(String jsonString, String interactionId) {
		Span validateJsonSpan = tracer.spanBuilder("FHIRMapService.validateBundleProfileUrl").startSpan();
		try {
			JsonNode rootNode;
			try {
				rootNode = Configuration.objectMapper.readTree(jsonString);
				JsonNode metaNode = rootNode.path("meta").path("profile");

				List<String> profileList = Optional.ofNullable(metaNode)
						.filter(JsonNode::isArray)
						.map(node -> StreamSupport.stream(node.spliterator(), false)
								.map(JsonNode::asText)
								.collect(Collectors.toList()))
						.orElse(List.of());

				if (CollectionUtils.isEmpty(profileList)) {
					LOG.error("Bundle profile is not provided for interaction id: {}", interactionId);
					throw new JsonValidationException(ErrorCode.BUNDLE_PROFILE_URL_IS_NOT_PROVIDED);
				}

				List<String> allowedProfileUrls = FHIRUtil.getAllowedProfileUrls(appConfig);
				if (profileList.stream().noneMatch(allowedProfileUrls::contains)) {
					LOG.error("Bundle profile URL provided is not valid for interaction id: {}", interactionId);
					throw new JsonValidationException(ErrorCode.INVALID_BUNDLE_PROFILE);
				}
			} catch (JsonProcessingException e) {
				LOG.error("Json Processing exception while extracting profile url for interaction id :{}", e);
			}

		} finally {
			validateJsonSpan.end();
		}

	}

	public static Map<String, Map<String, Object>> buildOperationOutcome(JsonValidationException ex,
			String interactionId) {
		var validationResult = Map.of(
				"valid", false,
				"issues", List.of(Map.of(
						"message", ex.getErrorCode() + ": " + ex.getMessage(),
						"severity", "FATAL")));
		var operationOutcome = new HashMap<String, Object>(Map.of(
				"resourceType", "OperationOutcome",
				"bundleSessionId", interactionId,
				"validationResults", List.of(validationResult)));
		return Map.of("OperationOutcome", operationOutcome);

	}

	private void addObservabilityHeadersToResponse(Map<String, String> requestParameters, Map<String, String> responseParameters) {
        final var startTimeText = requestParameters.get(START_TIME_ATTRIBUTE);
        final var finishTime = Instant.now();
    
        if (startTimeText == null) {
            LOG.warn("START_TIME_ATTRIBUTE is missing in request parameters. Skipping observability headers.");
            return;
        }
    
        final var startTime = Instant.parse(startTimeText);
        final Duration duration = Duration.between(startTime, finishTime);
    
        final String finishTimeText = finishTime.toString();
        final String durationMsText = String.valueOf(duration.toMillis());
        final String durationNsText = String.valueOf(duration.toNanos());
    
        // Store observability headers in the responseParameters map
        responseParameters.put("X-Observability-Metric-Interaction-Start-Time", startTimeText);
        responseParameters.put("X-Observability-Metric-Interaction-Finish-Time", finishTimeText);
        responseParameters.put("X-Observability-Metric-Interaction-Duration-Nanosecs", durationNsText);
        responseParameters.put("X-Observability-Metric-Interaction-Duration-Millisecs", durationMsText);
    
        // Simulate setting a cookie by storing it in responseParameters
        try {
            String metricCookieValue = URLEncoder.encode("{ \"startTime\": \"" + startTimeText
                    + "\", \"finishTime\": \"" + finishTimeText
                    + "\", \"durationMillisecs\": \"" + durationMsText
                    + "\", \"durationNanosecs\": \"" + durationNsText + "\" }",
                    StandardCharsets.UTF_8.toString());
    
            responseParameters.put("Set-Cookie", "Observability-Metric-Interaction-Active=" + metricCookieValue + "; Path=/; HttpOnly=false");
        } catch (UnsupportedEncodingException ex) {
            LOG.error("Exception during setting Observability-Metric-Interaction-Active cookie in response parameters", ex);
        }
    }
    
    

	private Map<String, Object> registerBundleInteraction(org.jooq.Configuration jooqCfg,
            Map<String, String> requestParameters, Map<String, String> responseParameters,
			String payload, Map<String, Map<String, Object>> validationResult, String interactionId,
			String groupInteractionId, String masterInteractionId, String sourceType,
			String requestUriToBeOverriden, String coRrelationId)
			throws IOException {
		Span span = tracer.spanBuilder("FHIRMapService.registerBundleInteraction").startSpan();
		try {
			final InteractionsMap interactions = new InteractionsMap();
			//final var mutatableReq = new ContentCachingRequestWrapper(requestParameters);
			RequestEncountered requestEncountered = null;
			if (StringUtils.isNotEmpty(coRrelationId)) {
				requestEncountered = new InteractionsMap.RequestEncountered(requestParameters,
						payload.getBytes(),
						UUID.fromString(coRrelationId));
			} else if (null != interactionId) {
				// If its a converted CSV payload ,it will already have an interaction id.hence
				// do not create new interactionId
				requestEncountered = new InteractionsMap.RequestEncountered(requestParameters,
						payload.getBytes(),
						UUID.fromString(interactionId));
			} else if (null != getBundleInteractionId(requestParameters, coRrelationId)) {
				// If its a converted HL7 payload ,it will already have an interaction id.hence
				// do not create new interactionId
				requestEncountered = new InteractionsMap.RequestEncountered(requestParameters,
						payload.getBytes(),
						UUID.fromString(getBundleInteractionId(requestParameters, coRrelationId)));
			} else {
				requestEncountered = new InteractionsMap.RequestEncountered(requestParameters,
						payload.getBytes());
			}
			//final var mutatableResp = new ContentCachingResponseWrapper(responseParameters);
			setActiveRequestEnc(requestParameters, requestEncountered);
			final var rre = new InteractionsMap.RequestResponseEncountered(requestEncountered, 
					new InteractionsMap.ResponseEncountered(responseParameters, requestEncountered,
							Configuration.objectMapper
									.writeValueAsBytes(validationResult)));
                                    
			interactions.addHistory(rre);
			setActiveInteraction(requestParameters, rre);
			final var provenance = "%s.doFilterInternal".formatted(FHIRMapService.class.getName());
			final var rihr = new RegisterInteractionHttpRequest();

			try {
				prepareRequest(rihr, rre, provenance, requestParameters, interactionId, groupInteractionId,
						masterInteractionId, sourceType, requestUriToBeOverriden);
				final var start = Instant.now();
				rihr.execute(jooqCfg);
				final var end = Instant.now();
				LOG.info(
						"FHIRMapService  - Time taken : {} milliseconds for DB call to REGISTER State None, Accept, Disposition: for interaction id: {} ",
						Duration.between(start, end).toMillis(),
						rre.interactionId().toString());
				JsonNode payloadWithDisposition = rihr.getReturnValue();
				LOG.info("REGISTER State None, Accept, Disposition: END for interaction id: {} ",
						rre.interactionId().toString());
				return Configuration.objectMapper.convertValue(payloadWithDisposition,
						new TypeReference<Map<String, Object>>() {
						});
			} catch (Exception e) {
				LOG.error(
						"ERROR:: REGISTER State None, Accept, Disposition: for interaction id: {} tenant id: {}: CALL "
								+ rihr.getName() + " error",
						rre.interactionId().toString(),
						rre.tenant(), e);
			}
			return null;
		} finally

		{
			span.end();
		}
	}
 
	private void prepareRequest(RegisterInteractionHttpRequest rihr, RequestResponseEncountered rre,
			String provenance, Map<String, String> requestParameters, String interactionId, String groupInteractionId,
			String masterInteractionId, String sourceType, String requestUriToBeOverriden) {
		LOG.info("REGISTER State None, Accept, Disposition: BEGIN for interaction id: {} tenant id: {}",
				rre.interactionId().toString(), rre.tenant());
		rihr.setInteractionId(interactionId != null ? interactionId : rre.interactionId().toString());
		rihr.setGroupHubInteractionId(groupInteractionId);
		rihr.setSourceHubInteractionId(masterInteractionId);
		rihr.setNature((JsonNode) Configuration.objectMapper.valueToTree(
				Map.of("nature", RequestResponseEncountered.class.getName(),
						"tenant_id",
						rre.tenant() != null ? (rre.tenant().tenantId() != null
								? rre.tenant().tenantId()
								: "N/A") : "N/A")));
		rihr.setContentType(MimeTypeUtils.APPLICATION_JSON_VALUE);
		rihr.setInteractionKey(StringUtils.isNotEmpty(requestUriToBeOverriden) ? requestUriToBeOverriden
				: requestParameters.get("URI"));
		rihr.setPayload((JsonNode) Configuration.objectMapper.valueToTree(rre));
		rihr.setCreatedAt(OffsetDateTime.now());
		rihr.setCreatedBy(InteractionsFilter.class.getName());
		rihr.setSourceType(sourceType);
		rihr.setProvenance(provenance);
		// if (saveUserDataToInteractions) {
		// 	setUserDetails(rihr, requestParameters);
		// } else {
		// 	LOG.info("User details are not saved with Interaction as saveUserDataToInteractions: "
		// 			+ saveUserDataToInteractions);
		// }
	}

	
    // private void setUserDetails(RegisterInteractionHttpRequest rihr, HttpServletRequest request) {
	// 	var curUserName = "API_USER";
	// 	var gitHubLoginId = "N/A";
	// 	final var sessionId = request.getRequestedSessionId();
	// 	var userRole = "API_ROLE";
	// 	if (!Constant.isStatelessApiUrl(request.getRequestURI())) { // Call only if not a stateless URL
	// 		final var curUser = GitHubUserAuthorizationFilter.getAuthenticatedUser(request);
	// 		if (curUser.isPresent()) {
	// 			final var ghUser = curUser.get().ghUser();
	// 			if (ghUser != null) {
	// 				curUserName = Optional.ofNullable(ghUser.name()).orElse("NO_DATA");
	// 				gitHubLoginId = Optional.ofNullable(ghUser.gitHubId()).orElse("NO_DATA");
	// 				userRole = curUser.get().principal().getAuthorities().stream()
	// 						.map(GrantedAuthority::getAuthority)
	// 						.collect(Collectors.joining(","));
	// 				LOG.info("userRole: " + userRole);
	// 				userRole = "DEFAULT_ROLE"; // TODO -set user role
	// 			}
	// 		}
	// 	}
	// 	rihr.setUserName(curUserName);
	// 	rihr.setUserId(gitHubLoginId);
	// 	rihr.setUserSession(sessionId);
	// 	rihr.setUserRole(userRole);
	// }

	protected static final void setActiveRequestTenant(final @NonNull Map<String, String> requestParameters,
			final @NonNull InteractionsMap.Tenant tenant) {
		requestParameters.put("activeHttpRequestTenant", tenant.tenantId());
	}

	private void setActiveRequestEnc(final @NonNull Map<String, String> requestParameters,
			final @NonNull InteractionsMap.RequestEncountered re) {
		requestParameters.put("activeHttpRequestEncountered", re.requestId().toString()); // Store requestId as a string
		setActiveRequestTenant(requestParameters, re.tenant()); // Store tenant ID separately
	}

	private void setActiveInteraction(final @NonNull Map<String, String> requestParameters,
			final @NonNull InteractionsMap.RequestResponseEncountered rre) {
		requestParameters.put("activeHttpInteraction", rre.interactionId().toString()); // Store interaction ID as a string
	}

	public Map<String, Object> validate(Map<String, String> requestParameters, 
                                     String payload, 
                                     String interactionId, 
                                     String provenance, 
                                     String sourceType) {
    Span span = tracer.spanBuilder("FHIRMapService.validate").startSpan();
    try {
        final var start = Instant.now();
        LOG.info("FHIRMapService  - Validate -BEGIN for interactionId: {} ", interactionId);
        final var igPackages = appConfig.getIgPackages();
        final var igVersion = appConfig.getIgVersion();
        final var sessionBuilder = engine.session()
                .withSessionId(UUID.randomUUID().toString())
                .onDevice(Device.createDefault())
                .withInteractionId(interactionId)
                .withPayloads(List.of(payload))
                .withFhirProfileUrl(FHIRUtil.getBundleProfileUrl())
                .withTracer(tracer)
                .withFhirIGPackages(igPackages)
                .withIgVersion(igVersion)
                .addHapiValidationEngine(); // by default
					// clearExisting is set to true so engines can be fully supplied through header
        
        final var session = sessionBuilder.build();
        try {
            engine.orchestrate(session);
            // TODO: if there are errors that should prevent forwarding, stop here
            // TODO: need to implement `immediate` (sync) webClient op, right now it's async
            // only
            // immediateResult is what's returned to the user while async operation
            // continues
             final var immediateResult = new HashMap<>(Map.of(
                    "resourceType", "OperationOutcome",
                    "help",
                    "If you need help understanding how to decipher OperationOutcome, please see " 
                            + appConfig.getOperationOutcomeHelpUrl(),
                    "bundleSessionId", interactionId, // Tracking ID
                    "isAsync", true,
                    "validationResults", session.getValidationResults(),
                    "statusUrl",
                    getBaseUrl(requestParameters) + "/Bundle/$status/"
								+ interactionId.toString(),
                    "device", session.getDevice()));
            if (SourceType.CSV.name().equals(sourceType) && StringUtils.isNotEmpty(provenance)) {
                immediateResult.put("provenance",
							Configuration.objectMapper.readTree(provenance));
            }

            return immediateResult; // Return the validation results
        } catch (Exception e) {
// Log the error and create a failure response
            LOG.error("FHIRMapService - Validate - FAILED for interactionId: {}", interactionId, e);
            return Map.of(
                    "resourceType", "OperationOutcome",
                    "interactionId", interactionId,
                    "error", "Validation failed: " + e.getMessage());
        } finally {
            // Ensure the session is cleared to avoid memory leaks
            engine.clear(session);
            Instant end = Instant.now();
            Duration timeElapsed = Duration.between(start, end);
            LOG.info("FHIRMapService  - Validate -END for interaction id: {} Time Taken : {}  milliseconds",
                     interactionId, timeElapsed.toMillis());
        }
    } finally {
        span.end();
    }
}

	private void sendToScoringEngine(org.jooq.Configuration jooqCfg, Map<String, String> requestParameters,
			String scoringEngineApiURL,
			String dataLakeApiContentType,
			
			String tenantId,
			String payload,
			String provenance,
			Map<String, Object> validationPayloadWithDisposition, 
			String mtlsStrategy, String interactionId, String groupInteractionId,
			String masterInteractionId, String sourceType, String requestUriToBeOverriden, String coRrelationId) {
		Span span = tracer.spanBuilder("FHIRMapService.sentToScoringEngine").startSpan();
		try {
			interactionId = null != interactionId ? interactionId : getBundleInteractionId(requestParameters, coRrelationId);
			if (StringUtils.isNotEmpty(coRrelationId)) {
				interactionId = coRrelationId;
			}
			LOG.info("FHIRMapService:: sendToScoringEngine BEGIN for interaction id: {} for", interactionId);

			try {
				Map<String, Object> bundlePayloadWithDisposition = null;
				LOG.debug("FHIRMapService:: sendToScoringEngine includeOperationOutcome : {} interaction id: {}",
						 interactionId);
				if ( null != validationPayloadWithDisposition) { // todo
																							// -revisit
					LOG.debug(
							"FHIRMapService:: sendToScoringEngine Prepare payload with operation outcome interaction id: {}",
							interactionId);
					bundlePayloadWithDisposition = preparePayload(requestParameters,
							payload,
							validationPayloadWithDisposition, interactionId);
				} else {
					LOG.debug(
							"FHIRMapService:: sendToScoringEngine Send payload without operation outcome interaction id: {}",
							interactionId);
					bundlePayloadWithDisposition = Configuration.objectMapper.readValue(payload,
							new TypeReference<Map<String, Object>>() {
							});
				}
				final var dataLakeApiBaseURL = Optional.ofNullable(scoringEngineApiURL)
						.filter(s -> !s.isEmpty())
						.orElse(appConfig.getDefaultDatalakeApiUrl());
				final var defaultDatalakeApiAuthn = appConfig.getDefaultDataLakeApiAuthn();

				if (null == defaultDatalakeApiAuthn) {
					LOG.info(
							"###### defaultDatalakeApiAuthn is not defined #######.Hence proceeding with post to scoring engine without mTls for interaction id :{}",
							interactionId);
					handleNoMtls(MTlsStrategy.NO_MTLS, interactionId, tenantId, dataLakeApiBaseURL,
							jooqCfg, requestParameters,
							bundlePayloadWithDisposition, payload, dataLakeApiContentType,
							provenance,  
                                                        groupInteractionId,
							masterInteractionId, sourceType, requestUriToBeOverriden);
				} else {
					handleMTlsStrategy(defaultDatalakeApiAuthn, interactionId, tenantId,
							dataLakeApiBaseURL,
							jooqCfg, requestParameters, bundlePayloadWithDisposition,
							payload,
							dataLakeApiContentType, provenance, 
							mtlsStrategy, groupInteractionId, masterInteractionId,
							sourceType, requestUriToBeOverriden);
				}

			} catch (

			Exception e) {
				handleError(validationPayloadWithDisposition, e, requestParameters, interactionId);
			} finally {
				LOG.info("FHIRMapService:: sendToScoringEngine END for interaction id: {}", interactionId);
			}
		} finally {
			span.end();
		}
	}

	public void handleMTlsStrategy(DefaultDataLakeApiAuthn defaultDatalakeApiAuthn, String interactionId,
			String tenantId, String dataLakeApiBaseURL,
			org.jooq.Configuration jooqCfg, Map<String, String> requestParameters,
			Map<String, Object> bundlePayloadWithDisposition, String payload, String dataLakeApiContentType,
			String provenance,  
                        String mtlsStrategyStr,
			String groupInteractionId,
			String masterInteractionId, String sourceType, String requestUriToBeOverriden) {
		MTlsStrategy mTlsStrategy = null;

		LOG.info("FHIRMapService:: handleMTlsStrategy MTLS strategy from application.yml :{} for interaction id: {}",
				defaultDatalakeApiAuthn.mTlsStrategy(), interactionId);
		if (StringUtils.isNotEmpty(mtlsStrategyStr)) {
			LOG.info("FHIRMapService:: Proceed with mtls strategy from endpoint  :{} for interaction id: {}",
					defaultDatalakeApiAuthn.mTlsStrategy(), interactionId);
			mTlsStrategy = MTlsStrategy.fromString(mtlsStrategyStr);
		} else {
			mTlsStrategy = MTlsStrategy.fromString(defaultDatalakeApiAuthn.mTlsStrategy());
		}
		String requestURI = StringUtils.isNotEmpty(requestUriToBeOverriden) ? requestUriToBeOverriden
				: requestParameters.get("URI");
		switch (mTlsStrategy) {
			case AWS_SECRETS -> handleAwsSecrets(defaultDatalakeApiAuthn.mTlsAwsSecrets(), interactionId,
					tenantId, dataLakeApiBaseURL, dataLakeApiContentType,
					bundlePayloadWithDisposition, jooqCfg, provenance,
					requestURI, 
                                        payload,
					groupInteractionId, masterInteractionId, sourceType);
			case POST_STDOUT_PAYLOAD_TO_NYEC_DATA_LAKE_EXTERNAL ->
				handlePostStdoutPayload(interactionId, tenantId, jooqCfg, dataLakeApiBaseURL,
						bundlePayloadWithDisposition,
						
                                                payload, provenance, requestParameters,
						defaultDatalakeApiAuthn.postStdinPayloadToNyecDataLakeExternal(),
						groupInteractionId, masterInteractionId, sourceType,
						requestUriToBeOverriden);
			case MTLS_RESOURCES ->
				handleMtlsResources(interactionId, tenantId, jooqCfg,
						bundlePayloadWithDisposition,
						 
                                                payload, provenance, requestParameters,
						dataLakeApiContentType, dataLakeApiBaseURL,
						defaultDatalakeApiAuthn.mTlsResources(), groupInteractionId,
						masterInteractionId, sourceType, requestUriToBeOverriden);
			default ->
				handleNoMtls(mTlsStrategy, interactionId, tenantId, dataLakeApiBaseURL, jooqCfg,
						requestParameters,
						bundlePayloadWithDisposition, payload, dataLakeApiContentType,
						provenance, 
                                                groupInteractionId,
						masterInteractionId, sourceType, requestUriToBeOverriden);
		}
	}

	private void handleMtlsResources(String interactionId, String tenantId, org.jooq.Configuration jooqCfg,
			Map<String, Object> bundlePayloadWithDisposition,
			String payload, String provenance, Map<String, String> requestParameters, String dataLakeApiContentType,
			String dataLakeApiBaseURL,
			MTlsResources mTlsResources, String groupInteractionId, String masterInteractionId,
			String sourceType, String requestUriToBeOverriden) {
		LOG.info("FHIRMapService:: handleMtlsResources BEGIN for interaction id: {} tenantid :{} scoring",
				interactionId,
				tenantId);
		final var requestURI = StringUtils.isNotEmpty(requestUriToBeOverriden) ? requestUriToBeOverriden
				: requestParameters.get("URI");

		try {
			registerStateForward(jooqCfg, provenance, getBundleInteractionId(requestParameters, interactionId),
					requestURI, tenantId,
					Optional.ofNullable(bundlePayloadWithDisposition)
							.orElse(new HashMap<>()),
					null, 
                                         payload, groupInteractionId,
					masterInteractionId, sourceType);

			if (null == mTlsResources.mTlsKeyResourceName()) {
				LOG.error(
						"ERROR:: FHIRMapService:: handleMtlsResources Key location  `mTlsKeyResourceName` is not configured in application.yml for interaction id : {}  tenant id :{} ",
						interactionId, tenantId);
				throw new IllegalArgumentException(
						"Client key location `mTlsKeyResourceName` is not configured in application.yml");
			}
			if (null == mTlsResources.mTlsCertResourceName()) {
				LOG.error(
						"ERROR:: FHIRMapService:: handleMtlsResources Client certificate location `mTlsCertResourceName` not configured in application.yml  for interaction id : {} tenant id : {}",
						interactionId, tenantId);
				throw new IllegalArgumentException(
						"Client certificate location `mTlsCertResourceName` not configured in application.yml");
			}
			String myClientKey = Files
					.readString(Paths.get(mTlsResources.mTlsKeyResourceName()));
			if (null == myClientKey) {
				LOG.error(
						"ERROR:: FHIRMapService:: handleMtlsResources Key not provided.Copy the key to file in location :{} for interaction id :{} tenant id :{} ",
						mTlsResources.mTlsKeyResourceName(), interactionId, tenantId);
				throw new IllegalArgumentException(
						"Client key not provided.Copy the key to file in location : "
								+ mTlsResources.mTlsKeyResourceName());
			}
			LOG.debug(
					"FHIRMapService:: handleMtlsResources Client key fetched successfully for interaction id: {} tenantid :{} ",
					interactionId,
					tenantId);
			String myClientCert = Files.readString(Paths.get(mTlsResources.mTlsCertResourceName()));
			if (null == myClientCert) {
				LOG.error(
						"ERROR:: FHIRMapService:: handleMtlsResources Client certificate not provided.Copy the certificate to file in location :{}  for interaction id : {}  tenantId :{}",
						mTlsResources.mTlsCertResourceName(), interactionId, tenantId);
				throw new IllegalArgumentException(
						"Client certificate not provided.Copy the certificate to file in location : "
								+ mTlsResources.mTlsCertResourceName());
			}
			LOG.debug(
					"FHIRMapService:: handleMtlsResources Client cert fetched successfully for interaction id: {} tenantid :{} ",
					interactionId,
					tenantId);
			LOG.debug("FHIRMapService:: handleMtlsResources Get SSL Context -BEGIN for interaction id: {} tenantid:{}",
					interactionId, tenantId);

			final var sslContext = SslContextBuilder.forClient()
					.keyManager(new ByteArrayInputStream(myClientCert.getBytes()),
							new ByteArrayInputStream(myClientKey.getBytes()))
					.build();
			LOG.debug("FHIRMapService:: handleMtlsResources Get SSL Context -END for interaction id: {} tenantId:{}",
					interactionId, tenantId);
			LOG.debug("FHIRMapService:: handleMtlsResources Create HttpClient for interaction id: {} tenantID:{}",
					interactionId, tenantId);
			HttpClient httpClient = HttpClient.create()
					.secure(sslSpec -> sslSpec.sslContext(sslContext));

			LOG.debug("FHIRMapService:: Create ReactorClientHttpConnector for interaction id: {} tenantId:{}",
					interactionId, tenantId);
			ReactorClientHttpConnector connector = new ReactorClientHttpConnector(httpClient);
			LOG.debug(
					"FHIRMapService:: Build WebClient with MTLS Enabled ReactorClientHttpConnector -BEGIN with scoring Engine API URL : {} for interactionID :{} tenant Id:{} ",
					dataLakeApiBaseURL, interactionId, tenantId);
			var webClient = WebClient.builder()
					.baseUrl(dataLakeApiBaseURL)
					.defaultHeader("Content-Type", dataLakeApiContentType)
					.clientConnector(connector)
					.build();
			LOG.debug(
					"FHIRMapService:: Build WebClient with MTLS Enabled ReactorClientHttpConnector -END with scoring Engine API URL : {} for interactionID :{} tenant Id:{} ",
					dataLakeApiBaseURL, interactionId, tenantId);
			LOG.debug(
					"FHIRMapService:: handleMtlsResources Build WebClient with MTLS Enabled ReactorClientHttpConnector -BEGIN \n"
							+
							"with scoring Engine API URL: {} \n" +
							"dataLakeApiContentType: {} \n" +
							"bundlePayloadWithDisposition: {} \n" +
							"for interactionID: {} \n" +
							"tenant Id: {}",
					dataLakeApiBaseURL,
					dataLakeApiContentType,
					bundlePayloadWithDisposition == null ? "Payload is null"
							: "Payload is not null",
					interactionId,
					tenantId);
			sendPostRequest(webClient, tenantId, bundlePayloadWithDisposition, payload,
					dataLakeApiContentType, interactionId,
					jooqCfg, provenance, requestParameters.get("URI"), dataLakeApiBaseURL,
					groupInteractionId, masterInteractionId, sourceType);
			LOG.debug(
					"FHIRMapService:: handleMtlsResources Build WebClient with MTLS Enabled ReactorClientHttpConnector -END for interaction Id :{}",
					interactionId);
			LOG.info("FHIRMapService:: handleMtlsResources END for interaction id: {} tenantid :{} ",
					interactionId,
					tenantId);
		} catch (Exception ex) {
			LOG.error(
					"ERROR:: handleMtlsResources Exception while posting to scoring engine with MTLS enabled for interactionId : {}",
					interactionId, ex);
			registerStateFailed(jooqCfg, interactionId,
					requestURI, tenantId, ex.getMessage(), provenance,
					groupInteractionId, masterInteractionId, sourceType);

		}
	}

	private void handleNoMtls(MTlsStrategy mTlsStrategy, String interactionId, String tenantId,
			String dataLakeApiBaseURL,
			org.jooq.Configuration jooqCfg, Map<String, String> requestParameters,
			Map<String, Object> bundlePayloadWithDisposition, String payload, String dataLakeApiContentType,
			String provenance, 
                         String groupInteractionId,
			String masterInteractionId, String sourceType, String requestUriToBeOverriden) {
		if (!MTlsStrategy.NO_MTLS.value.equals(mTlsStrategy.value)) {
			LOG.info(
					"#########Invalid MTLS Strategy defined #############: Allowed values are {} .Hence proceeding with post to scoring engine without mTls for interaction id :{}",
					MTlsStrategy.getAllValues(), interactionId);
		}
		LOG.debug("FHIRMapService:: handleNoMtls Build WebClient with MTLS  Disabled -BEGIN \n"
				+
				"with scoring Engine API URL: {} \n" +
				"dataLakeApiContentType: {} \n" +
				"bundlePayloadWithDisposition: {} \n" +
				"for interactionID: {} \n" +
				"tenant Id: {}",
				dataLakeApiBaseURL,
				dataLakeApiContentType,
				bundlePayloadWithDisposition == null ? "Payload is null"
						: "Payload is not null",
				interactionId,
				tenantId);
		var webClient = createWebClient(dataLakeApiBaseURL, jooqCfg, requestParameters,
				tenantId, payload,
				bundlePayloadWithDisposition, provenance, 
				interactionId, groupInteractionId, masterInteractionId, sourceType,
				requestUriToBeOverriden);
		LOG.debug("FHIRMapService:: createWebClient END for interaction id: {} tenant id :{} ", interactionId,
				tenantId);
		LOG.debug("FHIRMapService:: sendPostRequest BEGIN for interaction id: {} tenantid :{} ", interactionId,
				tenantId);
		sendPostRequest(webClient, tenantId, bundlePayloadWithDisposition, payload,
				dataLakeApiContentType, interactionId,
				jooqCfg, provenance,
				StringUtils.isNotEmpty(requestUriToBeOverriden) ? requestUriToBeOverriden
						: requestParameters.get("URI"),
				dataLakeApiBaseURL, groupInteractionId,
				masterInteractionId, sourceType);
		LOG.debug("FHIRMapService:: sendPostRequest END for interaction id: {} tenantid :{} ", interactionId,
				tenantId);
	}

	private void handleAwsSecrets(MTlsAwsSecrets mTlsAwsSecrets, String interactionId, String tenantId,
			String dataLakeApiBaseURL, String dataLakeApiContentType,
			Map<String, Object> bundlePayloadWithDisposition,
			org.jooq.Configuration jooqCfg, String provenance, String requestURI,
			 
                        String payload, String groupInteractionId,
			String masterInteractionId,
			String sourceType) {
		try {
			LOG.info("FHIRMapService :: handleAwsSecrets -BEGIN for interactionId : {}",
					interactionId);

			registerStateForward(jooqCfg, provenance, interactionId, requestURI,
					tenantId, bundlePayloadWithDisposition, null, 
					payload, groupInteractionId, masterInteractionId, sourceType);
			if (null == mTlsAwsSecrets || null == mTlsAwsSecrets.mTlsKeySecretName()
					|| null == mTlsAwsSecrets.mTlsCertSecretName()) {
				throw new IllegalArgumentException(
						"######## Strategy defined is aws-secrets but mTlsKeySecretName and mTlsCertSecretName is not correctly configured. ######### ");
			}
			if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
				Security.addProvider(new BouncyCastleProvider());
			}
			KeyDetails keyDetails = getSecretsFromAWSSecretManager(mTlsAwsSecrets.mTlsKeySecretName(),
					mTlsAwsSecrets.mTlsCertSecretName());
			final String CERTIFICATE = keyDetails.cert();
			final String PRIVATE_KEY = keyDetails.key();

			if (StringUtils.isEmpty(CERTIFICATE)) {
				throw new IllegalArgumentException(
						"Certifcate read from secrets manager with certficate secret name : {} is null "
								+ mTlsAwsSecrets.mTlsCertSecretName());
			}

			if (StringUtils.isEmpty(PRIVATE_KEY)) {
				throw new IllegalArgumentException(
						"Private key read from secrets manager with key secret name : {} is null "
								+ mTlsAwsSecrets.mTlsKeySecretName());
			}

			LOG.debug(
					"FHIRMapService :: handleAwsSecrets Certificate and Key Details fetched successfully for interactionId : {}",
					interactionId);

			LOG.debug("FHIRMapService :: handleAwsSecrets Creating SSLContext  -BEGIN for interactionId : {}",
					interactionId);

			final var sslContext = SslContextBuilder.forClient()
					.keyManager(new ByteArrayInputStream(CERTIFICATE.getBytes()),
							new ByteArrayInputStream(PRIVATE_KEY.getBytes()))
					.build();
			LOG.debug("FHIRMapService :: handleAwsSecrets Creating SSLContext  - END for interactionId : {}",
					interactionId);

			HttpClient httpClient = HttpClient.create()
					.secure(sslSpec -> sslSpec.sslContext(sslContext));
			LOG.debug("FHIRMapService :: handleAwsSecrets HttpClient created successfully  for interactionId : {}",
					interactionId);

			ReactorClientHttpConnector connector = new ReactorClientHttpConnector(httpClient);
			LOG.debug(
					"FHIRMapService :: handleAwsSecrets ReactorClientHttpConnector created successfully  for interactionId : {}",
					interactionId);
			LOG.info(
					"FHIRMapService:: handleAwsSecrets Build WebClient with MTLS Enabled ReactorClientHttpConnector -BEGIN \n"
							+
							"with scoring Engine API URL: {} \n" +
							"dataLakeApiContentType: {} \n" +
							"bundlePayloadWithDisposition: {} \n" +
							"for interactionID: {} \n" +
							"tenant Id: {}",
					dataLakeApiBaseURL,
					dataLakeApiContentType,
					bundlePayloadWithDisposition == null ? "Payload is null"
							: "Payload is not null",
					interactionId,
					tenantId);
			var webClient = WebClient.builder()
					.baseUrl(dataLakeApiBaseURL)
					.defaultHeader("Content-Type", dataLakeApiContentType)
					.clientConnector(connector)
					.build();
			LOG.debug(
					"FHIRMapService :: handleAwsSecrets  Build WebClient with MTLS Enabled ReactorClientHttpConnector -END for interactionId :{}",
					interactionId);
			LOG.debug("FHIRMapService:: handleAwsSecrets - sendPostRequest BEGIN for interaction id: {} tenantid :{} ",
					interactionId,
					tenantId);
			sendPostRequest(webClient, tenantId, bundlePayloadWithDisposition, payload,
					dataLakeApiContentType, interactionId,
					jooqCfg, provenance, requestURI, dataLakeApiBaseURL, groupInteractionId,
					masterInteractionId, sourceType);
			LOG.debug("FHIRMapService:: handleAwsSecrets -sendPostRequest END for interaction id: {} tenantid :{} ",
					interactionId,
					tenantId);
			LOG.debug("FHIRMapService :: handleAwsSecrets Post to scoring engine -END for interactionId :{}",
					interactionId);
		} catch (Exception ex) {
			LOG.error(
					"ERROR:: FHIRMapService :: handleAwsSecrets Post to scoring engine FAILED with error :{} for interactionId :{} tenantId:{}",
					ex.getMessage(),
					interactionId, tenantId, ex);
			registerStateFailed(jooqCfg, interactionId, requestURI, tenantId, ex.getMessage(),
					provenance, groupInteractionId, masterInteractionId, sourceType);
		}
		LOG.info("FHIRMapService :: handleAwsSecrets -END for interactionId : {}",
				interactionId);
	}

	private void handlePostStdoutPayload(String interactionId, String tenantId, org.jooq.Configuration jooqCfg,
			String dataLakeApiBaseURL,
			Map<String, Object> bundlePayloadWithDisposition,
			
                         String payload, String provenance,
			Map<String, String> requestParameters,
			PostStdinPayloadToNyecDataLakeExternal postStdinPayloadToNyecDataLakeExternal,
			String groupInteractionId, String masterInteractionId, String sourceType,
			String requestUriToBeOverriden) {
		LOG.info("Proceed with posting payload via external process BEGIN forinteractionId : {}",
				interactionId);
		final var requestURI = StringUtils.isNotEmpty(requestUriToBeOverriden) ? requestUriToBeOverriden
				: requestParameters.get("URI");

		try {
			registerStateForward(jooqCfg, provenance, getBundleInteractionId(requestParameters, interactionId),
					requestURI, tenantId,
					Optional.ofNullable(bundlePayloadWithDisposition)
							.orElse(new HashMap<>()),
					null, 
                                        payload, groupInteractionId,
					masterInteractionId, sourceType);
			var postToNyecExternalResponse = postStdinPayloadToNyecDataLakeExternal(dataLakeApiBaseURL,
					tenantId, interactionId,
					bundlePayloadWithDisposition,
					postStdinPayloadToNyecDataLakeExternal);

			LOG.info("Create payload from postToNyecExternalResponse- BEGIN for interactionId : {}",
					interactionId);
			var payloadJson = Map.of("completed",
					postToNyecExternalResponse.completed(),
					"processOutput", postToNyecExternalResponse.processOutput(),
					"errorOutput", postToNyecExternalResponse.errorOutput());
			String responsePayload = Configuration.objectMapper
					.writeValueAsString(payloadJson);
			LOG.info("Create payload from postToNyecExternalResponse- END forinteractionId : {}",
					interactionId);
			if (postToNyecExternalResponse.completed() && null != postToNyecExternalResponse.processOutput()
					&& postToNyecExternalResponse.processOutput()
							.contains("{\"status\": \"Success\"")) {
				registerStateComplete(jooqCfg, interactionId,
						requestURI, tenantId, responsePayload,
						provenance, groupInteractionId, masterInteractionId, sourceType);
			} else {
				registerStateFailed(jooqCfg, interactionId,
						requestURI, tenantId, responsePayload,
						provenance, groupInteractionId, masterInteractionId, sourceType);
			}
		} catch (Exception ex) {
			LOG.error("Exception while postStdinPayloadToNyecDataLakeExternal forinteractionId : {}",
					interactionId, ex);
			registerStateFailed(jooqCfg, interactionId,
					requestURI, tenantId, ex.getMessage(), provenance,
					groupInteractionId, masterInteractionId, sourceType);
		}
		LOG.info("Proceed with posting payload via external process END for interactionId : {}",
				interactionId);
	}

	private PostToNyecExternalResponse postStdinPayloadToNyecDataLakeExternal(String dataLakeApiBaseURL,
			String tenantId,
			String interactionId,
			Map<String, Object> bundlePayloadWithDisposition,
			PostStdinPayloadToNyecDataLakeExternal postStdinPayloadToNyecDataLakeExternal)
			throws Exception {
		boolean completed = false;
		String processOutput = "";
		String errorOutput = "";
		LOG.info("FHIRMapService :: postStdinPayloadToNyecDataLakeExternal BEGIN for interaction id : {} tenantID :{}",
				interactionId, tenantId);
		final var bashScriptPath = postStdinPayloadToNyecDataLakeExternal.cmd();
		if (null == bashScriptPath) {
			throw new IllegalArgumentException(
					"Bash Script path not configured for the environment.Configure this in application.yml.");
		}
		LOG.debug(
				"FHIRMapService :: postStdinPayloadToNyecDataLakeExternal Fetched Bash Script Path :{} for interaction id : {} tenantID :{}",
				bashScriptPath, interactionId, tenantId);
		LOG.debug(
				"FHIRMapService :: postStdinPayloadToNyecDataLakeExternal Prepare ProcessBuilder to run the bash script for interaction id : {} tenantID :{}",
				interactionId, tenantId);
		final var processBuilder = new ProcessBuilder(bashScriptPath, tenantId, dataLakeApiBaseURL)
				.redirectErrorStream(true);
		LOG.debug(
				"FHIRMapService :: postStdinPayloadToNyecDataLakeExternal Start the process  for interaction id : {} tenantID :{}",
				interactionId, tenantId);
		final var process = processBuilder.start();
		LOG.debug(
				"FHIRMapService :: postStdinPayloadToNyecDataLakeExternal DEBUG: Capture any output from stdout or stderr immediately after starting\r\n"
						+ //
						"        // the process  for interaction id : {} tenantID :{}",
				interactionId, tenantId);
		try (var errorStream = process.getErrorStream();
				var inputStream = process.getInputStream()) {

			// DEBUG: Print any errors encountered
			errorOutput = new String(errorStream.readAllBytes(), StandardCharsets.UTF_8);
			if (!errorOutput.isBlank()) {
				LOG.error(
						"FHIRMapService :: postStdinPayloadToNyecDataLakeExternal Error Stream Output before sending payload:{}  for interaction id : {} tenantID :{}",
						errorOutput, interactionId, tenantId);
				throw new UnexpectedException(
						"Error Stream Output before sending payload:\n" + errorOutput);
			}
			LOG.debug(
					"FHIRMapService :: postStdinPayloadToNyecDataLakeExternal Convert Payload map to String BEGIN for interaction id : {} tenantID :{}",
					interactionId, tenantId);

			LOG.debug(
					"FHIRMapService :: postStdinPayloadToNyecDataLakeExternal Pass the payload map to String END for interaction id : {} tenantID :{}",
					interactionId, tenantId);
			String payload = Configuration.objectMapper.writeValueAsString(bundlePayloadWithDisposition);
			LOG.debug(
					"FHIRMapService :: postStdinPayloadToNyecDataLakeExternal Pass the payload via STDIN -BEGIN for interaction id : {} tenantID :{}",
					interactionId, tenantId);
			try (OutputStream outputStream = process.getOutputStream()) {
				outputStream.write(payload.getBytes(StandardCharsets.UTF_8));
				outputStream.flush();
				LOG.debug(
						"FHIRMapService :: postStdinPayloadToNyecDataLakeExternal Payload send successfully to STDIN -END for interaction id : {} tenantID :{}",
						interactionId, tenantId);
			} catch (IOException e) {
				LOG.debug(
						"FHIRMapService :: postStdinPayloadToNyecDataLakeExternal Failed to write payload to process STDIN due to error :{}  begin for interaction id : {} tenantID :{}",
						e.getMessage(), interactionId, tenantId);
				throw e;
			}
			final int timeout = Integer.valueOf(postStdinPayloadToNyecDataLakeExternal.timeout());
			LOG.debug(
					"FHIRMapService :: postStdinPayloadToNyecDataLakeExternal Wait for the process to complete with a timeout :{}  begin for interaction id : {} tenantID :{}",
					timeout, interactionId, tenantId);
			completed = process.waitFor(timeout, TimeUnit.SECONDS);
			LOG.debug(
					"FHIRMapService :: postStdinPayloadToNyecDataLakeExternal Wait elapsed ...Fetch response  begin for interaction id : {} tenantID :{}",
					interactionId, tenantId);

			processOutput = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
			LOG.debug(
					"FHIRMapService :: postStdinPayloadToNyecDataLakeExternal Standard Output received:{} for interaction id : {} tenantID :{}",
					processOutput, interactionId, tenantId);
			errorOutput = new String(errorStream.readAllBytes(), StandardCharsets.UTF_8);
			if (!errorOutput.isBlank()) {
				LOG.debug(
						"FHIRMapService :: postStdinPayloadToNyecDataLakeExternal Error Stream Output after execution: {} for interaction id : {} tenantID :{}",
						errorOutput, interactionId, tenantId);
			}
		}

		LOG.info("FHIRMapService :: postStdinPayloadToNyecDataLakeExternal END for interaction id : {} tenantID :{}",
				interactionId, tenantId);
		return new PostToNyecExternalResponse(completed, processOutput, errorOutput);
	}

	private WebClient createWebClient(String scoringEngineApiURL,
			org.jooq.Configuration jooqCfg,
			Map<String, String> requestParameters,
			String tenantId,
			String payload,
			Map<String, Object> bundlePayloadWithDisposition,
			String provenance,
			
                        String interactionId, String groupInteractionId,
			String masterInteractionId, String sourceType, String requestUriToBeOverriden) {
		return WebClient.builder()
				.baseUrl(scoringEngineApiURL)
				.filter(ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
					filter(clientRequest, requestParameters, jooqCfg, provenance, tenantId, payload,
							bundlePayloadWithDisposition,
							
                                                         groupInteractionId,
							masterInteractionId, sourceType, requestUriToBeOverriden, interactionId);
					return Mono.just(clientRequest);
				}))
				.build();
	}

	private KeyDetails getSecretsFromAWSSecretManager(String keyName, String certName) {
		Region region = Region.US_EAST_1;
		LOG.debug(
				"FHIRMapService:: getSecretsFromAWSSecretManager  - Get Secrets Client Manager for region : {} BEGIN for interaction id: {}",
				Region.US_EAST_1);
		SecretsManagerClient secretsClient = SecretsManagerClient.builder()
				.region(region)
				.build();
		KeyDetails keyDetails = new KeyDetails(getValue(secretsClient, keyName),
				getValue(secretsClient, certName));
		secretsClient.close();
		LOG.debug(
				"FHIRMapService:: getSecretsFromAWSSecretManager  - Get Secrets Client Manager for region : {} END for interaction id: {}",
				Region.US_EAST_1);
		return keyDetails;
	}

	public static String getValue(SecretsManagerClient secretsClient, String secretName) {
		LOG.debug("FHIRMapService:: getValue  - Get Value of secret with name  : {} -BEGIN", secretName);
		String secret = null;
		try {
			GetSecretValueRequest valueRequest = GetSecretValueRequest.builder()
					.secretId(secretName)
					.build();

			GetSecretValueResponse valueResponse = secretsClient.getSecretValue(valueRequest);
			secret = valueResponse.secretString();
			LOG.info("FHIRMapService:: getValue  - Fetched value of secret with name  : {}  value  is null : {} -END",
					secretName, secret == null ? "true" : "false");
		} catch (SecretsManagerException e) {
			LOG.error("ERROR:: FHIRMapService:: getValue  - Get Value of secret with name  : {} - FAILED with error "
					+ e.awsErrorDetails().errorMessage(), e);
		} catch (Exception e) {
			LOG.error("ERROR:: FHIRMapService:: getValue  - Get Value of secret with name  : {} - FAILED with error ",
					e);
		}
		LOG.info("FHIRMapService:: getValue  - Get Value of secret with name  : {} ", secretName);
		return secret;
	}

	private void filter(ClientRequest clientRequest,
			Map<String, String> requestParameters,
			org.jooq.Configuration jooqCfg,
			String provenance,
			String tenantId,
			String payload,
			Map<String, Object> bundlePayloadWithDisposition,
			
                        String groupInteractionId, String masterInteractionId,
			String sourceType, String requestUriToBeOverriden, String interactionId) {

		LOG.debug("FHIRMapService:: sendToScoringEngine Filter request before post - BEGIN interaction id: {}",
				interactionId);
		final var requestURI = StringUtils.isNotEmpty(requestUriToBeOverriden) ? requestUriToBeOverriden
				: requestParameters.get("URI");
		StringBuilder requestBuilder = new StringBuilder()
				.append(clientRequest.method().name()).append(" ")
				.append(clientRequest.url()).append(" HTTP/1.1").append("\n");

		clientRequest.headers().forEach((name, values) -> values
				.forEach(value -> requestBuilder.append(name).append(": ").append(value).append("\n")));

		final var outboundHttpMessage = requestBuilder.toString();

		registerStateForward(jooqCfg, provenance, getBundleInteractionId(requestParameters, interactionId), requestURI, tenantId,
				Optional.ofNullable(bundlePayloadWithDisposition).orElse(new HashMap<>()),
				outboundHttpMessage, 
                                 payload, groupInteractionId,
				masterInteractionId, sourceType);

		LOG.debug("FHIRMapService:: sendToScoringEngine Filter requestParameters before post - END interaction id: {}",
				interactionId);
	}

	private void sendPostRequest(WebClient webClient,
			String tenantId,
			Map<String, Object> bundlePayloadWithDisposition,
			String payload,
			String dataLakeApiContentType,
			String interactionId,
			org.jooq.Configuration jooqCfg,
			String provenance,
			String requestURI, String scoringEngineApiURL, String groupInteractionId,
			String masterInteractionId, String sourceType) {
		Span span = tracer.spanBuilder("FhirService.sendPostRequest").startSpan();
		try {
			LOG.debug(
					"FHIRMapService:: sendToScoringEngine Post to scoring engine - BEGIN interaction id: {} tenantID :{}",
					interactionId, tenantId);
			webClient.post()
					.uri("?processingAgent=" + tenantId)
					.body(BodyInserters.fromValue(null != bundlePayloadWithDisposition
							? bundlePayloadWithDisposition
							: payload))
					.header("Content-Type", Optional.ofNullable(dataLakeApiContentType)
							.orElse(AppConfig.Servlet.FHIR_CONTENT_TYPE_HEADER_VALUE))
					.retrieve()
					.bodyToMono(String.class)
					.subscribe(response -> {
						handleResponse(response, jooqCfg, interactionId, requestURI, tenantId,
								provenance, scoringEngineApiURL, groupInteractionId,
								masterInteractionId, sourceType);
					}, error -> {
						registerStateFailure(jooqCfg, scoringEngineApiURL, interactionId, error,
								requestURI, tenantId, provenance, groupInteractionId,
								masterInteractionId, sourceType);
					});

			LOG.info("FHIRMapService:: sendToScoringEngine Post to scoring engine - END interaction id: {} tenantid: {}",
					interactionId, tenantId);
		} finally {
			span.end();
		}
	}

	private void handleResponse(String response,
			org.jooq.Configuration jooqCfg,
			String interactionId,
			String requestURI,
			String tenantId,
			String provenance,
			String scoringEngineApiURL, String groupInteractionId, String masterInteractionId,
			String sourceType) {
		Span span = tracer.spanBuilder("FhirService.handleResponse").startSpan();
		try {
			LOG.debug("FHIRMapService:: handleResponse BEGIN for interaction id: {}", interactionId);

			try {
				LOG.debug("FHIRMapService:: handleResponse Response received for :{} interaction id: {}",
						interactionId, response);
				final var responseMap = new ObjectMapper().readValue(response,
						new TypeReference<Map<String, String>>() {
						});

				if ("Success".equalsIgnoreCase(responseMap.get("status"))) {
					LOG.info("FHIRMapService:: handleResponse SUCCESS for interaction id: {}",
							interactionId);
					registerStateComplete(jooqCfg, interactionId, requestURI, tenantId, response,
							provenance, groupInteractionId, masterInteractionId,
							sourceType);
				} else {
					LOG.warn("FHIRMapService:: handleResponse FAILURE for interaction id: {}",
							interactionId);
					registerStateFailed(jooqCfg, interactionId, requestURI, tenantId, response,
							provenance, groupInteractionId, masterInteractionId,
							sourceType);
				}
			} catch (Exception e) {
				LOG.error("FHIRMapService:: handleResponse unexpected error for interaction id : {}, response: {}",
						interactionId, response, e);
				registerStateFailed(jooqCfg, interactionId, requestURI, tenantId, e.getMessage(),
						provenance, groupInteractionId, masterInteractionId, sourceType);
			}
			LOG.info("FHIRMapService:: handleResponse END for interaction id: {}", interactionId);
		} finally {
			span.end();
		}
	}

	private void handleError(Map<String, Object> validationPayloadWithDisposition,
			Exception e,
			Map<String, String> requestParameters, String interactionId) {

		validationPayloadWithDisposition.put("exception", e.toString());
		LOG.error(
				"ERROR:: FHIRMapService:: sendToScoringEngine Exception while sending to scoring engine payload with interaction id: {}",
				getBundleInteractionId(requestParameters, interactionId), e);
	}

	private Map<String, Object> preparePayload(Map<String, String> requestParameters, String bundlePayload,
			Map<String, Object> payloadWithDisposition, String interactionId) {
		LOG.debug("FHIRMapService:: addValidationResultToPayload BEGIN for interaction id : {}", interactionId);

		Map<String, Object> resultMap = null;

		try {
			Map<String, Object> extractedOutcome = Optional
					.ofNullable(extractIssueAndDisposition(interactionId, payloadWithDisposition))
					.filter(outcome -> !outcome.isEmpty())
					.orElseGet(() -> {
						LOG.warn(
								"FHIRMapService:: resource type operation outcome or issues or techByDisposition is missing or empty for interaction id : {}",
								interactionId);
						return null;
					});

			if (extractedOutcome == null) {
				LOG.warn("FHIRMapService:: extractedOutcome is null for interaction id : {}",
						interactionId);
				return payloadWithDisposition;
			}
			Map<String, Object> bundleMap = Optional
					.ofNullable(Configuration.objectMapper.readValue(bundlePayload,
							new TypeReference<Map<String, Object>>() {
							}))
					.filter(map -> !map.isEmpty())
					.orElseGet(() -> {
						LOG.warn(
								"FHIRMapService:: bundleMap is missing or empty after parsing bundlePayload for interaction id : {}",
								interactionId);
						return null;
					});

			if (bundleMap == null) {
				LOG.warn("FHIRMapService:: bundleMap is null for interaction id : {}", interactionId);
				return payloadWithDisposition;
			}
			resultMap = appendToBundlePayload(interactionId, bundleMap, extractedOutcome);
			LOG.info(
					"FHIRMapService:: addValidationResultToPayload END - Validation results added to Bundle payload for interaction id : {}",
					interactionId);
			return resultMap;
		} catch (Exception ex) {
			LOG.error(
					"ERROR :: FHIRMapService:: addValidationResultToPayload encountered an exception for interaction id : {}",
					interactionId, ex);
		}
		LOG.info(
				"FHIRMapService:: addValidationResultToPayload END - Validation results not added - returning original payload for interaction id : {}",
				interactionId);
		return payloadWithDisposition;
	}

	@SuppressWarnings("unchecked")
	public Map<String, Object> extractIssueAndDisposition(String interactionId,
			Map<String, Object> operationOutcomePayload) {
		LOG.debug("FHIRMapService:: extractResourceTypeAndDisposition BEGIN for interaction id : {}",
				interactionId);

		if (operationOutcomePayload == null) {
			LOG.warn("FHIRMapService:: operationOutcomePayload is null for interaction id : {}",
					interactionId);
			return null;
		}

		return Optional.ofNullable(operationOutcomePayload.get("OperationOutcome"))
				.filter(Map.class::isInstance)
				.map(Map.class::cast)
				.flatMap(operationOutcomeMap -> {
					List<?> validationResults = (List<?>) operationOutcomeMap
							.get("validationResults");
					if (validationResults == null || validationResults.isEmpty()) {
						return Optional.empty();
					}

					// Extract the first validationResult
					Map<String, Object> validationResult = (Map<String, Object>) validationResults
							.get(0);

					// Navigate to operationOutcome.issue
					Map<String, Object> operationOutcome = (Map<String, Object>) validationResult
							.get("operationOutcome");
					List<?> issues = operationOutcome != null
							? (List<?>) operationOutcome.get("issue")
							: null;

					// Prepare the result
					Map<String, Object> result = new HashMap<>();
					result.put("resourceType", operationOutcomeMap.get("resourceType"));
					result.put("issue", issues);

					// Add techByDesignDisposition if available
					List<?> techByDesignDisposition = (List<?>) operationOutcomeMap
							.get("techByDesignDisposition");
					if (techByDesignDisposition != null && !techByDesignDisposition.isEmpty()) {
						result.put("techByDesignDisposition", techByDesignDisposition);
					}

					return Optional.of(result);
				})
				.orElseGet(() -> {
					LOG.warn("FHIRMapService:: Missing required fields in operationOutcome for interaction id : {}",
							interactionId);
					return null;
				});
	}

	private Map<String, Object> appendToBundlePayload(String interactionId, Map<String, Object> payload,
			Map<String, Object> extractedOutcome) {
		LOG.debug("FHIRMapService:: appendToBundlePayload BEGIN for interaction id : {}", interactionId);
		if (payload == null) {
			LOG.warn("FHIRMapService:: payload is null for interaction id : {}", interactionId);
			return payload;
		}

		if (extractedOutcome == null || extractedOutcome.isEmpty()) {
			LOG.warn("FHIRMapService:: extractedOutcome is null or empty for interaction id : {}",
					interactionId);
			return payload; // Return the original payload if no new outcome to append
		}

		@SuppressWarnings("unchecked")
		List<Map<String, Object>> entries = Optional.ofNullable(payload.get("entry"))
				.filter(List.class::isInstance)
				.map(entry -> (List<Map<String, Object>>) entry)
				.orElseGet(() -> {
					LOG.warn("FHIRMapService:: 'entry' field is missing or invalid for interaction id : {}",
							interactionId);
					return new ArrayList<>(); // if no entries in payload create new
				});

		final Map<String, Object> newEntry = Map.of("resource", extractedOutcome);
		entries.add(newEntry);

		final Map<String, Object> finalPayload = new HashMap<>(payload);
		finalPayload.put("entry", List.copyOf(entries));
		LOG.debug("FHIRMapService:: appendToBundlePayload END for interaction id : {}", interactionId);
		return finalPayload;
	}

	private void registerStateForward(org.jooq.Configuration jooqCfg, String provenance,
			String bundleAsyncInteractionId, String requestURI,
			String tenantId,
			Map<String, Object> payloadWithDisposition,
			String outboundHttpMessage, 
                        String payload,
			String groupInteractionId, String masterInteractionId, String sourceType) {
		Span span = tracer.spanBuilder("FHIRMapService.registerStateForward").startSpan();
		try {
			LOG.info("REGISTER State Forward : BEGIN for inteaction id  : {} tenant id : {}",
					bundleAsyncInteractionId, tenantId);
			final var forwardedAt = OffsetDateTime.now();
			final var initRIHR = new RegisterInteractionHttpRequest();
			try {
				// TODO -check the need of includeIncomingPayloadInDB and add this later if
				// needed
				// payloadWithDisposition.put("outboundHttpMessage", outboundHttpMessage);
				// + "\n" + (includeIncomingPayloadInDB
				// ? payload
				// : "The incoming FHIR payload was not stored (to save space).\nThis is not an
				// error or warning just an FYI - if you'd like to see the incoming FHIR payload
				// `?include-incoming-payload-in-db=true` to request payload storage for each
				// request that you'd like to store."));
				initRIHR.setInteractionId(bundleAsyncInteractionId);
				initRIHR.setGroupHubInteractionId(groupInteractionId);
				initRIHR.setSourceHubInteractionId(masterInteractionId);
				initRIHR.setInteractionKey(requestURI);
				initRIHR.setNature((JsonNode) Configuration.objectMapper.valueToTree(
						Map.of("nature", "Forward HTTP Request", "tenant_id",
								tenantId)));
				initRIHR.setContentType(MimeTypeUtils.APPLICATION_JSON_VALUE);
				initRIHR.setPayload((JsonNode) Configuration.objectMapper
						.valueToTree(payloadWithDisposition));
				initRIHR.setFromState("DISPOSITION");
				initRIHR.setToState("FORWARD");
				initRIHR.setSourceType(sourceType);
				initRIHR.setCreatedAt(forwardedAt); // don't let DB set this, use app
				// time
				initRIHR.setCreatedBy(FHIRMapService.class.getName());
				initRIHR.setProvenance(provenance);
				final var start = Instant.now();
				final var execResult = initRIHR.execute(jooqCfg);
				final var end = Instant.now();
				LOG.info(
						"REGISTER State Forward : END for interaction id : {} tenant id : {} .Time taken : {} milliseconds"
								+ execResult,
						bundleAsyncInteractionId, tenantId,
						Duration.between(start, end).toMillis());
			} catch (Exception e) {
				LOG.error("ERROR:: REGISTER State Forward CALL for interaction id : {} tenant id : {}"
						+ initRIHR.getName() + " initRIHR error", bundleAsyncInteractionId,
						tenantId,
						e);
			}
		} finally {
			span.end();
		}
	}

	private void registerStateComplete(org.jooq.Configuration jooqCfg, String bundleAsyncInteractionId,
			String requestURI, String tenantId,
			String response, String provenance, String groupInteractionId, String masterInteractionId,
			String sourceType) {
		Span span = tracer.spanBuilder("FHIRMapService.registerStateComplete").startSpan();
		try {
			LOG.info("REGISTER State Complete : BEGIN for interaction id :  {} tenant id : {}",
					bundleAsyncInteractionId, tenantId);
			final var forwardRIHR = new RegisterInteractionHttpRequest();
			try {
				forwardRIHR.setInteractionId(bundleAsyncInteractionId);
				forwardRIHR.setSourceHubInteractionId(masterInteractionId);
				forwardRIHR.setGroupHubInteractionId(groupInteractionId);
				forwardRIHR.setInteractionKey(requestURI);
				forwardRIHR.setNature((JsonNode) Configuration.objectMapper.valueToTree(
						Map.of("nature", "Forwarded HTTP Response",
								"tenant_id", tenantId)));
				forwardRIHR.setContentType(
						MimeTypeUtils.APPLICATION_JSON_VALUE);
				try {
					// expecting a JSON payload from the server
					forwardRIHR.setPayload(Configuration.objectMapper
							.readTree(response));
				} catch (JsonProcessingException jpe) {
					// in case the payload is not JSON store the string
					forwardRIHR.setPayload((JsonNode) Configuration.objectMapper
							.valueToTree(response));
				}
				forwardRIHR.setFromState("FORWARD");
				forwardRIHR.setToState("COMPLETE");
				forwardRIHR.setSourceType(sourceType);
				forwardRIHR.setCreatedAt(OffsetDateTime.now()); // don't let DB
				forwardRIHR.setCreatedBy(FHIRMapService.class.getName());
				forwardRIHR.setProvenance(provenance);
				final var start = Instant.now();
				final var execResult = forwardRIHR.execute(jooqCfg);
				final var end = Instant.now();
				LOG.info(
						"REGISTER State Complete : END for interaction id : {} tenant id : {} .Time Taken : {} milliseconds"
								+ execResult,
						bundleAsyncInteractionId, tenantId,
						Duration.between(start, end).toMillis());
			} catch (Exception e) {
				LOG.error("ERROR:: REGISTER State Complete CALL for interaction id : {} tenant id : {} "
						+ forwardRIHR.getName()
						+ " forwardRIHR error", bundleAsyncInteractionId, tenantId, e);
			}
		} finally {
			span.end();
		}
	}

	private void registerStateFailed(org.jooq.Configuration jooqCfg, String bundleAsyncInteractionId,
			String requestURI, String tenantId,
			String response, String provenance, String groupInteractionId, String masterInteractionId,
			String sourceType) {
		Span span = tracer.spanBuilder("FHIRMapService.registerStateFailed").startSpan();
		try {
			LOG.info("REGISTER State Fail : BEGIN for interaction id :  {} tenant id : {}",
					bundleAsyncInteractionId, tenantId);
			final var forwardRIHR = new RegisterInteractionHttpRequest();
			try {
				forwardRIHR.setInteractionId(bundleAsyncInteractionId);
				forwardRIHR.setInteractionKey(requestURI);
				forwardRIHR.setGroupHubInteractionId(groupInteractionId);
				forwardRIHR.setSourceHubInteractionId(masterInteractionId);
				forwardRIHR.setNature((JsonNode) Configuration.objectMapper.valueToTree(
						Map.of("nature", "Forwarded HTTP Response Error",
								"tenant_id", tenantId)));
				forwardRIHR.setContentType(
						MimeTypeUtils.APPLICATION_JSON_VALUE);
				try {
					// expecting a JSON payload from the server
					forwardRIHR.setPayload(Configuration.objectMapper
							.readTree(response));
				} catch (JsonProcessingException jpe) {
					// in case the payload is not JSON store the string
					forwardRIHR.setPayload((JsonNode) Configuration.objectMapper
							.valueToTree(response));
				}
				forwardRIHR.setFromState("FORWARD");
				forwardRIHR.setToState("FAIL");
				forwardRIHR.setSourceType(sourceType);
				forwardRIHR.setCreatedAt(OffsetDateTime.now()); // don't let DB
				// set this, use
				// app time
				forwardRIHR.setCreatedBy(FHIRMapService.class.getName());
				forwardRIHR.setProvenance(provenance);
				final var start = Instant.now();
				final var execResult = forwardRIHR.execute(jooqCfg);
				final var end = Instant.now();
				LOG.info(
						"REGISTER State Fail : END for interaction id : {} tenant id : {} .Time Taken : {} milliseconds"
								+ execResult,
						bundleAsyncInteractionId, tenantId,
						Duration.between(start, end).toMillis());
			} catch (Exception e) {
				LOG.error("ERROR:: REGISTER State Fail CALL for interaction id : {} tenant id : {} "
						+ forwardRIHR.getName()
						+ " forwardRIHR error", bundleAsyncInteractionId, tenantId, e);
			}
		} finally {
			span.end();
		}
	}

	private void registerStateFailure(org.jooq.Configuration jooqCfg, String dataLakeApiBaseURL,
			String bundleAsyncInteractionId, Throwable error,
			String requestURI, String tenantId,
			String provenance, String groupInteractionId, String masterInteractionId, String sourceType) {
		Span span = tracer.spanBuilder("FhirService.registerStateFailure").startSpan();
		try {
			LOG.error(
					"Register State Failure - Exception while sending FHIR payload to datalake URL {} for interaction id {}",
					dataLakeApiBaseURL, bundleAsyncInteractionId, error);
			final var errorRIHR = new RegisterInteractionHttpRequest();
			try {
				errorRIHR.setInteractionId(bundleAsyncInteractionId);
				errorRIHR.setGroupHubInteractionId(groupInteractionId);
				errorRIHR.setSourceHubInteractionId(masterInteractionId);
				errorRIHR.setInteractionKey(requestURI);
				errorRIHR.setNature((JsonNode) Configuration.objectMapper.valueToTree(
						Map.of("nature", "Forwarded HTTP Response Error",
								"tenant_id", tenantId)));
				errorRIHR.setContentType(MimeTypeUtils.APPLICATION_JSON_VALUE);
				errorRIHR.setSourceType(sourceType);
				final var rootCauseThrowable = NestedExceptionUtils
						.getRootCause(error);
				final var rootCause = rootCauseThrowable != null
						? rootCauseThrowable.toString()
						: "null";
				final var mostSpecificCause = NestedExceptionUtils
						.getMostSpecificCause(error).toString();
				final var errorMap = new HashMap<String, Object>() {
					{
						put("dataLakeApiBaseURL", dataLakeApiBaseURL);
						put("error", error.toString());
						put("message", error.getMessage());
						put("rootCause", rootCause);
						put("mostSpecificCause", mostSpecificCause);
						put("tenantId", tenantId);
					}
				};
				if (error instanceof final WebClientResponseException webClientResponseException) {
					String responseBody = webClientResponseException
							.getResponseBodyAsString();
					errorMap.put("responseBody", responseBody);
					String bundleId = "";
					JsonNode rootNode = Configuration.objectMapper
							.readTree(responseBody);
					JsonNode bundleIdNode = rootNode.path("bundle_id"); // Adjust
					// this
					// path
					// based
					// on
					// actual
					if (!bundleIdNode.isMissingNode()) {
						bundleId = bundleIdNode.asText();
					}
					LOG.error(
							"Exception while sending FHIR payload to datalake URL {} for interaction id {} bundle id {} response from datalake {}",
							dataLakeApiBaseURL,
							bundleAsyncInteractionId, bundleId,
							responseBody);
					errorMap.put("statusCode", webClientResponseException
							.getStatusCode().value());
					final var responseHeaders = webClientResponseException
							.getHeaders()
							.entrySet()
							.stream()
							.collect(Collectors.toMap(
									Map.Entry::getKey,
									entry -> String.join(
											",",
											entry.getValue())));
					errorMap.put("headers", responseHeaders);
					errorMap.put("statusText", webClientResponseException
							.getStatusText());
				}
				errorRIHR.setPayload((JsonNode) Configuration.objectMapper
						.valueToTree(errorMap));
				errorRIHR.setFromState("FORWARD");
				errorRIHR.setToState("FAIL");
				errorRIHR.setCreatedAt(OffsetDateTime.now()); // don't let DB set this, use app time
				errorRIHR.setCreatedBy(FHIRMapService.class.getName());
				errorRIHR.setProvenance(provenance);
				final var start = Instant.now();
				final var execResult = errorRIHR.execute(jooqCfg);
				final var end = Instant.now();
				LOG.error("Register State Failure - END for interaction id : {} tenant id : {} forwardRIHR execResult"
						+ execResult + ". Time Taken : {} milliseconds ",
						bundleAsyncInteractionId,
						tenantId, Duration.between(start, end).toMillis());
			} catch (Exception e) {
				LOG.error("ERROR :: Register State Failure - for interaction id : {} tenant id : {} CALL "
						+ errorRIHR.getName() + " errorRIHR error", bundleAsyncInteractionId,
						tenantId,
						e);
			}
		} finally {
			span.end();
		}
	}

	private String getBundleInteractionId(Map<String, String> requestParameters, String correlationId) {
        if (StringUtils.isNotEmpty(correlationId)) {
            return correlationId;
        }
        
        // Retrieve the active request ID from requestParameters
        String requestId = requestParameters.get("requestId");
    
        if (StringUtils.isNotEmpty(requestId)) {
            return requestId;
        }
    
        // If no correlationId or requestId is found, return a default value or throw an exception
        throw new IllegalArgumentException("No valid interaction ID found");
    }
    

	// private String getBaseUrl(HttpServletRequest request) {
	// 	return Helpers.getBaseUrl(request);
	// }
    private String getBaseUrl(Map<String, String> requestParameters) {
        return requestParameters.getOrDefault("baseUrl", "http://default-url.com");
    }
    

	public enum MTlsStrategy {
		NO_MTLS("no-mTls"),
		AWS_SECRETS("aws-secrets"),
		MTLS_RESOURCES("mTlsResources"),
		POST_STDOUT_PAYLOAD_TO_NYEC_DATA_LAKE_EXTERNAL("post-stdin-payload-to-nyec-datalake-external");
		// AWS_SECRETS_TEMP_FILE("aws-secrets-temp-file"),
		// AWS_SECRETS_TEMP_WITHOUT_HASH("aws-secrets-without-hash"),
		// AWS_SECRETS_TEMP_WITHOUT_OPENSSL("aws-secrets-without-openssl"),
		// AWS_SECRETS_TEMP_FILE_WITHOUT_HASH("aws-secrets-temp-file-without-hash"),
		// AWS_SECRETS_TEMP_FILE_WITHOUT_OPENSSL("aws-secrets-temp-file-without-openssl"),
		// AWS_SECRETS_TEMP_FILE_WITHOUT_OPENSSLANDHASH("aws-secrets-temp-file-without-opensslandhash");

		private final String value;

		MTlsStrategy(String value) {
			this.value = value;
		}

		public String getValue() {
			return value;
		}

		public static String getAllValues() {
			return Arrays.stream(MTlsStrategy.values())
					.map(MTlsStrategy::getValue)
					.collect(Collectors.joining(", "));
		}

		public static MTlsStrategy fromString(String value) {
			for (MTlsStrategy strategy : MTlsStrategy.values()) {
				if (strategy.value.equals(value)) {
					return strategy;
				}
			}
			throw new IllegalArgumentException("No enum constant for value: " + value);
		}
	}

	public record KeyDetails(String key, String cert) {
	}

	public record PostToNyecExternalResponse(boolean completed, String processOutput, String errorOutput) {
	}

	public enum TechByDesignDisposition {
		ACCEPT("accept"),
		REJECT("reject"),
		DISCARD("discard");

		private final String action;

		TechByDesignDisposition(String action) {
			this.action = action;
		}

		public String getAction() {
			return action;
		}
	}

}
