package org.techbd.orchestrate.csv;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.techbd.service.http.hub.prime.api.FHIRService;

import com.nimbusds.oauth2.sdk.util.CollectionUtils;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The {@code OrchestrationEngine} class is responsible for managing and
 * orchestrating CSV validation sessions.
 */
@Component
@Getter
@Setter
@NoArgsConstructor
public class CsvOrchestrationEngine {
    private List<OrchestrationSession> sessions = new ArrayList<>();
    private FHIRService fhirService;
    
    private static final Logger log = LoggerFactory.getLogger(CsvOrchestrationEngine.class);
    private static final Pattern FILE_PATTERN = Pattern.compile("(DEMOGRAPHIC_DATA|QE_ADMIN_DATA|SCREENING)_(.+)");

    public List<OrchestrationSession> getSessions() {
        return Collections.unmodifiableList(sessions);
    }

    public synchronized void orchestrate(@NotNull final OrchestrationSession... sessions) throws Exception {
        for (final OrchestrationSession session : sessions) {
            this.sessions.add(session);
            session.validate();
        }
    }

    public void clear(@NotNull final OrchestrationSession... sessionsToRemove) {
        if (sessionsToRemove != null && CollectionUtils.isNotEmpty(sessions)) {
            synchronized (this) {
                final Set<String> sessionIdsToRemove = Arrays.stream(sessionsToRemove)
                        .map(OrchestrationSession::getSessionId)
                        .collect(Collectors.toSet());
                final Iterator<OrchestrationSession> iterator = this.sessions.iterator();
                while (iterator.hasNext()) {
                    final OrchestrationSession session = iterator.next();
                    if (sessionIdsToRemove.contains(session.getSessionId())) {
                        iterator.remove();
                    }
                }
            }
        }
    }

}
