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
import org.techbd.service.fhir.FHIRService;
import org.techbd.udi.UdiPrimeJpaConfig;
import org.techbd.udi.auto.jooq.ingress.routines.GetXmlContentFromMirthFdw;
import org.techbd.udi.auto.jooq.ingress.routines.MergeBundleResourceIds;
import org.techbd.util.AppLogger;
import org.techbd.util.TemplateLogger;

import com.fasterxml.jackson.databind.JsonNode;

import reactor.core.publisher.Mono;

@Service
public class CcdaReplayService {
    private final TemplateLogger LOG;
    private final TaskExecutor asyncTaskExecutor;
    private final UdiPrimeJpaConfig udiPrimeJpaConfig;

    public CcdaReplayService(TaskExecutor asyncTaskExecutor, AppLogger appLogger, UdiPrimeJpaConfig udiPrimeJpaConfig) {
        this.asyncTaskExecutor = asyncTaskExecutor;
        this.udiPrimeJpaConfig = udiPrimeJpaConfig;
        LOG = appLogger.getLogger(FHIRService.class);
    }

    public String replayBundlesAsync(List<String> bundleIds, String replayMasterInteractionId) {
    //    CompletableFuture.runAsync(() -> {
            for (String bundleId : bundleIds) {
                final var interactionId = UUID.randomUUID().toString();
                try {
                    LOG.info("Replaying bundle %s with replayMasterInteractionId: {} interactionid: {} bundleid: {}",
                            bundleId, replayMasterInteractionId, interactionId);
                    //getOriginalCCDPayload(bundleId, replayMasterInteractionId, interactionId);
                    String generatedBundle = getGeneratedBundle(
                            getOriginalCCDPayload(bundleId, replayMasterInteractionId, interactionId), bundleId,
                            replayMasterInteractionId, interactionId);
                   // Map<String,Object> correctedResponse = mergeBundleResourceIds(null, bundleId, replayMasterInteractionId, replayMasterInteractionId);
                   // boolean mergeSuccess = (boolean) correctedResponse.get("merge_success");
                    //final var correctedBundle = correctedResponse.get("corrected_bundle");
                    LOG.info(
                            "Bundle %s replayed successfully with replayMasterInteractionId:{} interactionid: {} bundleid: {}",
                            replayMasterInteractionId, interactionId, bundleId);
                            return generatedBundle;
                } catch (Exception e) {
                    LOG.error("Bundle %s failed with replayMasterInteractionId: {} interactionid: {} bundleid: {}: %s",
                            replayMasterInteractionId, interactionId, bundleId, e.getMessage());
                }
            }
   //     }, asyncTaskExecutor);
   return null;
    }

    public Map<String, Object> getReplayStatus(String replayMasterInteractionId) {
        return null;// replayStatusMap.get(replayMasterInteractionId);
    }

    /**
     * Fetch the original CCDA XML payload for a given bundleId.
     *
     * @param bundleId                  the Bundle ID
     * @param replayMasterInteractionId replay request master ID
     * @param interactionId             individual interaction ID
     * @return CCDA XML payload as string
     */
    private String getOriginalCCDPayload(String bundleId,
            String replayMasterInteractionId,
            String interactionId) {
        LOG.info("Fetching CCDA payload for replayMasterInteractionId: {} InteractionId: {} bundleId: {}",
                replayMasterInteractionId, interactionId, bundleId);
        try {
            final DSLContext dslContext = udiPrimeJpaConfig.dsl();
            final var jooqCfg = dslContext.configuration();
            GetXmlContentFromMirthFdw routine = new GetXmlContentFromMirthFdw();
            routine.setPBundleId(bundleId);
            routine.execute(jooqCfg);
            final var responseJson = (JsonNode) routine.getReturnValue();
            Map<String,Object> response = new HashMap<>(3);
            if (responseJson == null) {
                LOG.warn("No CCDA payload found for replayMasterInteractionId: {} InteractionId: {}  bundleId={}",
                        replayMasterInteractionId, interactionId, bundleId);
            } else {
                 response = extractFields(responseJson);
                LOG.info(
                        "Successfully fetched CCDA payload for replayMasterInteractionId: {} InteractionId: {} bundleId={} ({} chars)",
                        replayMasterInteractionId, interactionId, bundleId, responseJson.size());
            }

            final String originalCCDPayload = (String) response.get("originalCCDAPayload");
            return originalCCDPayload;

        } catch (Exception e) {
            LOG.error(
                    "Error fetching CCDA payload for replayMasterInteractionId: {} InteractionId: {} bundleId={}  : {}",
                    replayMasterInteractionId, interactionId, bundleId, e.getMessage(), e);
            throw e;
        }
    }

    public static Map<String, Object> extractFields(JsonNode payload) {
        var result = new HashMap<String, Object>();

        payload.fieldNames().forEachRemaining(field -> {
            JsonNode value = payload.get(field);

            // if it's a value node (string, number, boolean, null) → use .asText()
            if (value.isValueNode()) {
                result.put(field, value.asText());
            }
            // if it's an object/array, you can either keep as JsonNode or serialize to
            // string
            else {
                result.put(field, value);
            }
        });

        return result;
    }

    /**
     * Fetches a generated CCDA bundle by posting the original CCD payload to the
     * API.
     *
     * @param originalCCDPayload        The CCD/CCDA payload content (string form of
     *                                  HL7/XML)
     * @param bundleId                  The bundle identifier
     * @param replayMasterInteractionId The replay master interaction ID
     * @param interactionId             The interaction ID
     * @return JSON response string from the API, or an error message
     */
    private String getGeneratedBundle(String originalCCDPayload,
            String bundleId,
            String replayMasterInteractionId,
            String interactionId) {
        LOG.info("Fetching CCDA payload for replayMasterInteractionId: {} InteractionId: {} bundleId: {}",
                replayMasterInteractionId, interactionId, bundleId);
        WebClient webClient = WebClient.builder()
                .baseUrl(System.getenv("TECHBD_CCDA_BASEURL"))
                .build();
        try {
            return webClient.post()
                    .uri("/ccda/Bundle/")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .header("X-TechBD-Tenant-ID", "QE-123")
                    .header("X-TechBD-CIN", "AB12345C")
                    .header("X-TechBD-OrgNPI", "NPI123456")
                    .header("X-TechBD-Facility-ID", "FacilityID-123")
                    .header("X-TechBD-Encounter-Type", "405672008")
                    .header("X-TechBD-BundleId", bundleId)
                    .body(BodyInserters.fromMultipartData("file",
                            originalCCDPayload.getBytes(StandardCharsets.UTF_8)))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, clientResponse -> clientResponse.bodyToMono(String.class)
                            .defaultIfEmpty("Unknown error from server")
                            .flatMap(errorBody -> {
                                LOG.error("Error response from CCDA API (status={}): {}",
                                        clientResponse.statusCode(), errorBody);
                                return Mono.error(new RuntimeException("CCDA API error: " + errorBody));
                            }))
                    .bodyToMono(String.class)
                    .block(); // block because we return String

        } catch (WebClientResponseException e) {
            LOG.error("WebClient error: status={} body={}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            return "{\"error\":\"WebClient error: " + e.getMessage() + "\"}";
        } catch (Exception e) {
            LOG.error("Unexpected error calling CCDA API", e);
            return "{\"error\":\"Unexpected error: " + e.getMessage() + "\"}";
        }
    }

    private Map<String, Object> mergeBundleResourceIds(JsonNode newBundle,
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
            routine.setPNewBundle(newBundle);
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
            throw e;
        }
    }
}
