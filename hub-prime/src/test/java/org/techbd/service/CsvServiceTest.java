package org.techbd.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.techbd.orchestrate.csv.CsvOrchestrationEngine;
import org.techbd.orchestrate.csv.CsvOrchestrationEngine.OrchestrationSessionBuilder;
import org.techbd.service.http.Interactions;
import org.techbd.service.http.InteractionsFilter;
import org.techbd.udi.UdiPrimeJpaConfig;
import org.techbd.util.Constants;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jooq.DSLContext;

class CsvServiceTest {
    @Mock
    private CsvOrchestrationEngine engine;
    @Mock
    private UdiPrimeJpaConfig udiPrimeJpaConfig;
    @Mock
    private CsvBundleProcessorService csvBundleProcessorService;
    @Mock
    private OrchestrationSessionBuilder sessionBuilder;
    @InjectMocks
    private CsvService csvService;

    private HttpServletRequest mockRequest;
    private HttpServletResponse mockResponse;
    private MultipartFile testFile;
    private static final String TEST_TENANT_ID = "testTenant";
    private static final String TEST_ORIGIN = "testOrigin";
    private static final String TEST_SFTP_SESSION_ID = "testSessionId";
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockRequest = mock(HttpServletRequest.class);
        mockResponse = mock(HttpServletResponse.class);
        testFile = new MockMultipartFile("test.csv", "test.csv", "text/csv", "test data".getBytes());
        csvService.setHeaderParameters(getHeaderParameters());
        csvService.setRequestParameters(getRequestParameters());
        when(engine.session()).thenReturn(sessionBuilder);
        when(sessionBuilder.withMasterInteractionId(anyString())).thenReturn(sessionBuilder);
        when(sessionBuilder.withSessionId(anyString())).thenReturn(sessionBuilder);
        when(sessionBuilder.withTenantId(anyString())).thenReturn(sessionBuilder);
        when(sessionBuilder.withFile(any())).thenReturn(sessionBuilder);
        when(sessionBuilder.withRequestParameters(any())).thenReturn(sessionBuilder);
        when(sessionBuilder.withHeaderParameters(any())).thenReturn(sessionBuilder);
    }

    @Nested
    class ValidateCsvFileTests {
          @Test
    public void shouldReturnsValidateCsvFile_ValidationResults() throws Exception {
        // Arrange
        MultipartFile file = new MockMultipartFile("test.csv", "test.csv", "text/csv", "test data".getBytes());
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        String tenantId = "testTenant";
        String origin = "testOrigin";
        String sftpSessionId = "testSessionId";

        // Mock InteractionsFilter and its return value
        InteractionsFilter interactionsFilter = mock(InteractionsFilter.class);
        Interactions.RequestEncountered mockRequestEncountered = mock(Interactions.RequestEncountered.class);
        when(interactionsFilter.getActiveRequestEnc(request)).thenReturn(mockRequestEncountered);
        // Mock requestId to return a UUID
        UUID mockRequestId = UUID.randomUUID();
        when(mockRequestEncountered.requestId()).thenReturn(mockRequestId);

        DSLContext dslContext = mock(DSLContext.class);
        when(udiPrimeJpaConfig.dsl()).thenReturn(dslContext);

        CsvOrchestrationEngine.OrchestrationSession session = mock(CsvOrchestrationEngine.OrchestrationSession.class);

        when(engine.session()).thenReturn(sessionBuilder);
        when(sessionBuilder.withMasterInteractionId(anyString())).thenReturn(sessionBuilder);
        when(sessionBuilder.withSessionId(anyString())).thenReturn(sessionBuilder);
        when(sessionBuilder.withTenantId(tenantId)).thenReturn(sessionBuilder);
        when(sessionBuilder.withFile(file)).thenReturn(sessionBuilder);
        when(sessionBuilder.withRequestParameters(any())).thenReturn(sessionBuilder);
        when(sessionBuilder.withHeaderParameters(any())).thenReturn(sessionBuilder);
        when(sessionBuilder.build()).thenReturn(session);

        Map<String, Object> expectedValidationResults = new HashMap<>();
        expectedValidationResults.put("result", "Test Result");
        when(session.getValidationResults()).thenReturn(expectedValidationResults);

        // Act
        Object result = csvService.validateCsvFile(file);

        // Assert
        assertNotNull(result);
        assertEquals(expectedValidationResults, result);
        verify(engine).orchestrate(session);
    }

        @Test
        void shouldHandleOrchestrationException() {
            // Arrange
            CsvOrchestrationEngine.OrchestrationSession mockSession = mock(CsvOrchestrationEngine.OrchestrationSession.class);
            when(sessionBuilder.build()).thenReturn(mockSession);

            // Act & Assert
            assertThrows(RuntimeException.class, () -> 
                csvService.validateCsvFile(
                    testFile
                )
            );
        }

        @Nested
        class InputValidationTests {
            @Test
            void shouldRequireNonNullFile() {
                assertThrows(NullPointerException.class, () ->
                    csvService.validateCsvFile(
                        null
                    )
                );
            }

            @Test
            void shouldRequireNonNullTenantId() {
                assertThrows(NullPointerException.class, () ->
                    csvService.validateCsvFile(
                        testFile
                    )
                );
            }
            
            @Test
            void shouldValidateAllRequiredParameters() {
                // Testing all null parameter combinations in one test
                assertAll(
                    () -> assertThrows(NullPointerException.class, () ->
                        csvService.validateCsvFile(null)),
                    () -> assertThrows(NullPointerException.class, () ->
                        csvService.validateCsvFile(testFile)),
                    () -> assertThrows(NullPointerException.class, () ->
                        csvService.validateCsvFile(testFile))
                );
            }
        }
    }

    private InteractionsFilter setupInteractionsFilter() {
        InteractionsFilter interactionsFilter = mock(InteractionsFilter.class);
        Interactions.RequestEncountered mockRequestEncountered = mock(Interactions.RequestEncountered.class);
        when(interactionsFilter.getActiveRequestEnc(mockRequest)).thenReturn(mockRequestEncountered);
        when(mockRequestEncountered.requestId()).thenReturn(UUID.randomUUID());
        return interactionsFilter;
    }

    public Map<String, String> getRequestParameters() {
        return Map.of(
            Constants.REQUEST_URI, "/api/resource",
            Constants.INTERACTION_ID, UUID.randomUUID().toString(),
            Constants.REQUESTED_SESSION_ID, "mockSessionId123",
            Constants.ORIGIN, "MockOrigin",
            Constants.SFTP_SESSION_ID, "MockSftpSessionId"
        );
    }

    public Map<String, String> getHeaderParameters() {
        return Map.of(
            Constants.USER_AGENT, "MockTenantId",
            Constants.TENANT_ID, "MockSftpSessionId"
        );
    }
}



