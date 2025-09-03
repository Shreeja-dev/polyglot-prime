package org.techbd.service.http.hub.prime.replay;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ccda")
public class CcdaReplayController {

    private final CcdaReplayService replayService;

    public CcdaReplayController(CcdaReplayService replayService) {
        this.replayService = replayService;
    }

    /**
     * Replay CCDA Bundles asynchronously for the given list of Bundle IDs.
     *
     * @param bundleIds list of bundle IDs to replay
     * @return interim response with acknowledgement
     */
    @PostMapping("/replay")
    public ResponseEntity<Map<String, Object>> replayBundles(@RequestBody List<String> bundleIds) {
        if (bundleIds == null || bundleIds.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "bundleIds list cannot be null or empty"));
        }
        final var replayMasterInteractionId = UUID.randomUUID().toString()
        replayService.replayBundlesAsync(bundleIds,replayMasterInteractionId);

        // Interim response
        return ResponseEntity.accepted().body(
                  Map.of(
                "replayMasterInteractionId", replayMasterInteractionId,
                "status", "Replay initiated",
                "replayStatusUrl", "/ccda/Bundle/replay/status?replayMasterInteractionId=" + replayMasterInteractionId,
                "bundleCount", bundleIds.size(),
                "bundleIds", bundleIds
        ));
    }

    /**
     * Get status of a replay by replayMasterInteractionId.
     *
     * @param replayMasterInteractionId the unique id of the replay request
     * @return current status of replay
     */
    @GetMapping("/replay/status")
    public ResponseEntity<Map<String, Object>> getReplayStatus(
            @RequestParam String replayMasterInteractionId) {
        var status = replayService.getReplayStatus(replayMasterInteractionId);
        if (status == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(status);
    }
}
