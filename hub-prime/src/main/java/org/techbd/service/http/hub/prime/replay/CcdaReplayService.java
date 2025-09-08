package org.techbd.service.http.hub.prime.replay;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jooq.DSLContext;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.techbd.config.Configuration;
import org.techbd.service.fhir.FHIRService;
import org.techbd.udi.UdiPrimeJpaConfig;
import org.techbd.udi.auto.jooq.ingress.routines.GetXmlContentFromMirthFdw;
import org.techbd.udi.auto.jooq.ingress.routines.MergeBundleResourceIds;
import org.techbd.util.AppLogger;
import org.techbd.util.TemplateLogger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Mono;

@Service
public class CcdaReplayService {
    private final TemplateLogger LOG;
    private final TaskExecutor asyncTaskExecutor;
    private final UdiPrimeJpaConfig udiPrimeJpaConfig;
    private final FHIRService fhirService;

    public CcdaReplayService(TaskExecutor asyncTaskExecutor, AppLogger appLogger, UdiPrimeJpaConfig udiPrimeJpaConfig,FHIRService fhirService) {
        this.asyncTaskExecutor = asyncTaskExecutor;
        this.udiPrimeJpaConfig = udiPrimeJpaConfig;
        this.fhirService = fhirService;
        LOG = appLogger.getLogger(CcdaReplayService.class);
    }

