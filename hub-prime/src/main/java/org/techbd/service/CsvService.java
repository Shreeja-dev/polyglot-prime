package org.techbd.service;

import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;
import org.techbd.conf.Configuration;
import org.techbd.config.Constants;
import org.techbd.orchestrate.csv.CsvOrchestrationEngine;
import org.techbd.service.DataLedgerApiClient.DataLedgerPayload;
import org.techbd.service.constants.Origin;
import org.techbd.service.constants.SourceType;
import org.techbd.service.http.Interactions;
import org.techbd.service.http.InteractionsFilter;
import org.techbd.udi.UdiPrimeJpaConfig;
import org.techbd.udi.auto.jooq.ingress.routines.RegisterInteractionCsvRequest;
import org.techbd.util.fhir.CoreFHIRUtil;

import com.fasterxml.jackson.databind.JsonNode;

import io.micrometer.common.util.StringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
@Service
public class CsvService {

    private final CsvOrchestrationEngine engine;
    private static final Logger LOG = LoggerFactory.getLogger(CsvService.class);
    @Value("${org.techbd.service.http.interactions.saveUserDataToInteractions:true}")
    private boolean saveUserDataToInteractions;
    private final UdiPrimeJpaConfig udiPrimeJpaConfig;
    private final CsvBundleProcessorService csvBundleProcessorService;
	private final DataLedgerApiClient dataLedgerApiClient;
    public CsvService(final CsvOrchestrationEngine engine, final UdiPrimeJpaConfig udiPrimeJpaConfig,
            final CsvBundleProcessorService csvBundleProcessorService,DataLedgerApiClient dataLedgerApiClient) {
        this.engine = engine;
        this.udiPrimeJpaConfig = udiPrimeJpaConfig;
        this.csvBundleProcessorService = csvBundleProcessorService;
        this.dataLedgerApiClient = dataLedgerApiClient;
    }

    public Object validateCsvFile(final MultipartFile file, final HttpServletRequest request,
            final HttpServletResponse response,
            final String tenantId,String origin,String sftpSessionId) throws Exception {
        CsvOrchestrationEngine.OrchestrationSession session = null;
        try {
            final var dslContext = udiPrimeJpaConfig.dsl();
            final var jooqCfg = dslContext.configuration();
            saveArchiveInteraction(jooqCfg, request, file, tenantId,origin,sftpSessionId);
            session = engine.session()
                    .withMasterInteractionId(getBundleInteractionId(request))
                    .withSessionId(UUID.randomUUID().toString())
                    .withTenantId(tenantId)
                    .withFile(file)
                    .withRequest(request)
                    .build();
            engine.orchestrate(session);
            return session.getValidationResults();
        } finally {
            if (null == session) {
                engine.clear(session);
            }
        }
    }

    private String getBundleInteractionId(final HttpServletRequest request) {
        return InteractionsFilter.getActiveRequestEnc(request).requestId()
                .toString();
    }

