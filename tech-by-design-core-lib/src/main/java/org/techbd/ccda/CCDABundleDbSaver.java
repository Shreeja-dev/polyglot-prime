package org.techbd.ccda;

import org.techbd.config.MirthJooqConfig;
import org.techbd.config.Configuration;
import org.techbd.config.Constants;
import org.techbd.udi.auto.jooq.ingress.routines.RegisterInteractionHttpRequest;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public class CCDABundleDbSaver {

    public static boolean saveCcdaValidation(String interactionId, String tenantId, String requestUri, String payloadJson, Map<String, Object> operationOutcome) {
        try {
            var jooqCfg = MirthJooqConfig.dsl().configuration();
            var rihr = new RegisterInteractionHttpRequest();

            rihr.setInteractionId(interactionId);
            rihr.setInteractionKey(requestUri);
            rihr.setNature((JsonNode) Configuration.objectMapper.valueToTree(
                Map.of("nature", "CCDA Validation Result", "tenant_id", tenantId)
            ));
            rihr.setContentType("application/json");
            rihr.setPayload((JsonNode) Configuration.objectMapper.valueToTree(operationOutcome));
            rihr.setFromState("VALIDATE");
            rihr.setToState("STORE");
            rihr.setSourceType("CCDA");
            rihr.setCreatedAt(OffsetDateTime.now());
            rihr.setCreatedBy(CCDABundleDbSaver.class.getName());

            int result = rihr.execute(jooqCfg);
            return result >= 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
