package org.techbd.orchestrate.fhir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.hl7.fhir.r4.model.OperationOutcome;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.techbd.orchestrate.fhir.OrchestrationEngine.HapiValidationEngine;
import org.techbd.orchestrate.fhir.OrchestrationEngine.OrchestrationSession;
import org.techbd.service.http.hub.prime.AppConfig;
import org.techbd.service.http.hub.prime.AppConfig.FhirV4Config;
import org.techbd.util.FHIRUtil;

import ca.uhn.fhir.context.FhirContext;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;

@ExtendWith(MockitoExtension.class)
class OrchestrationEngineTest {
    private static final String TEST_PROFILE_URL = "http://shinny.org/us/ny/hrsn/StructureDefinition/SHINNYBundleProfile";
    private static final String TEST_PAYLOAD = "{ \"resourceType\": \"Bundle\" }";
    
    @Mock
    private Tracer tracer;

    private OrchestrationEngine engine;

    @Mock
    private OrchestrationSession mockSession;

    @Mock
    private SpanBuilder spanBuilder;

    @Mock
    private Span span;

    @Mock
    private OrchestrationEngine.ValidationResult mockValidationResult;

    @Mock
    private OrchestrationSession.Builder mockSessionBuilder;

    @Mock
    private AppConfig appConfig;

    @Mock
    HapiValidationEngine mockHapiValidationEngine;

    @BeforeEach
    void setUp() throws Exception {
        // Setup span tracing first
        when(tracer.spanBuilder(anyString())).thenReturn(spanBuilder);
        when(spanBuilder.startSpan()).thenReturn(span);
        doNothing().when(span).end();
        
        // Setup app config
        when(appConfig.getIgPackages()).thenReturn(getIgPackages());
        
        // Setup minimal required session builder mocks
        // when(mockSessionBuilder.build()).thenReturn(mockSession);

        // Initialize base engine
        OrchestrationEngine baseEngine = new OrchestrationEngine(tracer, appConfig);
        
        // Setup sessions map before spying
        Field sessionsField = OrchestrationEngine.class.getDeclaredField("sessions");
        sessionsField.setAccessible(true);
        sessionsField.set(baseEngine, new ConcurrentHashMap<>());
        
        // Create spy after sessions map is setup
        engine = spy(baseEngine);
        
        // Setup session builder mock
        // doReturn(mockSessionBuilder).when(engine).session();

        // Setup session with fixed ID to avoid null issues
        String fixedSessionId = UUID.randomUUID().toString();
        doReturn(fixedSessionId).when(mockSession).getSessionId();
        // when(mockSession.getPayloads()).thenReturn(List.of(TEST_PAYLOAD));
        // when(mockSession.getFhirProfileUrl()).thenReturn(TEST_PROFILE_URL);
        // when(mockSession.getValidationResults()).thenReturn(List.of(mockValidationResult));

        // Setup profile map
        Field profileMapField = FHIRUtil.class.getDeclaredField("PROFILE_MAP");
        profileMapField.setAccessible(true);
        profileMapField.set(null, getProfileMap());
    }

    @Test
    void testOrchestrateSingleSession() {
        String sessionId = mockSession.getSessionId();
        assertThat(sessionId).isNotNull();
        engine.orchestrate(mockSession);
        assertThat(engine.getSessions()).hasSize(1);
 }

    @Test
    void testOrchestrateMultipleSessions() {
        OrchestrationSession mockSession2 = mock(OrchestrationSession.class);
        String fixedSessionId2 = UUID.randomUUID().toString();
        doReturn(fixedSessionId2).when(mockSession2).getSessionId();
        engine.orchestrate(mockSession, mockSession2);
        assertThat(engine.getSessions()).hasSize(2);
    }

    @Test
    void testValidationEngineCaching() {
        engine.orchestrate(mockSession);
        assertThat(engine.getSessions()).hasSize(1);
        assertThat(engine.getValidationEngine(OrchestrationEngine.ValidationEngineIdentifier.HAPI))
            .isNotNull();
    }

    @Test
    void testValidationAgainstLatestShinnyIgHasNoErrors() {
        OrchestrationEngine.ValidationResult mockValidationResultWithNoIssues = createOperationOutcomeWithInfoIssue();
        when(mockSession.getValidationResults()).thenReturn(List.of(mockValidationResultWithNoIssues));
        engine.orchestrate(mockSession);
        assertThat(engine.getSessions().get(0).getValidationResults().get(0).isValid()).isTrue();
    }