    private void saveArchiveInteraction(final org.jooq.Configuration jooqCfg, final HttpServletRequest request,
            final MultipartFile file,
            final String tenantId,String origin,String sftpSessionId) {
        final var interactionId = getBundleInteractionId(request);
        LOG.info("REGISTER State NONE : BEGIN for inteaction id  : {} tenant id : {}",
                interactionId, tenantId);
        final var forwardedAt = OffsetDateTime.now();
        final var initRIHR = new RegisterInteractionCsvRequest();
        try {
            initRIHR.setPOrigin(StringUtils.isEmpty(origin) ? Origin.HTTP.name():origin);
            initRIHR.setPInteractionId(interactionId);
            initRIHR.setPInteractionKey(request.getRequestURI());
            if(StringUtils.isNotEmpty(sftpSessionId)) {
                initRIHR.setPSftpSessionId(sftpSessionId);
            }
            initRIHR.setPNature((JsonNode) Configuration.objectMapper.valueToTree(
                    Map.of("nature", "Original CSV Zip Archive", "tenant_id",
                            tenantId)));
            initRIHR.setPContentType(MimeTypeUtils.APPLICATION_JSON_VALUE);
            initRIHR.setPCsvZipFileContent(file.getBytes());
            initRIHR.setPCsvZipFileName(file.getOriginalFilename());
           // initRIHR.setPCreatedAt(forwardedAt);
            final InetAddress localHost = InetAddress.getLocalHost();
            final String ipAddress = localHost.getHostAddress();
            initRIHR.setPClientIpAddress(ipAddress);
            initRIHR.setPUserAgent(request.getHeader("User-Agent"));
            initRIHR.setPCreatedBy(CsvService.class.getName());
            final var provenance = "%s.saveArchiveInteraction".formatted(CsvService.class.getName());
            initRIHR.setPProvenance(provenance);
            initRIHR.setPCsvGroupId(interactionId);
            if (saveUserDataToInteractions) {
                Interactions.setUserDetails(initRIHR, request);
            }
            final var start = Instant.now();
            final var execResult = initRIHR.execute(jooqCfg);
            final var end = Instant.now();
            JsonNode response = initRIHR.getReturnValue();
            Map<String, Object> responseAttributes = CoreFHIRUtil.extractFields(response);
            LOG.info(
                    "REGISTER State NONE : END for interaction id: {} tenant id: {}. Time taken: {} milliseconds error: {}, hub_nexus_interaction_id: {} | execResult: {}",
                    interactionId,
                    tenantId,
                    Duration.between(start, end).toMillis(),
                    responseAttributes.getOrDefault(Constants.KEY_ERROR, "N/A"),
                    responseAttributes.getOrDefault(Constants.KEY_HUB_NEXUS_INTERACTION_ID, "N/A"),
                    execResult);
        } catch (final Exception e) {
            LOG.error("ERROR:: REGISTER State NONE CALL for interaction id : {} tenant id : {}"
                    + initRIHR.getName() + " initRIHR error", interactionId,
                    tenantId,
                    e);
        }
    }
    

    /**
     * Processes a Zip file uploaded as a MultipartFile and extracts data into
     * corresponding lists.
     *
     * @param file The uploaded zip file (MultipartFile).
     * @throws Exception If an error occurs during processing the zip file or CSV
     *                   parsing.
     */
    public List<Object> processZipFile(final MultipartFile file,final HttpServletRequest request ,HttpServletResponse response ,final String tenantId,String origin,String sftpSessionId,String baseFHIRUrl) throws Exception {
        CsvOrchestrationEngine.OrchestrationSession session = null;
        try {
            final String masterInteractionId = getBundleInteractionId(request);
             DataLedgerPayload dataLedgerPayload = DataLedgerPayload.create(tenantId, DataLedgerApiClient.Action.RECEIVED.getValue(), DataLedgerApiClient.Actor.TECHBD.getValue(), masterInteractionId
			);
			final var dataLedgerProvenance = "%s.processZipFile".formatted(CsvService.class.getName());
            dataLedgerApiClient.processRequest(dataLedgerPayload,masterInteractionId,dataLedgerProvenance,SourceType.CSV.name(),null);
            final var dslContext = udiPrimeJpaConfig.dsl();
            final var jooqCfg = dslContext.configuration();
            saveArchiveInteraction(jooqCfg, request, file, tenantId,origin,sftpSessionId);
            session = engine.session()
                    .withMasterInteractionId(masterInteractionId)
                    .withSessionId(UUID.randomUUID().toString())
                    .withTenantId(tenantId)
                    .withGenerateBundle(true)
                    .withFile(file)
                    .withRequest(request)
                    .build();
            engine.orchestrate(session);
            return csvBundleProcessorService.processPayload(masterInteractionId,
            session.getPayloadAndValidationOutcomes(), session.getFilesNotProcessed(),request,
             response,tenantId,file.getOriginalFilename(),baseFHIRUrl);
        } finally {
            if (null == session) {
                engine.clear(session);
            }
        }
    }
    
}
