package org.techbd.service.http.hub.prime.prime.replay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;
import org.techbd.service.fhir.FHIRService;
import org.techbd.service.http.hub.prime.replay.CcdaReplayService;
import org.techbd.udi.UdiPrimeJpaConfig;
import org.techbd.util.AppLogger;
import org.techbd.util.TemplateLogger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class CcdaReplayServiceTest {

    @Mock
    private TaskExecutor taskExecutor;
    @Mock
    private AppLogger appLogger;
    @Mock
    private TemplateLogger logger;
    @Mock
    private UdiPrimeJpaConfig udiPrimeJpaConfig;
    @Mock
    private FHIRService fhirService;

    @InjectMocks
    private CcdaReplayService service;

    private static final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        when(appLogger.getLogger(CcdaReplayService.class)).thenReturn(logger);
        service = new CcdaReplayService(taskExecutor, appLogger, udiPrimeJpaConfig, fhirService);
    }

    @Test
    void testAlreadyResubmittedBundle() {
        String bundleId = "bundle-123";
        String replayMasterInteractionId = "replay-1";

        // stub getOriginalCCDPayload → alreadyResubmitted true
        CcdaReplayService spyService = spy(service);
        doReturn(Map.of("hubInteractionId", "hub-1", "alreadyResubmitted", "true"))
                .when(spyService).getOriginalCCDPayload(eq(bundleId), any(), any());
        String result = spyService.replayBundlesAsync(List.of(bundleId), replayMasterInteractionId);
        assertThat(result).isNull();
        verify(spyService).upsertCcdaReplayDetails(eq(bundleId), eq("hub-1"), any(), eq(false),
                any(), any(), eq(replayMasterInteractionId), eq("TEST_TENANT"));
    }

    @Test
    void testInvalidGeneratedBundle() {
        String bundleId = "bundle-456";
        String replayMasterInteractionId = "replay-2";

        CcdaReplayService spyService = spy(service);

        doReturn(Map.of("hubInteractionId", "hub-2")).when(spyService)
                .getOriginalCCDPayload(eq(bundleId), any(), any());

        // generated bundle says invalid
        doReturn(Map.of("isValid", false,
                "errorMessage", Map.of("message", "Schema validation failed")))
                .when(spyService).getGeneratedBundle(any(), eq(bundleId), any(), any());

        doNothing().when(spyService)
                .upsertCcdaReplayDetails(any(), any(), any(), anyBoolean(),
                        any(JsonNode.class), any(JsonNode.class), any(), any());

        String result = spyService.replayBundlesAsync(List.of(bundleId), replayMasterInteractionId);

        assertThat(result).isNull();
        verify(spyService).upsertCcdaReplayDetails(eq(bundleId), eq("hub-2"), any(),
                eq(false), any(JsonNode.class), any(JsonNode.class),
                eq(replayMasterInteractionId), eq("TEST_TENANT"));
    }

    @Test
    void testMergeFailure() throws Exception {
        String bundleId = "bundle-789";
        String replayMasterInteractionId = "replay-3";

        CcdaReplayService spyService = spy(service);

        doReturn(Map.of("hubInteractionId", "hub-3")).when(spyService)
                .getOriginalCCDPayload(eq(bundleId), any(), any());

        doReturn(Map.of("isValid", true, "generatedBundle", Map.of("foo", "bar")))
                .when(spyService).getGeneratedBundle(any(), eq(bundleId), any(), any());

        doReturn(Map.of("merge_success", false,
                "error", Map.of("message", "merge failed")))
                .when(spyService).mergeBundleResourceIds(any(), eq(bundleId), any(), any());

        doNothing().when(spyService)
                .upsertCcdaReplayDetails(any(), any(), any(), anyBoolean(),
                        any(JsonNode.class), any(JsonNode.class), any(), any());

        String result = spyService.replayBundlesAsync(List.of(bundleId), replayMasterInteractionId);

        assertThat(result).isNull();
        verify(spyService).mergeBundleResourceIds(any(), eq(bundleId), any(), any());
    }

    @Test
    void testSuccessReplay() throws Exception {
        String bundleId = "bundle-999";
        String replayMasterInteractionId = "replay-4";

        CcdaReplayService spyService = spy(service);

        doReturn(Map.of("hubInteractionId", "hub-9")).when(spyService)
                .getOriginalCCDPayload(eq(bundleId), any(), any());

        doReturn(Map.of("isValid", true, "generatedBundle", Map.of("foo", "bar")))
                .when(spyService).getGeneratedBundle(any(), eq(bundleId), any(), any());

        doReturn(Map.of("merge_success", true, "corrected_bundle", "corrected-json"))
                .when(spyService).mergeBundleResourceIds(any(), eq(bundleId), any(), any());

        doNothing().when(spyService)
                .upsertCcdaReplayDetails(any(), any(), any(), anyBoolean(),
                        any(), any(), any(), any());

        String result = spyService.replayBundlesAsync(List.of(bundleId), replayMasterInteractionId);

        assertThat(result).isNotNull();
        verify(fhirService).processBundle(eq("corrected-json"), anyMap(), anyMap());
        verify(spyService).upsertCcdaReplayDetails(eq(bundleId), eq("hub-9"), any(), eq(false),
                isNull(), any(JsonNode.class), eq(replayMasterInteractionId), eq("TEST_TENANT"));
    }

    @Test
    void testExceptionDuringProcessing() {
        String bundleId = "bundle-ex";
        String replayMasterInteractionId = "replay-5";

        CcdaReplayService spyService = spy(service);

        doThrow(new RuntimeException("DB error")).when(spyService)
                .getOriginalCCDPayload(eq(bundleId), any(), any());

        doNothing().when(spyService)
                .upsertCcdaReplayDetails(any(), any(), any(), anyBoolean(),
                        any(), any(), any(), any());

        String result = spyService.replayBundlesAsync(List.of(bundleId), replayMasterInteractionId);

        assertThat(result).isNull();
        verify(spyService).upsertCcdaReplayDetails(eq(bundleId), isNull(), any(),
                eq(false), isNull(), any(JsonNode.class), eq(replayMasterInteractionId), isNull());
    }

    @Test
    void testMultipleBundlesMixedOutcomes() throws Exception {
        List<String> bundleIds = List.of("bundle-resub", "bundle-invalid", "bundle-mergefail", "bundle-success");
        String replayMasterInteractionId = "replay-mixed";

        CcdaReplayService spyService = spy(service);

        // --- (1) Already resubmitted
        doReturn(Map.of("hubInteractionId", "hub-resub", "alreadyResubmitted", "true"))
                .when(spyService).getOriginalCCDPayload(eq("bundle-resub"), any(), any());

        // --- (2) Invalid bundle (schema validation failed)
        doReturn(Map.of("hubInteractionId", "hub-invalid"))
                .when(spyService).getOriginalCCDPayload(eq("bundle-invalid"), any(), any());
        doReturn(Map.of("isValid", false,
                "errorMessage", Map.of("message", "Schema validation failed")))
                .when(spyService).getGeneratedBundle(any(), eq("bundle-invalid"), any(), any());

        // --- (3) Merge failed
        doReturn(Map.of("hubInteractionId", "hub-mergefail"))
                .when(spyService).getOriginalCCDPayload(eq("bundle-mergefail"), any(), any());
        doReturn(Map.of("isValid", true, "generatedBundle", Map.of("foo", "bar")))
                .when(spyService).getGeneratedBundle(any(), eq("bundle-mergefail"), any(), any());
        doReturn(Map.of("merge_success", false,
                "error", Map.of("message", "merge failed")))
                .when(spyService).mergeBundleResourceIds(any(), eq("bundle-mergefail"), any(), any());

        // --- (4) Success
        doReturn(Map.of("hubInteractionId", "hub-success"))
                .when(spyService).getOriginalCCDPayload(eq("bundle-success"), any(), any());
        doReturn(Map.of("isValid", true, "generatedBundle", Map.of("foo", "bar")))
                .when(spyService).getGeneratedBundle(any(), eq("bundle-success"), any(), any());
        doReturn(Map.of("merge_success", true, "corrected_bundle", "corrected-json"))
                .when(spyService).mergeBundleResourceIds(any(), eq("bundle-success"), any(), any());

        // Stub upsertCcdaReplayDetails to do nothing
        doNothing().when(spyService).upsertCcdaReplayDetails(
                anyString(), anyString(), anyString(), anyBoolean(),
                any(), any(), anyString(), anyString());

        // --- Run replay
        spyService.replayBundlesAsync(bundleIds, replayMasterInteractionId);

        // (1) Already resubmitted
        verify(spyService).upsertCcdaReplayDetails(
                eq("bundle-resub"), eq("hub-resub"), anyString(), eq(false),
                isNull(), // errorMessage is null
                argThat(node -> node != null &&
                        "Not Processed - Already resubmitted".equals(node.path("message").asText()) &&
                        "Not Processed".equals(node.path("status").asText())), // elaboration
                eq(replayMasterInteractionId), eq("TEST_TENANT"));

        // (2) Invalid schema
        verify(spyService).upsertCcdaReplayDetails(
                eq("bundle-invalid"), eq("hub-invalid"), anyString(), eq(false),
                argThat(node -> node != null &&
                        "Schema validation failed".equals(node.path("message").asText())), // errorMessage
                argThat(node -> node != null &&
                        "Bundle failed validation -Failed CCDA Schema Validation".equals(node.path("message").asText())
                        &&
                        "Failed".equals(node.path("status").asText())), // elaboration
                eq(replayMasterInteractionId), eq("TEST_TENANT"));

        // (3) Merge failed
        verify(spyService).upsertCcdaReplayDetails(
                eq("bundle-mergefail"), eq("hub-mergefail"), anyString(), eq(false),
                argThat(node -> node != null &&
                        "merge failed".equals(node.path("message").asText())), // errorMessage
                argThat(node -> node != null &&
                        "Failed Merging Bundle Resource IDs".equals(node.path("message").asText()) &&
                        "Failed".equals(node.path("status").asText())), // elaboration
                eq(replayMasterInteractionId), eq("TEST_TENANT"));

        // (4) Success
        verify(spyService).upsertCcdaReplayDetails(
                eq("bundle-success"), eq("hub-success"), anyString(), eq(false),
                isNull(), // errorMessage is null
                argThat(node -> node != null &&
                        "PROCESSING COMPLETED - Bundle successfully replayed".equals(node.path("message").asText()) &&
                        "Success".equals(node.path("status").asText())), // elaboration
                eq(replayMasterInteractionId), eq("TEST_TENANT"));

        // Ensure fhirService.processBundle called only once (for the successful bundle)
        verify(fhirService, times(1)).processBundle(eq("corrected-json"), anyMap(), anyMap());
    }

}
