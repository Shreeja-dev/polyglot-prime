package org.techbd.controller.http.hub.prime.api;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.techbd.service.http.hub.prime.AppConfig;
import org.techbd.util.Constants;
import org.techbd.util.FHIRUtil;

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
  private final AppConfig appConfig;

  public CsvController(CsvService csvService,AppConfig appConfig) {
    this.csvService = csvService;
    this.appConfig = appConfig;
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
      @Parameter(hidden = true, description = "Parameter to specify origin of the request.", required = false) @RequestParam(value = "origin", required = false,defaultValue = "HTTP") String origin,
      @Parameter(hidden = true, description = "Parameter to specify sftp session id.", required = false) @RequestParam(value = "sftp-session-id", required = false) String sftpSessionId,
      HttpServletRequest request,
      HttpServletResponse response)
      throws Exception {

    validateFile(file);
    validateTenantId(tenantId);
    Map<String, String> requestParameters = getRequestParameters(request.getRequestURI(), request.getRequestedSessionId(), origin, sftpSessionId);
    Map<String, String> headerParameters = getHeaderParameters(tenantId);

    csvService.setRequestParameters(requestParameters);
    csvService.setHeaderParameters(headerParameters);
    return csvService.validateCsvFile(file.getBytes(),file.getOriginalFilename());
  }

  @PostMapping(value = { "/flatfile/csv/Bundle", "/flatfile/csv/Bundle/" }, consumes = {
      MediaType.MULTIPART_FORM_DATA_VALUE })
  @ResponseBody
  @Async
  public ResponseEntity<Object> handleCsvUploadAndConversion(
      @Parameter(description = "ZIP file containing CSV data. Must not be null.", required = true) @RequestPart("file") @Nonnull MultipartFile file,
      @Parameter(description = "Parameter to specify the Tenant ID. This is a <b>mandatory</b> parameter.", required = true) @RequestHeader(value = Configuration.Servlet.HeaderName.Request.TENANT_ID, required = true) String tenantId,
      @Parameter(description = "Optional parameter to specify the base FHIR URL. If provided, it will be used in the generated FHIR; otherwise, the default value from `application.yml` will be used.", required = false) @RequestHeader(value = "X-TechBD-Base-FHIR-URL", required = false) String baseFHIRURL,
      @Parameter(hidden = true, description = "Parameter to specify origin of the request.", required = false) @RequestParam(value = "origin", required = false,defaultValue = "HTTP") String origin,
      @Parameter(hidden = true, description = "Parameter to specify sftp session id.", required = false) @RequestParam(value = "sftp-session-id", required = false) String sftpSessionId,
      HttpServletRequest request,
      HttpServletResponse response) throws Exception {
        
    validateFile(file);
    validateTenantId(tenantId);
    
    Map<String, String> requestParameters = getRequestParameters(request.getRequestURI(), request.getRequestedSessionId(), origin, sftpSessionId);
    Map<String, String> headerParameters = getHeaderParameters(tenantId);

    csvService.setRequestParameters(requestParameters);
    csvService.setHeaderParameters(headerParameters);
    FHIRUtil.validateBaseFHIRProfileUrl(appConfig, baseFHIRURL);
    List<Object> processedFiles = csvService.processZipFile(file.getBytes(),file.getOriginalFilename(), request, response, tenantId, origin, sftpSessionId,baseFHIRURL);
    return ResponseEntity.ok(processedFiles);
  
  }
  public Map<String, String> getRequestParameters(String requestURI, String requestedSessionId, String origin, String sftpSessionId) {
    Map<String, String> requestParameters = new HashMap<>();
    if (requestURI != null) requestParameters.put(Constants.REQUEST_URI, requestURI);
    requestParameters.put(Constants.INTERACTION_ID, UUID.randomUUID().toString());
    if (requestedSessionId != null) requestParameters.put(Constants.REQUESTED_SESSION_ID, requestedSessionId);
    if (origin != null) requestParameters.put(Constants.ORIGIN, origin);
    if (sftpSessionId != null) requestParameters.put(Constants.SFTP_SESSION_ID, sftpSessionId);
    return requestParameters;
}

public Map<String, String> getHeaderParameters(String tenantId) {
    Map<String, String> headerParameters = new HashMap<>();    
    if (tenantId != null) headerParameters.put(Constants.USER_AGENT, tenantId);
    return headerParameters;
}
}
