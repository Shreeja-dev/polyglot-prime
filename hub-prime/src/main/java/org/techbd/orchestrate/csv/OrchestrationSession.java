package org.techbd.orchestrate.csv;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.vfs2.FileObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.MimeTypeUtils;
import org.techbd.conf.Configuration;
import org.techbd.model.csv.FileDetail;
import org.techbd.model.csv.FileType;
import org.techbd.model.csv.PayloadAndValidationOutcome;
import org.techbd.service.CsvService;
import org.techbd.service.VfsCoreService;
import org.techbd.service.http.hub.prime.AppConfig;
import org.techbd.udi.MirthJooqConfig;
import org.techbd.udi.auto.jooq.ingress.routines.RegisterInteractionHttpRequest;
import org.techbd.udi.auto.jooq.ingress.routines.SatInteractionCsvRequestUpserted;
import org.techbd.util.Constants;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lib.aide.vfs.VfsIngressConsumer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrchestrationSession {
    private String sessionId;
    private String masterInteractionId;
    private Device device;
    private byte[] content;
    private String originalFileName;
    private Map<String, Object> validationResults = new HashMap<>();
    private List<String> filesNotProcessed = new ArrayList<>();
    private List<String> csvFiles;
    private Map<String, PayloadAndValidationOutcome> payloadAndValidationOutcomes = new HashMap<>();
    private String tenantId;
    private boolean generateBundle;
    private Map<String, String> requestParameters; //will be initialised from Mirth Connect
    private Map<String, String> headerParameters; //will be initialised from Mirth Connect
    private AppConfig appConfig;
    private VfsCoreService vfsCoreService = new VfsCoreService();
    private static final Logger log = LoggerFactory.getLogger(OrchestrationSession.class);
    private static final Pattern FILE_PATTERN = Pattern.compile(
            "(DEMOGRAPHIC_DATA|QE_ADMIN_DATA|SCREENING)_(.+)");

    public void validate() throws IOException {
        log.info("CsvOrchestrationEngine : validate - file : {} BEGIN for interaction id : {}",
                originalFileName, masterInteractionId);
        final Instant intiatedAt = Instant.now();
        //TODO - commented as not working from mirth.Check later and uncomment.Now doing extraction from mirth
        // final String uniqueFilename = masterInteractionId + "_"
        //         + (originalFileName != null ? originalFileName : "upload.zip");
        // final Path destinationPath = Path.of(appConfig.getCsv().validation().inboundPath(), uniqueFilename);
        // Files.createDirectories(destinationPath.getParent());

        // // Save the uploaded file to the inbound folder
        // Files.write(destinationPath, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        // log.info("File saved to: {}", destinationPath);

        // Trigger CSV processing and validation
        this.validationResults = processScreenings(masterInteractionId, intiatedAt, originalFileName, tenantId);
      //  saveCombinedValidationResults(validationResults, masterInteractionId);
    }

    private void saveScreeningGroup(final String groupInteractionId, final List<FileDetail> fileDetailList, final String tenantId) {
        final var interactionId = requestParameters.get(Constants.INTERACTION_ID);
        log.info("REGISTER State NONE : BEGIN for inteaction id  : {} tenant id : {}",
                interactionId, tenantId);
        final var dslContext = MirthJooqConfig.dsl();
        final var jooqCfg = dslContext.configuration();
        final var forwardedAt = OffsetDateTime.now();
        final var initRIHR = new RegisterInteractionHttpRequest();
        try {
            initRIHR.setInteractionId(groupInteractionId);
            initRIHR.setGroupHubInteractionId(groupInteractionId);
            initRIHR.setSourceHubInteractionId(masterInteractionId);
            initRIHR.setInteractionKey(requestParameters.get(Constants.REQUEST_URI));
            initRIHR.setNature((JsonNode) Configuration.objectMapper.valueToTree(
                    Map.of("nature", "Original Flat File CSV", "tenant_id",
                            tenantId)));
            initRIHR.setContentType(MimeTypeUtils.APPLICATION_JSON_VALUE);
            initRIHR.setCsvZipFileName(originalFileName);
            initRIHR.setSourceHubInteractionId(interactionId);
            final InetAddress localHost = InetAddress.getLocalHost();
            final String ipAddress = localHost.getHostAddress();
            initRIHR.setClientIpAddress(ipAddress);
            initRIHR.setUserAgent(headerParameters.get(Constants.USER_AGENT));
            for (final FileDetail fileDetail : fileDetailList) {
                switch (fileDetail.fileType()) {
                    case FileType.DEMOGRAPHIC_DATA -> {
                        initRIHR.setCsvDemographicDataFileName(fileDetail.filename());
                        initRIHR.setCsvDemographicDataPayloadText(fileDetail.content());
                    }
                    case FileType.QE_ADMIN_DATA -> {
                        initRIHR.setCsvQeAdminDataFileName(fileDetail.filename());
                        initRIHR.setCsvQeAdminDataPayloadText(fileDetail.content());
                    }
                    case FileType.SCREENING_PROFILE_DATA -> {
                        initRIHR.setCsvScreeningProfileDataFileName(fileDetail.filename());
                        initRIHR.setCsvScreeningProfileDataPayloadText(fileDetail.content());
                    }
                    case FileType.SCREENING_OBSERVATION_DATA -> {
                        initRIHR.setCsvScreeningObservationDataFileName(fileDetail.filename());
                        initRIHR.setCsvScreeningObservationDataPayloadText(fileDetail.content());
                    }
                }
            }

            initRIHR.setCreatedAt(forwardedAt);
            initRIHR.setCreatedBy(CsvService.class.getName());
            initRIHR.setToState("CSV_ACCEPT");
            final var provenance = "%s.saveScreeningGroup"
                    .formatted(CsvService.class.getName());
            initRIHR.setProvenance(provenance);
            initRIHR.setCsvGroupId(interactionId);
            final var start = Instant.now();
            final var execResult = initRIHR.execute(jooqCfg);
            final var end = Instant.now();
            log.info(
                    "REGISTER State NONE : END for interaction id : {} tenant id : {} .Time taken : {} milliseconds"
                            + execResult,
                    interactionId, tenantId,
                    Duration.between(start, end).toMillis());
        } catch (final Exception e) {
            log.error("ERROR:: REGISTER State NONE CALL for interaction id : {} tenant id : {}"
                    + initRIHR.getName() + " initRIHR error", interactionId,
                    tenantId,
                    e);
        }
    }

    public static boolean extractValidValue(Map<String, Object> input) {
        if (input == null || !input.containsKey("validationResults")) {
            return false;
        }

        Object validationResults = input.get("validationResults");

        if (validationResults instanceof ObjectNode) {
            ObjectNode validationResultsNode = (ObjectNode) validationResults;

            // Check if errorSummary exists and is empty
            JsonNode errorsSummaryNode = validationResultsNode.get("errorsSummary");
            if (errorsSummaryNode != null && errorsSummaryNode.isArray() && errorsSummaryNode.size() > 0) {
                return false; // Return false if errorsSummary is not empty
            }

            // Check the "report" node
            JsonNode reportNode = validationResultsNode.get("report");
            if (reportNode != null && reportNode.isObject()) {
                JsonNode validNode = reportNode.get("valid");
                if (validNode != null && validNode.isBoolean()) {
                    return validNode.asBoolean();
                }
            }
        }

        return false;
    }

    private void saveValidationResults(final Map<String, Object> validationResults,
            final String masterInteractionId,
            final String groupInteractionId,
            final String tenantId) {
        final var interactionId = requestParameters.get(Constants.INTERACTION_ID);
        log.info("REGISTER State VALIDATION : BEGIN for inteaction id  : {} tenant id : {}",
                interactionId, tenantId);
        final var dslContext = MirthJooqConfig.dsl();
        final var jooqCfg = dslContext.configuration();
        final var createdAt = OffsetDateTime.now();
        final var initRIHR = new RegisterInteractionHttpRequest();
        try {
            initRIHR.setInteractionId(groupInteractionId);
            initRIHR.setGroupHubInteractionId(groupInteractionId);
            initRIHR.setSourceHubInteractionId(masterInteractionId);
            initRIHR.setInteractionKey(requestParameters.get(Constants.REQUEST_URI));
            initRIHR.setNature((JsonNode) Configuration.objectMapper.valueToTree(
                    Map.of("nature", "CSV Validation Result", "tenant_id",
                            tenantId)));
            initRIHR.setContentType(MimeTypeUtils.APPLICATION_JSON_VALUE);
            initRIHR.setCreatedAt(createdAt);
            initRIHR.setCreatedBy(CsvService.class.getName());
            initRIHR.setPayload((JsonNode) Configuration.objectMapper.valueToTree(validationResults));
            initRIHR.setPayload((JsonNode) Configuration.objectMapper.valueToTree(validationResults));
            initRIHR.setFromState("CSV_ACCEPT");
            if (extractValidValue(validationResults)) {
                initRIHR.setToState("VALIDATION_SUCCESS");
            } else {
                initRIHR.setToState("VALIDATION_FAILED");
            }
            final var provenance = "%s.saveValidationResults"
                    .formatted(CsvService.class.getName());
            initRIHR.setProvenance(provenance);
            initRIHR.setCsvGroupId(interactionId);
            final var start = Instant.now();
            final var execResult = initRIHR.execute(jooqCfg);
            final var end = Instant.now();
            log.info(
                    "REGISTER State VALIDATION : END for interaction id : {} tenant id : {} .Time taken : {} milliseconds"
                            + execResult,
                    interactionId, tenantId,
                    Duration.between(start, end).toMillis());
        } catch (final Exception e) {
            log.error("ERROR:: REGISTER State VALIDATION CALL for interaction id : {} tenant id : {}"
                    + initRIHR.getName() + " initRIHR error", interactionId,
                    tenantId,
                    e);
        }
    }

    private void saveCombinedValidationResults(final Map<String, Object> combinedValidationResults,
            final String masterInteractionId) {
        log.info("SaveCombinedValidationResults: BEGIN for inteaction id  : {} tenant id : {}",
                masterInteractionId, tenantId);
        final var dslContext = MirthJooqConfig.dsl();
        final var jooqCfg = dslContext.configuration();
        final var createdAt = OffsetDateTime.now();
        final var initRIHR = new SatInteractionCsvRequestUpserted();
        try {
            initRIHR.setInteractionId(masterInteractionId);
            initRIHR.setUri(requestParameters.get(Constants.REQUEST_URI));
            initRIHR.setNature("Update Zip File Payload");
            initRIHR.setCreatedAt(createdAt);
            initRIHR.setCreatedBy(CsvService.class.getName());
            initRIHR.setValidationResultPayload(
                    (JsonNode) Configuration.objectMapper.valueToTree(combinedValidationResults));
            final var start = Instant.now();
            final var execResult = initRIHR.execute(jooqCfg);
            final var end = Instant.now();
            log.info(
                    "SaveCombinedValidationResults : END for interaction id : {} tenant id : {} .Time taken : {} milliseconds"
                            + execResult,
                    masterInteractionId, tenantId,
                    Duration.between(start, end).toMillis());
        } catch (final Exception e) {
            log.error("ERROR:: saveCombinedValidationResults CALL for interaction id : {} tenant id : {}"
                    + initRIHR.getName() + " initRIHR error", masterInteractionId,
                    tenantId,
                    e);
        }
    }

    /**
     * Extracts the "provenance" object from the provided map.
     *
     * @param operationOutcomeForThisGroup A map containing operation outcome
     *                                     details.
     * @return A map representing the "provenance" object, or an empty map if
     *         "provenance" is not found.
     */
    public static Map<String, Object> extractProvenance(Map<String, Object> operationOutcomeForThisGroup) {
        return Optional.ofNullable(operationOutcomeForThisGroup)
                .map(map -> (Map<String, Object>) map.get("provenance"))
                .orElse(Map.of());
    }

    private static Map<String, Object> createOperationOutcome(final String masterInteractionId,
            final String groupInteractionId,
            final String validationResults,
            final List<FileDetail> fileDetails, final long zipFileSize,
            final Instant initiatedAt, final Instant completedAt, final String originalFileName) throws Exception {
        final Map<String, Object> provenance = populateProvenance(masterInteractionId,groupInteractionId, fileDetails, initiatedAt,
                completedAt, originalFileName);
        return Map.of(
                "resourceType", "OperationOutcome",
                "zipFileInteractionId",masterInteractionId,
                "groupInteractionId", groupInteractionId,
                "validationResults", Configuration.objectMapper.readTree(validationResults),
                "provenance", provenance);
    }

    public Map<String, Object> generateValidationResults(final String masterInteractionId,
            final long zipFileSize,
            final Instant initiatedAt,
            final Instant completedAt,
            final String originalFileName, final List<Map<String, Object>> combinedValidationResult)
            throws Exception {

        Map<String, Object> result = new HashMap<>();

        final String userAgent = requestParameters.get(Constants.USER_AGENT);
        final Device device = Device.INSTANCE;
        result.put("resourceType", "OperationOutcome");
        result.put("zipFileInteractionId", masterInteractionId);
        result.put("originalFileName", originalFileName);
        result.put("validationResults", combinedValidationResult);
        result.put("requestUri", requestParameters.get(Constants.REQUEST_URI));
        result.put("zipFileSize", zipFileSize);
        result.put("userAgent", userAgent);
        result.put("device", Map.of(
                "deviceId", device.deviceId(),
                "deviceName", device.deviceName()));
        result.put("initiatedAt", initiatedAt.toString());
        result.put("completedAt", completedAt.toString());
        result.put("fileNotProcessed", this.filesNotProcessed);
        return result;
    }

    private static Map<String, Object> populateProvenance(final String masterInteractionId,final String groupInteractionId,
            final List<FileDetail> fileDetails,
            final Instant initiatedAt, final Instant completedAt, final String originalFileName) {
        final List<String> fileNames = fileDetails.stream()
                .map(FileDetail::filename)
                .collect(Collectors.toList());
        return Map.of(
                "resourceType", "Provenance",
                "zipFileInteractionId",masterInteractionId,
                "groupInteractionId", groupInteractionId,
                "agent", List.of(Map.of(
                        "who", Map.of(
                                "coding", List.of(Map.of(
                                        "system", "Validator",
                                        "display", "frictionless version 5.18.0"))))),
                "initiatedAt", initiatedAt,
                "completedAt", completedAt,
                "description", "Validation of  files in " + originalFileName,
                "validatedFiles", fileNames);
    }

    public Map<String, Object> processScreenings(final String masterInteractionId, final Instant initiatedAt,
            final String originalFileName, final String tenantId) {
        try {
            //TODO - commented as not working from mirth.Check later and uncomment.Now doing extraction from mirth
            // log.info("Inbound Folder Path: {} for interactionid :{} ",
            //         appConfig.getCsv().validation().inboundPath(), masterInteractionId);
            // log.info("Ingress Home Path: {} for interactionId : {}",
            //         appConfig.getCsv().validation().ingessHomePath(), masterInteractionId);
            // // Process ZIP files and get the session ID
            // final UUID processId = processZipFilesFromInbound(masterInteractionId);
            // log.info("ZIP files processed with session ID: {} for interaction id :{} ", processId,
            //         masterInteractionId);

            // // Construct processed directory path
            // final String processedDirPath = appConfig.getCsv().validation().ingessHomePath() + "/" + processId
            //         + "/ingress";

            // copyFilesToProcessedDir(processedDirPath);
            // createOutputFileInProcessedDir(processedDirPath);
            // log.info("Attempting to resolve processed directory: {} for interactionId : {}", processedDirPath,
            //         masterInteractionId);

            // // Get processed files for validation
            // final FileObject processedDir = vfsCoreService
            //         .resolveFile(Paths.get(processedDirPath).toAbsolutePath().toString());

            // if (!vfsCoreService.fileExists(processedDir)) {
            //     log.error("Processed directory does not exist: {} for interactionId : {}", processedDirPath,
            //             masterInteractionId);
            //     throw new FileSystemException("Processed directory not found: " + processedDirPath);
            // }

            // Collect CSV files for validation
            // final List<String> csvFiles = scanForCsvFiles(processedDir, masterInteractionId);

            final Map<String, List<FileDetail>> groupedFiles = FileProcessor.processAndGroupFiles(csvFiles);
            List<Map<String, Object>> combinedValidationResults = new ArrayList<>();

            for (Map.Entry<String, List<FileDetail>> entry : groupedFiles.entrySet()) {
                String groupKey = entry.getKey();
                if (groupKey.equals("filesNotProcessed")) {
                    this.filesNotProcessed = entry.getValue().stream().map(FileDetail::filename).toList();
                    combinedValidationResults.add(
                            createOperationOutcomeForFileNotProcessed(
                                    masterInteractionId, this.filesNotProcessed, originalFileName));
                    continue;
                }
                List<FileDetail> fileDetails = entry.getValue();
                Map<String, Object> operationOutcomeForThisGroup;
                final String groupInteractionId = UUID.randomUUID().toString();
                if (isGroupComplete(fileDetails)) {
                    operationOutcomeForThisGroup = validateScreeningGroup(groupInteractionId, groupKey, fileDetails,
                            originalFileName);
                } else {
                    // Incomplete group - generate error operation outcome
                    operationOutcomeForThisGroup = createIncompleteGroupOperationOutcome(
                            groupKey, fileDetails, originalFileName, masterInteractionId);
                    log.warn("Incomplete Group - Missing files for group {}", groupKey);
                }

                combinedValidationResults.add(operationOutcomeForThisGroup);
                if (generateBundle) {
                    this.payloadAndValidationOutcomes.put(groupKey,
                            new PayloadAndValidationOutcome(fileDetails,
                                    isGroupComplete(fileDetails) ? extractValidValue(operationOutcomeForThisGroup)
                                            : false,
                                    groupInteractionId, extractProvenance(operationOutcomeForThisGroup),
                                    operationOutcomeForThisGroup));
                }
            }
            Instant completedAt = Instant.now();
            return generateValidationResults(masterInteractionId, 
                    content.length, initiatedAt, completedAt, originalFileName, combinedValidationResults);
        } catch (final Exception e) {
            log.error("Error in ZIP processing tasklet for interactionId: {}", masterInteractionId, e);
            throw new RuntimeException("Error processing ZIP files: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> createOperationOutcomeForFileNotProcessed(
            final String masterInteractionId,
            final List<String> filesNotProcessed, String originalFileName) {
        if (filesNotProcessed == null || filesNotProcessed.isEmpty()) {
            return Collections.emptyMap();
        }

        StringBuilder diagnosticsMessage = new StringBuilder("Files not processed: in input zip file : ");
        diagnosticsMessage.append(String.join(", ", filesNotProcessed));

        StringBuilder remediation = new StringBuilder("Filenames must start with one of the following prefixes: ");
        for (FileType type : FileType.values()) {
            remediation.append(type.name()).append(", ");
        }
        if (remediation.length() > 0) {
            remediation.setLength(remediation.length() - 2); // Remove trailing comma and space
        }

        Map<String, Object> errorDetails = Map.of(
                "type", "files-not-processed",
                "description", remediation.toString(),
                "message", diagnosticsMessage.toString());

        return Map.of(
                "zipFileInteractionId", masterInteractionId,
                "originalFileName", originalFileName,
                "validationResults", Map.of(
                        "errors", List.of(errorDetails),
                        "resourceType", "OperationOutcome"));
    }

    // Move this method outside of processScreenings
    public boolean isGroupComplete(List<FileDetail> fileDetails) {
        Set<FileType> presentFileTypes = fileDetails.stream()
                .map(FileDetail::fileType)
                .collect(Collectors.toSet());

        // Define required file types
        Set<FileType> requiredFileTypes = Set.of(
                FileType.QE_ADMIN_DATA,
                FileType.SCREENING_OBSERVATION_DATA,
                FileType.SCREENING_PROFILE_DATA,
                FileType.DEMOGRAPHIC_DATA);

        return presentFileTypes.containsAll(requiredFileTypes);
    }

    private Map<String, Object> createIncompleteGroupOperationOutcome(
            String groupKey,
            List<FileDetail> fileDetails,
            String originalFileName,
            String masterInteractionId) throws Exception {

        Instant initiatedAt = Instant.now();
        String groupInteractionId = UUID.randomUUID().toString();

        // Determine missing file types
        Set<FileType> requiredFileTypes = Set.of(
                FileType.QE_ADMIN_DATA,
                FileType.SCREENING_OBSERVATION_DATA,
                FileType.SCREENING_PROFILE_DATA,
                FileType.DEMOGRAPHIC_DATA);

        Set<FileType> presentFileTypes = fileDetails.stream()
                .map(FileDetail::fileType)
                .collect(Collectors.toSet());

        Set<FileType> missingFileTypes = new HashSet<>(requiredFileTypes);
        missingFileTypes.removeAll(presentFileTypes);

        Map<String, Object> operationOutcome = new HashMap<>();
        operationOutcome.put("resourceType", "OperationOutcome");
        operationOutcome.put("zipFileInteractionId", masterInteractionId);

        // Validation Results with Detailed Errors
        Map<String, Object> validationResults = new HashMap<>();
        List<Map<String, Object>> errors = new ArrayList<>();

        for (FileType missingType : missingFileTypes) {
            Map<String, Object> error = new HashMap<>();
            error.put("type", "missing-file-error");
            error.put("description", "Input file received is invalid.");
            error.put("message",
                    "Incomplete Group - Missing " + missingType.name() + " file for group " + groupKey);
            errors.add(error);
        }

        validationResults.put("errors", errors);
        operationOutcome.put("validationResults", validationResults);

        // Provenance Details
        Map<String, Object> provenance = new HashMap<>();
        provenance.put("resourceType", "Provenance");
        provenance.put("groupInteractionId", groupInteractionId);

        // Agent Details
        List<Map<String, Object>> agents = new ArrayList<>();
        Map<String, Object> agent = new HashMap<>();
        Map<String, Object> who = new HashMap<>();
        List<Map<String, Object>> coding = new ArrayList<>();
        Map<String, Object> agentCoding = new HashMap<>();
        agentCoding.put("system", "Validator");
        agentCoding.put("display", "TechByDesign");
        coding.add(agentCoding);
        who.put("coding", coding);
        agent.put("who", who);
        agents.add(agent);

        provenance.put("agent", agents);
        provenance.put("initiatedAt", initiatedAt);
        provenance.put("completedAt", Instant.now());
        provenance.put("description", "Validation of files in " + originalFileName);

        // Validated Files
        List<String> validatedFiles = fileDetails.stream()
                .map(FileDetail::filename)
                .collect(Collectors.toList());
        provenance.put("validatedFiles", validatedFiles);

        operationOutcome.put("provenance", provenance);

        // Save validation results
        saveValidationResults(
                operationOutcome,
                masterInteractionId,
                groupInteractionId,
                tenantId);

        return operationOutcome;
    }

    private Map<String, Object> validateScreeningGroup(String groupInteractionId, String groupKey,
            List<FileDetail> fileDetails,
            String originalFileName) throws Exception {
        Instant initiatedAtForThisGroup = Instant.now();

        // Log the group being processed
        log.info("Processing group {} with {} files for interactionId: {}", groupKey, fileDetails.size(),
                masterInteractionId);
        saveScreeningGroup(groupInteractionId, fileDetails, tenantId);

        // Validate CSV files inside the group
        String validationResults = validateCsvUsingPython(fileDetails, masterInteractionId);
        Instant completedAtForThisGroup = Instant.now();

        Map<String, Object> operationOutomeForThisGroup = createOperationOutcome(masterInteractionId,
                groupInteractionId, validationResults, fileDetails,
                content.length, initiatedAtForThisGroup, completedAtForThisGroup, originalFileName);

        saveValidationResults(operationOutomeForThisGroup, masterInteractionId, groupInteractionId, tenantId);
        return operationOutomeForThisGroup;
    }

    private void createOutputFileInProcessedDir(final String processedDirPathStr) throws IOException {
        final Path processedDirPath = Paths.get(processedDirPathStr);
        final Path outputJsonPath = processedDirPath.resolve("output.json");
        if (Files.notExists(outputJsonPath)) {
            Files.createFile(outputJsonPath);
        }
    }

    public void copyFilesToProcessedDir(final String processedDirPathStr) throws IOException {
        final Path processedDirPath = Paths.get(processedDirPathStr);
        final Path pathToPythonExecutable = Paths.get(appConfig.getCsv().validation().packagePath());
        final Path pathToPythonScript = Paths.get(appConfig.getCsv().validation().pythonScriptPath());
        if (Files.notExists(processedDirPath)) {
            Files.createDirectories(processedDirPath);
        }
        Files.copy(pathToPythonExecutable, processedDirPath.resolve(pathToPythonExecutable.getFileName()),
                StandardCopyOption.REPLACE_EXISTING);
        Files.copy(pathToPythonScript, processedDirPath.resolve(pathToPythonScript.getFileName()),
                StandardCopyOption.REPLACE_EXISTING);
    }

    private UUID processZipFilesFromInbound(final String interactionId)
            throws FileSystemException, org.apache.commons.vfs2.FileSystemException {
        log.info("CsvService : processZipFilesFromInbound - BEGIN for interactionId :{}" + interactionId);
        final FileObject inboundFO = vfsCoreService
                .resolveFile(Paths.get(appConfig.getCsv().validation().inboundPath()).toAbsolutePath().toString());
        final FileObject ingresshomeFO = vfsCoreService
                .resolveFile(
                        Paths.get(appConfig.getCsv().validation().ingessHomePath()).toAbsolutePath().toString());
        if (!vfsCoreService.fileExists(inboundFO)) {
            log.error("Inbound folder does not exist: {} for interactionId :{} ", inboundFO.getName().getPath(),
                    interactionId);
            log.error("Inbound folder does not exist: {} for interactionId :{} ", inboundFO.getName().getPath(),
                    interactionId);
            throw new FileSystemException("Inbound folder does not exist: " + inboundFO.getName().getPath());
        }
        vfsCoreService.validateAndCreateDirectories(ingresshomeFO);
        final VfsIngressConsumer consumer = vfsCoreService.createConsumer(
                inboundFO,
                this::extractGroupId,
                this::isGroupComplete);

        // Important: Capture the returned session UUID and processed file paths
        final UUID processId = vfsCoreService.processFiles(consumer, ingresshomeFO);
        log.info("CsvService : processZipFilesFromInbound - BEGIN for interactionId :{}" + interactionId);
        return processId;
    }

    private List<String> scanForCsvFiles(final FileObject processedDir, String interactionId)
            throws FileSystemException {
        final List<String> csvFiles = new ArrayList<>();

        try {
            final FileObject[] children = processedDir.getChildren();

            if (children == null) {
                log.warn("No children found in processed directory: {} for interactionId :{}",
                        processedDir.getName().getPath(), interactionId);
                log.warn("No children found in processed directory: {} for interactionId :{}",
                        processedDir.getName().getPath(), interactionId);
                return csvFiles;
            }

            for (final FileObject child : children) {
                // Enhanced null and extension checking
                if (child != null
                        && child.getName() != null
                        && "csv".equalsIgnoreCase(child.getName().getExtension())) {
                    log.info("Found CSV file: {}", child.getName().getPath());
                    csvFiles.add(child.getName().getPath());
                }
            }

            if (csvFiles.isEmpty()) {
                log.warn("No CSV files found in directory: {}", processedDir.getName().getPath());
            }
        } catch (final org.apache.commons.vfs2.FileSystemException e) {
            log.error("Error collecting CSV files from directory {}: {}",
                    processedDir.getName().getPath(), e.getMessage(), e);
        }

        return csvFiles;
    }
    public String validateCsvUsingPython(final List<FileDetail> fileDetails, final String interactionId)
            throws Exception {
        log.info("CsvService : validateCsvUsingPython BEGIN for interactionId :{} " + interactionId);
        try {
            final var config = appConfig.getCsv().validation();
            if (config == null) {
                throw new IllegalStateException("CSV validation configuration is null");
            }

            // Enhanced validation input
            if (fileDetails == null || fileDetails.isEmpty()) {
                log.error("No files provided for validation");
                throw new IllegalArgumentException("No files provided for validation");
            }

            // Ensure the files exist and are valid using VFS before running the validation
            final List<FileObject> fileObjects = new ArrayList<>();
            for (final FileDetail fileDetail : fileDetails) {
                log.info("Validating file: {}", fileDetail);
                final FileObject file = vfsCoreService.resolveFile(fileDetail.filePath());
                if (!vfsCoreService.fileExists(file)) {
                    log.error("File not found: {}", fileDetail.filePath());
                    throw new FileNotFoundException("File not found: " + fileDetail.filePath());
                }
                fileObjects.add(file);
            }

            // Validate and create directories
            vfsCoreService.validateAndCreateDirectories(fileObjects.toArray(new FileObject[0]));

            // Build command to run Python script
            final List<String> command = buildValidationCommand(config, fileDetails);

            log.info("Executing validation command: {}", String.join(" ", command));

            final ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.directory(new File(fileDetails.get(0).filePath()).getParentFile());
            processBuilder.command(command);
            processBuilder.redirectErrorStream(true);

            final Process process = processBuilder.start();

            // Capture and handle output/error streams
            final StringBuilder output = new StringBuilder();
            final StringBuilder errorOutput = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;

                while ((line = reader.readLine()) != null) {
                    log.info("argument : " + line);
                    output.append(line).append("\n");
                }
            }

            try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = errorReader.readLine()) != null) {
                    errorOutput.append(line).append("\n");
                }
            }

            final int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.error("Python script execution failed. Exit code: {}, Error: {}",
                        exitCode, errorOutput.toString());
                throw new IOException("Python script execution failed with exit code " +
                        exitCode + ": " + errorOutput.toString());
            }
            log.info("CsvService : validateCsvUsingPython END for interactionId :{} " + interactionId);
            // Return parsed validation results
            return output.toString();

        } catch (IOException | InterruptedException e) {
            log.error("Error during CSV validation: {}", e.getMessage(), e);
            throw new RuntimeException("Error during CSV validation : "+e.getMessage(), e);
        }
    }

    private List<String> buildValidationCommand(final AppConfig.CsvValidation.Validation config,
            final List<FileDetail> fileDetails) {
        final List<String> command = new ArrayList<>();
        command.add(config.pythonExecutable());
        command.add("validate-nyher-fhir-ig-equivalent.py");
        command.add("datapackage-nyher-fhir-ig-equivalent.json");
        List<FileType> fileTypeOrder = Arrays.asList(
                FileType.QE_ADMIN_DATA,
                FileType.SCREENING_PROFILE_DATA,
                FileType.SCREENING_OBSERVATION_DATA,
                FileType.DEMOGRAPHIC_DATA);
        Map<FileType, String> fileTypeToFileNameMap = new HashMap<>();
        for (FileDetail fileDetail : fileDetails) {
            fileTypeToFileNameMap.put(fileDetail.fileType(), fileDetail.filename());
        }
        for (FileType fileType : fileTypeOrder) {
            command.add(fileTypeToFileNameMap.get(fileType)); // Adding the filename in order
        }

        // Pad with empty strings if fewer than 7 files
        while (command.size() < 7) { // 1 (python) + 1 (script) + 1 (package) + 4 (files) //TODO CHECK IF THIS IS
                                     // NEEDED ACCORDING TO NUMBER OF FILES.
            command.add("");
        }

        // Add output path
        // command.add("output.json");

        return command;
    }

    private String extractGroupId(final FileObject file) {
        final String fileName = file.getName().getBaseName();
        final var matcher = FILE_PATTERN.matcher(fileName);
        return matcher.matches() ? matcher.group(2) : null;
    }

    private boolean isGroupComplete(final VfsIngressConsumer.IngressGroup group) {
        if (group == null || group.groupedEntries().isEmpty()) {
            return false;
        }

        boolean hasDemographic = false;
        boolean hasQeAdmin = false;
        boolean hasScreening = false;
        // please add other files also according to command

        for (final VfsIngressConsumer.IngressIndividual entry : group.groupedEntries()) {
            final String fileName = entry.entry().getName().getBaseName();
            if (fileName.startsWith("DEMOGRAPHIC_DATA")) {
                hasDemographic = true;
            } else if (fileName.startsWith("QE_ADMIN_DATA")) {
                hasQeAdmin = true;
            } else if (fileName.startsWith("SCREENING")) {
                hasScreening = true;
            }
        }

        return hasDemographic && hasQeAdmin && hasScreening;
    }
        public record Device(String deviceId, String deviceName) {

        public static final Device INSTANCE = createDefault();

        public static Device createDefault() {
            try {
                final InetAddress localHost = InetAddress.getLocalHost();
                final String ipAddress = localHost.getHostAddress();
                final String hostName = localHost.getHostName();
                return new Device(ipAddress, hostName);
            } catch (final UnknownHostException e) {
                return new Device("Unable to retrieve the localhost information", e.toString());
            }
        }
    }
}