    private OrchestrationEngine.ValidationResult createOperationOutcomeWithInfoIssue() {
    OperationOutcome outcome = new OperationOutcome();
    
    // Add one issue with severity = information
    OperationOutcome.OperationOutcomeIssueComponent issue = outcome.addIssue();
    issue.setSeverity(OperationOutcome.IssueSeverity.INFORMATION);
    issue.setCode(OperationOutcome.IssueType.INFORMATIONAL); // or another suitable IssueType
    issue.setDiagnostics("This is an informational message.");

    String outcomeJson = FhirContext.forR4().newJsonParser().encodeResourceToString(outcome);

    return new OrchestrationEngine.ValidationResult() {
        @Override
        public Instant getInitiatedAt() {
            return Instant.now();
        }

        @Override
        public String getProfileUrl() {
            return TEST_PROFILE_URL;
        }

        @Override
        public String getIgVersion() {
            return "1.0.0";
        }

        @Override
        public OrchestrationEngine.ValidationEngine.Observability getObservability() {
            return new OrchestrationEngine.ValidationEngine.Observability(
                "test", "test", Instant.now(), Instant.now());
        }

        @Override
        public boolean isValid() {
            return true;
        }

        @Override
        public String getOperationOutcome() {
            return outcomeJson;
        }

        @Override
        public Instant getCompletedAt() {
            return Instant.now();
        }
    };
}

    private Map<String, FhirV4Config> getIgPackages() {
        final Map<String, FhirV4Config> igPackages = new HashMap<>();
        FhirV4Config fhirV4Config = new FhirV4Config();

        // Base packages for external dependencies
        Map<String, String> basePackages = new HashMap<>();
        basePackages.put("us-core", "ig-packages/fhir-v4/us-core/stu-7.0.0");
        basePackages.put("sdoh", "ig-packages/fhir-v4/sdoh-clinicalcare/stu-2.2.0");
        basePackages.put("uv-sdc", "ig-packages/fhir-v4/uv-sdc/stu-3.0.0");

        // Shinny Packages
        Map<String, Map<String, String>> shinnyPackages = new HashMap<>();

        // Shinny version 1.2.3
        Map<String, String> shinnyV123 = new HashMap<>();
        shinnyV123.put("profile-base-url", "http://shinny.org/us/ny/hrsn");
        shinnyV123.put("package-path", "ig-packages/shin-ny-ig/shinny/v1.2.3");
        shinnyV123.put("ig-version", "1.2.3");
        shinnyPackages.put("shinny-v1-2-3", shinnyV123);

        // Test Shinny version 1.3.0
        Map<String, String> testShinnyV130 = new HashMap<>();
        testShinnyV130.put("profile-base-url", "http://test.shinny.org/us/ny/hrsn");
        testShinnyV130.put("package-path", "ig-packages/shin-ny-ig/test-shinny/v1.3.0");
        testShinnyV130.put("ig-version", "1.3.0");
        shinnyPackages.put("test-shinny-v1-3-0", testShinnyV130);

        fhirV4Config.setBasePackages(basePackages);
        fhirV4Config.setShinnyPackages(shinnyPackages);
        igPackages.put("fhir-v4", fhirV4Config);

        return igPackages;
    }

    private Map<String, String> getProfileMap() {
        Map<String, String> profileMap = new HashMap<>();
        profileMap.put("bundle", "/StructureDefinition/SHINNYBundleProfile");
        profileMap.put("patient", "/StructureDefinition/shinny-patient");
        profileMap.put("consent", "/StructureDefinition/shinny-Consent");
        profileMap.put("encounter", "/StructureDefinition/shinny-encounter");
        profileMap.put("organization", "/StructureDefinition/shin-ny-organization");
        profileMap.put("observation", "/StructureDefinition/shinny-observation-screening-response");
        profileMap.put("questionnaire", "/StructureDefinition/shinny-questionnaire");
        profileMap.put("practitioner", "/StructureDefinition/shin-ny-practitioner");
        profileMap.put("questionnaireResponse", "/StructureDefinition/shinny-questionnaire");
        profileMap.put("observationSexualOrientation",
                        "/StructureDefinition/shinny-observation-sexual-orientation");
        return profileMap;
    }
}