    public String replayBundlesAsync(List<String> bundleIds, String replayMasterInteractionId) {
        Map<String, Object> processingDetails = new HashMap<>();
        // CompletableFuture.runAsync(() -> {

        for (String bundleId : bundleIds) {
            final var interactionId = UUID.randomUUID().toString();
            try {
                LOG.info(
                        "PROCESSING STARTED - Replaying bundle %s with replayMasterInteractionId: {} interactionid: {} bundleid: {}",
                        bundleId, replayMasterInteractionId, interactionId);
                Map<String, Object> orginalPayloadAndHeaders = getOriginalCCDPayload(bundleId,
                        replayMasterInteractionId, interactionId);
                LOG.info(
                        "Successfully fetched original values for replayMasterInteractionId={} interactionId={} bundleId={} ({} entries)",
                        replayMasterInteractionId, interactionId, bundleId, orginalPayloadAndHeaders.size());
                logResponse(orginalPayloadAndHeaders, replayMasterInteractionId, interactionId, bundleId);
                boolean isAlreadyResubmitted = orginalPayloadAndHeaders.get("alreadyResubmitted") != null
                        && Boolean.parseBoolean(orginalPayloadAndHeaders.get("alreadyResubmitted").toString());

                if (isAlreadyResubmitted) {
                    LOG.warn(
                            "PROCESSING_COMPLETED - skipping already resubmitted bundle as it has already been resubmitted. replayMasterInteractionId={} interactionId={}",
                            bundleId, replayMasterInteractionId, interactionId);
                    processingDetails = buildProcessingDetailsMap(
                            processingDetails,
                            bundleId,
                            null, // no errorMessage in this case
                            "Not Processed - already resubmitted",
                            "Not Processed");
                    continue;
                }
                Map<String, Object> responseJson = getGeneratedBundle(
                        orginalPayloadAndHeaders, bundleId,
                        replayMasterInteractionId, interactionId);
                LOG.info(
                        "Successfully passed original ccda payload and received response for replayMasterInteractionId={} interactionId={} bundleId={} ({} entries)",
                        replayMasterInteractionId, interactionId, bundleId, orginalPayloadAndHeaders.size());
                logResponse(responseJson, replayMasterInteractionId, interactionId, bundleId);
                boolean isValid = responseJson.get("isValid") != null
                        && Boolean.parseBoolean(responseJson.get("isValid").toString());
                if (!isValid) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> errorMessage = (Map<String, Object>) responseJson
                            .getOrDefault("errorMessage", Map.of("message", "Unknown error during CCDA processing"));
                    LOG.error(
                            "PROCESSING_COMPLETED - Bundle failed validation with replayMasterInteractionId={} interactionId={} bundleId={}\nErrorMessage: {}",
                            replayMasterInteractionId, interactionId, bundleId, errorMessage);
                    // Use the helper to enrich processingDetails
                    processingDetails = buildProcessingDetailsMap(
                            processingDetails,
                            bundleId,
                            errorMessage,
                            "Failed CCDA Schema Validation",
                            "FAILED");
                    continue;
                }
                ObjectMapper mapper = new ObjectMapper();
                String generatedBundle = mapper.writeValueAsString(responseJson.get("generatedBundle"));
                Map<String, Object> correctedResponse = mergeBundleResourceIds(generatedBundle, bundleId,
                        replayMasterInteractionId, replayMasterInteractionId);
                final var correctedBundle = correctedResponse.get("corrected_bundle");
                boolean mergeSuccess = correctedResponse.get("merge_success") != null
                        && Boolean.parseBoolean(correctedResponse.get("merge_success").toString());
                if (!mergeSuccess) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> errorMessage = (Map<String, Object>) correctedResponse
                            .getOrDefault("error", Map.of("message", "Unknown error during merging bundle resource IDs"));
                    LOG.error(
                            "PROCESSING_COMPLETED - Bundle failed merging with replayMasterInteractionId={} interactionId={} bundleId={}\nErrorMessage: {}",
                            replayMasterInteractionId, interactionId, bundleId, errorMessage);
                    // Use the helper to enrich processingDetails
                    processingDetails = buildProcessingDetailsMap(
                            processingDetails,
                            bundleId,
                            errorMessage,
                            "Failed Merging Bundle Resource IDs",
                            "FAILED");
                    continue;
                }
                LOG.info(
                        "PROCESSING COMPLETED - Bundle successfully replayed with replayMasterInteractionId={} interactionId={} bundleId={}",
                        replayMasterInteractionId, interactionId, bundleId);
                        
                fhirService.processBundle(correctedBundle,rocessingDetails, responseJson);
                return generatedBundle;
            } catch (Exception e) {
                LOG.error("Bundle %s failed with replayMasterInteractionId: {} interactionid: {} bundleid: {}: %s",
                        replayMasterInteractionId, interactionId, bundleId, e.getMessage());
            }
        }
        // }, asyncTaskExecutor);
        return null;
    }

    public Map<String, Object> getReplayStatus(String replayMasterInteractionId) {
        return null;// replayStatusMap.get(replayMasterInteractionId);
    }

    private Map<String, Object> buildProcessingDetailsMap(Map<String, Object> processingDetails,
            String bundleId,
            Map<String, Object> errorMessage,
            String message,
            String status) {
        if (processingDetails == null) {
            processingDetails = new HashMap<>();
        }

        if (bundleId != null) {
            processingDetails.put("bundleId", bundleId);
        }
        if (errorMessage != null && !errorMessage.isEmpty()) {
            processingDetails.put("errorMessage", errorMessage);
        }
        if (message != null) {
            processingDetails.put("message", message);
        }
        if (status != null) {
            processingDetails.put("status", status);
        }

        return processingDetails;
    }

    private void logResponse(Map<String, Object> responseMap,
            String replayMasterInteractionId,
            String interactionId,
            String bundleId) {

        for (Map.Entry<String, Object> entry : responseMap.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof JsonNode jsonNode) {
                LOG.info(
                        "Key={} JSON Payload with {} fields | replayMasterInteractionId={} interactionId={} bundleId={}",
                        key, jsonNode.size(), replayMasterInteractionId, interactionId, bundleId);
            } else {
                LOG.info(
                        "Key={} Value={} | replayMasterInteractionId={} interactionId={} bundleId={}",
                        key, value, replayMasterInteractionId, interactionId, bundleId);
            }
        }
    }

    /**
     * Fetch the original CCDA XML payload for a given bundleId.
     *
     * @param bundleId                  the Bundle ID
     * @param replayMasterInteractionId replay request master ID
     * @param interactionId             individual interaction ID
     * @return CCDA XML payload as string
     */
    private Map<String, Object> getOriginalCCDPayload(String bundleId,
            String replayMasterInteractionId,
            String interactionId) {
        LOG.info("Fetching CCDA payload for replayMasterInteractionId: {} InteractionId: {} bundleId: {}",
                replayMasterInteractionId, interactionId, bundleId);
        Map<String, Object> response = new HashMap<>(3);
        try {
            final DSLContext dslContext = udiPrimeJpaConfig.dsl();
            final var jooqCfg = dslContext.configuration();
            GetXmlContentFromMirthFdw routine = new GetXmlContentFromMirthFdw();
            routine.setPBundleId(bundleId);
            routine.execute(jooqCfg);
            final var responseJson = (JsonNode) routine.getReturnValue();
            if (responseJson == null) {
                LOG.warn("No CCDA payload found for replayMasterInteractionId: {} InteractionId: {}  bundleId={}",
                        replayMasterInteractionId, interactionId, bundleId);
            } else {
                response.putAll(extractFields(responseJson));
                LOG.info(
                        "Successfully fetched CCDA payload for replayMasterInteractionId: {} InteractionId: {} bundleId={} ({} chars)",
                        replayMasterInteractionId, interactionId, bundleId, responseJson.size());
            }
        } catch (Exception e) {
            LOG.error(
                    "Error fetching CCDA payload for replayMasterInteractionId: {} InteractionId: {} bundleId={}  : {}",
                    replayMasterInteractionId, interactionId, bundleId, e.getMessage(), e);
            throw e;
        }
        return response;
    }

    public static Map<String, Object> extractFields(JsonNode payload) {
        var result = new HashMap<String, Object>();

        payload.fieldNames().forEachRemaining(field -> {
            JsonNode value = payload.get(field);
            if (value.isValueNode()) {
                result.put(field, value.asText());
            } else {
                result.put(field, value);
            }
        });

        return result;
    }

    private Map<String, Object> getGeneratedBundle(Map<String, Object> response,
            String bundleId,
            String replayMasterInteractionId,
            String interactionId) {
        LOG.info("Fetching CCDA payload for replayMasterInteractionId: {} InteractionId: {} bundleId: {}",
                replayMasterInteractionId, interactionId, bundleId);

        // 🔍 Debug logs before making the API call
        LOG.debug("bundleId: {}", bundleId);
        LOG.debug("replayMasterInteractionId: {}", replayMasterInteractionId);
        LOG.debug("interactionId: {}", interactionId);
        LOG.debug("fhir_base_url: {}", response.get("fhir_base_url"));
        LOG.debug("originalCCDAPayload is {}",
                response.get("originalCCDAPayload") == null ? "NULL"
                        : "NOT NULL (length=" + String.valueOf(response.get("originalCCDAPayload")).length() + ")");

        WebClient webClient = WebClient.builder()
                .baseUrl(System.getenv("TECHBD_CCDA_BASEURL"))
                .build();

        ObjectMapper objectMapper = new ObjectMapper();

        try {
            String apiResponse = webClient.post()
                    .uri("/ccda/Bundle/")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .header("X-TechBD-Tenant-ID", "QE-123")
                    .header("X-TechBD-CIN", "AB12345C")
                    .header("X-TechBD-OrgNPI", "NPI123456")
                    .header("X-TechBD-Facility-ID", "FacilityID-123")
                    .header("X-TechBD-Encounter-Type", "405672008")
                    .header("X-TechBD-BundleId", bundleId)
                    .header("X-TechBD-Base-FHIR-URL", String.valueOf(response.get("fhir_base_url")))
                    .body(BodyInserters.fromMultipartData("file",
                            String.valueOf(response.get("originalCCDAPayload")).getBytes(StandardCharsets.UTF_8)))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, clientResponse -> clientResponse.bodyToMono(String.class)
                            .defaultIfEmpty("Unknown error from server")
                            .flatMap(errorBody -> {
                                LOG.error("Error response from CCDA API (status={}): {}",
                                        clientResponse.statusCode(), errorBody);
                                return Mono.error(new RuntimeException("CCDA API error: " + errorBody));
                            }))
                    .bodyToMono(String.class)
                    .block(); // raw JSON string

            LOG.debug("Raw API Response: {}", apiResponse);

            // Convert JSON -> Map
            Map<String, Object> result = objectMapper.readValue(apiResponse, Map.class);
            LOG.debug("Parsed response map keys: {}", result.keySet());

            return result;

        } catch (WebClientResponseException e) {
            LOG.error("WebClient error: status={} body={}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            return Map.of(
                    "isValid", false,
                    "errorMessage", Map.of("message", "WebClient error: " + e.getMessage()),
                    "generatedBundle", Map.of());
        } catch (Exception e) {
            LOG.error("Unexpected error calling CCDA API", e);
            return Map.of(
                    "isValid", false,
                    "errorMessage", Map.of("message", "Unexpected error: " + e.getMessage()),
                    "generatedBundle", Map.of());
        }
    }

    private Map<String, Object> mergeBundleResourceIds(String newBundle,
            String bundleId,
            String replayMasterInteractionId,
            String interactionId) {
        LOG.info("Merging bundle resources for replayMasterInteractionId: {} InteractionId: {} bundleId: {}",
                replayMasterInteractionId, interactionId, bundleId);
        Map<String, Object> response = new HashMap<>();
        try {
            final var dslContext = udiPrimeJpaConfig.dsl();
            final var jooqCfg = dslContext.configuration();
            MergeBundleResourceIds routine = new MergeBundleResourceIds();
            routine.setPNewBundle(Configuration.objectMapper.readTree(newBundle));
            routine.setPBundleId(bundleId);
            routine.execute(jooqCfg);
            final var responseJson = routine.getReturnValue();
            if (responseJson == null) {
                LOG.warn("Merge returned null for replayMasterInteractionId: {} InteractionId: {} bundleId: {}",
                        replayMasterInteractionId, interactionId, bundleId);
            } else {
                LOG.debug(
                        "Successfully merged resources for replayMasterInteractionId: {} InteractionId: {} bundleId: {} resultSize: {}",
                        replayMasterInteractionId, interactionId, bundleId, responseJson.toString().length());
                response = extractFields(responseJson);
            }
            return response;
        } catch (Exception e) {
            LOG.error(
                    "Error merging bundle resources for replayMasterInteractionId: {} InteractionId: {} bundleId: {} error: {}",
                    replayMasterInteractionId, interactionId, bundleId, e.getMessage(), e);
            // throw e;
        }
        return null;
    }
}
