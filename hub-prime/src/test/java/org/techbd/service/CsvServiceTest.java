package org.techbd.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jooq.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.techbd.orchestrate.csv.CsvOrchestrationEngine;
import org.techbd.orchestrate.csv.OrchestrationSession;
import org.techbd.service.http.hub.prime.AppConfig;
import org.techbd.udi.auto.jooq.ingress.routines.RegisterInteractionHttpRequest;
import org.techbd.util.Constants;
import static org.mockito.MockitoAnnotations.openMocks;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class CsvServiceTest {

    @Mock
    private CsvOrchestrationEngine engine;

    @Mock
    private CsvBundleProcessorService csvBundleProcessorService;

    @Mock
    private AppConfig appConfig;

    @Mock
    private Configuration jooqConfig;

    @Mock
    private Logger logger;

    @Mock
    private Configuration mockJooqConfig;

    @Mock
    private InetAddress mockInetAddress;

    @Mock
    private RegisterInteractionHttpRequest mockHttpRequest;

    @InjectMocks
    private CsvService csvService;

    private byte[] sampleContent;
    private String originalFileName;
    private Map<String, String> requestParameters;
    private Map<String, String> headerParameters;

    @BeforeEach
    void setUp() {
        openMocks(this);
        sampleContent = "sample csv data".getBytes();
        originalFileName = "test.csv";

        requestParameters = Map.of(
                Constants.INTERACTION_ID, UUID.randomUUID().toString(),
                Constants.REQUEST_URI, "/test-uri",
                Constants.ORIGIN, "HTTP");

        headerParameters = Map.of(
                Constants.TENANT_ID, "tenant-123",
                Constants.USER_AGENT, "test-agent");

        csvService.setRequestParameters(requestParameters);
        csvService.setHeaderParameters(headerParameters);
        csvService.setCsvFiles(List.of("file1.csv", "file2.csv"));
        //when(mockInetAddress.getHostAddress()).thenReturn("127.0.0.1");

    }

    @Test
    void validateCsvFile_ShouldReturnValidationResults() throws Exception {
        String json = """
                {
                  "validationResults": [
                    {},
                    {
                      "groupInteractionId": "96ccf132-f783-4484-97f6-ed43b9e062d2",
                      "resourceType": "OperationOutcome",
                      "validationResults": {
                        "errorsSummary": [],
                        "report": {
                          "valid": true,
                          "stats": {
                            "tasks": 4,
                            "errors": 0,
                            "warnings": 0,
                            "seconds": 0.274
                          },
                          "warnings": [],
                          "errors": []
                        }
                      }
                    }
                  ]
                }
                """;
        OrchestrationSession mockSession = mock(OrchestrationSession.class);
        ObjectMapper objectMapper = new ObjectMapper();
        // when(mockSession.getValidationResults()).thenReturn(objectMapper.readValue(json,
        // new TypeReference<Map<String, Object>>() {}));
        doAnswer((Answer<Void>) invocation -> {
            OrchestrationSession sessionArg = invocation.getArgument(0);
            sessionArg.setValidationResults(objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            }));
            return null;
        }).when(engine).orchestrate(any(OrchestrationSession.class));

        Map<String, Object> result = (Map<String, Object>) csvService.validateCsvFile(sampleContent, originalFileName);
        List<Map<String, Object>> validationResults = (List<Map<String, Object>>) result.get("validationResults");
        assertThat(validationResults).isNotNull().hasSize(2);

        // Extract the second element (index 1)
        Map<String, Object> secondResult = validationResults.get(1);
        Map<String, Object> validationResultsMap = (Map<String, Object>) secondResult.get("validationResults");

        // Extract and assert `valid`
        Map<String, Object> report = (Map<String, Object>) validationResultsMap.get("report");
        boolean valid = (boolean) report.get("valid");
        assertThat(valid).isTrue();

        // Extract and assert `errorsSummary`
        List<Object> errorsSummary = (List<Object>) validationResultsMap.get("errorsSummary");
        assertThat(errorsSummary).isEmpty();
        verify(engine, times(1)).orchestrate(any());
    }

    @Test
    void validateCsvFile_ShouldHandleExceptionGracefully() throws Exception {
        assertThrows(Exception.class, () -> {
            doThrow(new RuntimeException("Error in orchestration"))
                    .when(engine).orchestrate(any());
            csvService.validateCsvFile(sampleContent, originalFileName);
        });

        verify(engine, times(1)).orchestrate(any());
    }
@Test
    public void testSaveArchiveInteraction_Success() throws Exception {
        // Arrange
        byte[] content = "sample content".getBytes();
        String originalFileName = "sample.csv";
        String tenantId = "tenant123";
        String origin = "HTTP";
        String sftpSessionId = "sftp123";

        // Mock static method InetAddress.getLocalHost()
        InetAddress localHost = mock(InetAddress.class);
        when(localHost.getHostAddress()).thenReturn("127.0.0.1");

        // Mock RegisterInteractionHttpRequest behavior
        RegisterInteractionHttpRequest mockRequest = mock(RegisterInteractionHttpRequest.class);
        when(mockRequest.execute(any(Configuration.class))).thenReturn(1);

        // Act
        csvService.saveArchiveInteraction(mockJooqConfig, content, originalFileName, tenantId, origin, sftpSessionId);

        // Assert
        ArgumentCaptor<RegisterInteractionHttpRequest> requestCaptor = ArgumentCaptor.forClass(RegisterInteractionHttpRequest.class);
        verify(mockRequest).setOrigin(origin);
        verify(mockRequest).setInteractionId(anyString());
        verify(mockRequest).setInteractionKey(anyString());
        verify(mockRequest).setSftpSessionId(sftpSessionId);
        verify(mockRequest).setNature(any(JsonNode.class));
        verify(mockRequest).setContentType("application/json");
        verify(mockRequest).setCsvZipFileContent(content);
        verify(mockRequest).setCsvZipFileName(originalFileName);
        verify(mockRequest).setCreatedAt(any(OffsetDateTime.class));
        verify(mockRequest).setClientIpAddress("127.0.0.1");
        verify(mockRequest).setUserAgent(anyString());
        verify(mockRequest).setCreatedBy(CsvService.class.getName());
        verify(mockRequest).setProvenance(CsvService.class.getName() + ".saveArchiveInteraction");
        verify(mockRequest).setCsvGroupId(anyString());
        verify(mockRequest).execute(mockJooqConfig);
    }

    @Test
    public void testSaveArchiveInteraction_ExceptionHandling() throws Exception {
        // Arrange
        byte[] content = "sample content".getBytes();
        String originalFileName = "sample.csv";
        String tenantId = "tenant123";
        String origin = "HTTP";
        String sftpSessionId = "sftp123";

        // Mock RegisterInteractionHttpRequest behavior to throw exception
        RegisterInteractionHttpRequest mockRequest = mock(RegisterInteractionHttpRequest.class);
      //  doThrow(new RuntimeException("Execution failed")).when(mockRequest).execute(any(Configuration.class));

        // Act
        csvService.saveArchiveInteraction(mockJooqConfig, content, originalFileName, tenantId, origin, sftpSessionId);

        // Assert
        // Verify that an error was logged
        // (Assuming you have a way to verify logs, e.g., using a logging framework that supports testing)
    }
}
