package org.techbd.controller.http.hub.prime.api;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.techbd.conf.Configuration;
import org.techbd.service.CsvService;
import org.techbd.service.constants.CsvProcessingStatus;
import org.techbd.service.http.hub.CustomMultiPartFileRequestWrapper;

import io.micrometer.common.util.StringUtils;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nonnull;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@Tag(name = "Tech by Design Hub CSV Endpoints", description = "Tech by Design Hub CSV Endpoints")
public class CsvController {
  private static final Logger log = LoggerFactory.getLogger(CsvController.class);
  private final CsvService csvService;

  public CsvController(CsvService csvService) {
    this.csvService = csvService;
  }

  private void validateFile(MultipartFile file) {
    if (file == null || file.isEmpty() || file.getOriginalFilename() == null
        || file.getOriginalFilename().trim().isEmpty()) {
      throw new IllegalArgumentException(" Uploaded file is missing or empty.");
    }

    String originalFilename = file.getOriginalFilename();
    if (!originalFilename.toLowerCase().endsWith(".zip")) {
      throw new IllegalArgumentException(" Uploaded file must have a .zip extension.");
    }
  }

  private void validateTenantId(String tenantId) {
    if (tenantId == null || tenantId.trim().isEmpty()) {
      throw new IllegalArgumentException("Tenant ID must be provided.");
    }
  }

  @PostMapping(value = { "/flatfile/csv/Bundle/$validate",
      "/flatfile/csv/Bundle/$validate/" }, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseBody
  public Object handleCsvUpload(
      @Parameter(description = "ZIP file containing CSV data. Must not be null.", required = true) @RequestPart("file") @Nonnull MultipartFile file,
      @Parameter(description = "Tenant ID, a mandatory parameter.", required = true) @RequestHeader(value = Configuration.Servlet.HeaderName.Request.TENANT_ID) String tenantId,
      @Parameter(description = "Parameter to specify origin of the request.", required = false) @RequestParam(value = "origin", required = false, defaultValue = "HTTP") String origin,
      @Parameter(description = "Parameter to specify sftp session id.", required = false) @RequestParam(value = "sftp-session-id", required = false) String sftpSessionId,
      HttpServletRequest request,
      HttpServletResponse response)
      throws Exception {

    validateFile(file);
    validateTenantId(tenantId);
    return csvService.validateCsvFile(file, request, response, tenantId, origin, sftpSessionId);
  }

  @PostMapping(value = { "/flatfile/csv/Bundle", "/flatfile/csv/Bundle/" }, consumes = {
      MediaType.MULTIPART_FORM_DATA_VALUE })
  @Async
  public Object handleCsvUploadAndConversion(
      @Parameter(description = "ZIP file containing CSV data. Must not be null.", required = true) @RequestPart("file") @Nonnull MultipartFile file,
      @Parameter(description = "Parameter to specify the Tenant ID. This is a <b>mandatory</b> parameter.", required = true) @RequestHeader(value = Configuration.Servlet.HeaderName.Request.TENANT_ID, required = true) String tenantId,
      @Parameter(description = "Parameter to specify origin of the request.", required = false) @RequestParam(value = "origin", required = false, defaultValue = "HTTP") String origin,
      @Parameter(description = "Parameter to specify sftp session id.", required = false) @RequestParam(value = "sftp-session-id", required = false) String sftpSessionId,
      @Parameter(description = "Optional parameter to decide whether the response is to be synchronous or asynchronous.", required = false) @RequestParam(value = "immediate", required = false, defaultValue = "false") boolean isSync,
      HttpServletRequest request,
      HttpServletResponse response) throws Exception {
    validateFile(file);
    validateTenantId(tenantId);
    final HttpServletRequest requestUpdated = new CustomMultiPartFileRequestWrapper(request, file);
    final var requestUri =  request.getRequestURI();
    final var userAgent = request.getHeader("User-Agent");  //TODO - pass to orchestration engine 
    String interactionId = UUID.randomUUID().toString();
    if (isSync) {
      return csvService.processZipFile(file, requestUpdated, response, tenantId, origin, sftpSessionId, interactionId);
    } else {
      String statusEndpoint = String.format("/flatfile/csv/Bundle/status?interactionType=ZIP&interactionId=%s",
          interactionId);
      CompletableFuture.runAsync(() -> {
        try {
          csvService.processZipFile(file, requestUpdated, response, tenantId, origin, sftpSessionId, interactionId);
        } catch (Exception e) {
          log.error("Exception while processing ZIP file with interactionId : {} asynchronously ", interactionId, e);
        }
      });
      return Map.of(
          "status", CsvProcessingStatus.RECEIVED.name(),
          "message",
          "The file has been received and is being processed asynchronously. Use the provided endpoint to check the status.",
          "zipFileInteractionId", interactionId,
          "statusEndpoint", statusEndpoint);
    }
  }

  @PostMapping(value = { "/flatfile/csv/Bundle/$status", "/flatfile/csv/Bundle/$status/" }, consumes = {
      MediaType.APPLICATION_FORM_URLENCODED_VALUE })
  @Async
  public Object handleCsvBundleStatus(
      @Parameter(description = "Parameter to specify the Tenant ID. This is a <b>mandatory</b> parameter.", required = true) @RequestHeader(value = Configuration.Servlet.HeaderName.Request.TENANT_ID, required = true) String tenantId,
      @Parameter(description = "Parameter to specify the interaction type.", required = true) @RequestParam(value = "interaction-type", required = true) String interactionType,
      @Parameter(description = "Parameter to specify the interaction id", required = true) @RequestParam(value = "interaction-id", required = true) String interactionId,
      HttpServletRequest request,
      HttpServletResponse response) throws Exception {

    validateTenantId(tenantId);
    if (StringUtils.isEmpty(interactionId)) {
      throw new IllegalArgumentException("Interaction ID must be provided");
    }
    if (StringUtils.isEmpty(interactionType)) {
      throw new IllegalArgumentException("Interaction Type must be provided");
    }
    return csvService.getInteraction(interactionType, interactionId);
  }
}