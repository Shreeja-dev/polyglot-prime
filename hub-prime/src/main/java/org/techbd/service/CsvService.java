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
import org.techbd.orchestrate.csv.CsvOrchestrationEngine;
import org.techbd.service.constants.Origin;
import org.techbd.service.http.Interactions;
import org.techbd.udi.UdiPrimeJpaConfig;
import org.techbd.udi.auto.jooq.ingress.routines.RegisterInteractionHttpRequest;
import org.techbd.util.Constants;

import com.fasterxml.jackson.databind.JsonNode;

import io.micrometer.common.util.StringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Setter;

@Service
@Setter
public class CsvService {

    private CsvOrchestrationEngine engine;
    private static final Logger LOG = LoggerFactory.getLogger(CsvService.class);
    private Map<String,String> requestParameters;
    private Map<String,String> headerParameters;
    @Value("${org.techbd.service.http.interactions.saveUserDataToInteractions:true}")
    private boolean saveUserDataToInteractions;

    private UdiPrimeJpaConfig udiPrimeJpaConfig;
    private CsvBundleProcessorService csvBundleProcessorService;

    public Object validateCsvFile(final byte[]content,final String originalFileName) throws Exception {
        CsvOrchestrationEngine.OrchestrationSession session = null;
        try {
            final var dslContext = udiPrimeJpaConfig.dsl();
            final var jooqCfg = dslContext.configuration();
            saveArchiveInteraction(jooqCfg, content,originalFileName, headerParameters.get(Constants.TENANT_ID),requestParameters.get(Constants.ORIGIN),requestParameters.get(Constants.SFTP_SESSION_ID));
            session = engine.session()
                    .withMasterInteractionId(requestParameters.get(Constants.INTERACTION_ID))
                    .withSessionId(UUID.randomUUID().toString())
                    .withTenantId(headerParameters.get(Constants.TENANT_ID))
                    .withRequestParameters(requestParameters)
                    .withHeaderParameters(headerParameters)
                    .withFileContent(content)
                    .withOriginalFileName(originalFileName)
                    .build();
            engine.orchestrate(session);
            return session.getValidationResults();
        } finally {
            if (null == session) {
                engine.clear(session);
            }
        }
    }

    private void saveArchiveInteraction(final org.jooq.Configuration jooqCfg,
            final byte[] content,final String originalFileName,
            final String tenantId,String origin,String sftpSessionId) {
        final var interactionId = requestParameters.get(Constants.INTERACTION_ID);
        LOG.info("REGISTER State NONE : BEGIN for inteaction id  : {} tenant id : {}",
                interactionId, tenantId);
        final var forwardedAt = OffsetDateTime.now();
        final var initRIHR = new RegisterInteractionHttpRequest();
        try {
            initRIHR.setOrigin(StringUtils.isEmpty(origin) ? Origin.HTTP.name():origin);
            initRIHR.setInteractionId(interactionId);
            initRIHR.setInteractionKey(requestParameters.get(Constants.REQUEST_URI));
            if(StringUtils.isNotEmpty(sftpSessionId)) {
                initRIHR.setSftpSessionId(sftpSessionId);
            }
            initRIHR.setNature((JsonNode) Configuration.objectMapper.valueToTree(
                    Map.of("nature", "Original CSV Zip Archive", "tenant_id",
                            tenantId)));
            initRIHR.setContentType(MimeTypeUtils.APPLICATION_JSON_VALUE);
            initRIHR.setCsvZipFileContent(content);
            initRIHR.setCsvZipFileName(originalFileName);
            initRIHR.setCreatedAt(forwardedAt);
            final InetAddress localHost = InetAddress.getLocalHost();
            final String ipAddress = localHost.getHostAddress();
            initRIHR.setClientIpAddress(ipAddress);
            initRIHR.setUserAgent(headerParameters.get(Constants.USER_AGENT));
            initRIHR.setCreatedBy(CsvService.class.getName());
            final var provenance = "%s.saveArchiveInteraction".formatted(CsvService.class.getName());
            initRIHR.setProvenance(provenance);
            initRIHR.setCsvGroupId(interactionId);
            if (saveUserDataToInteractions) {
                Interactions.setUserDetails(initRIHR, requestParameters);
            }
            final var start = Instant.now();
            final var execResult = initRIHR.execute(jooqCfg);
            final var end = Instant.now();
            LOG.info(
                    "REGISTER State NONE : END for interaction id : {} tenant id : {} .Time taken : {} milliseconds"
                            + execResult,
                    interactionId, tenantId,
                    Duration.between(start, end).toMillis());
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
    public List<Object> processZipFile(final byte[] content,final String originalFileName,final HttpServletRequest request ,HttpServletResponse response ,final String tenantId,String origin,String sftpSessionId) throws Exception {
        CsvOrchestrationEngine.OrchestrationSession session = null;
        try {
            final var dslContext = udiPrimeJpaConfig.dsl();
            final var jooqCfg = dslContext.configuration();
            saveArchiveInteraction(jooqCfg,content,originalFileName, tenantId,origin,sftpSessionId);
            final String masterInteractionId = requestParameters.get(Constants.INTERACTION_ID);
            session = engine.session()
                    .withMasterInteractionId(masterInteractionId)
                    .withSessionId(UUID.randomUUID().toString())
                    .withTenantId(tenantId)
                    .withGenerateBundle(true)
                    .withOriginalFileName(originalFileName)
                    .withFileContent(content)
                    .withRequestParameters(requestParameters)
                    .withHeaderParameters(headerParameters)
                    .build();
            engine.orchestrate(session);
            return csvBundleProcessorService.processPayload(masterInteractionId,
            session.getPayloadAndValidationOutcomes(), session.getFilesNotProcessed(),request,
             response,tenantId,originalFileName);
        } finally {
            if (null == session) {
                engine.clear(session);
            }
        }
    }
    
}
