// This script executes once for each deploy or redeploy task
// You only have access to the globalMap here to persist data
// Function to load AppConfig dynamically from environment variables
// This script executes once for each deploy or redeploy task
// You only have access to the globalMap here to persist data
// Function to load AppConfig dynamically from environment variables
globalMap.put("loadAppConfig", function() {
    var AppConfig = Packages.org.techbd.service.http.hub.prime.AppConfig;
    var CsvValidation = Packages.org.techbd.service.http.hub.prime.AppConfig.CsvValidation;
    var Validation = Packages.org.techbd.service.http.hub.prime.AppConfig.CsvValidation.Validation;
    var DefaultDataLakeApiAuthn = Packages.org.techbd.service.http.hub.prime.AppConfig.DefaultDataLakeApiAuthn;
    var MTlsResources = Packages.org.techbd.service.http.hub.prime.AppConfig.MTlsResources;
    var MTlsAwsSecrets = Packages.org.techbd.service.http.hub.prime.AppConfig.MTlsAwsSecrets;
    var PostStdinPayloadToNyecDataLakeExternal = Packages.org.techbd.service.http.hub.prime.AppConfig.PostStdinPayloadToNyecDataLakeExternal;

    var config = new AppConfig();

    // Fetch Environment Variables using System.getenv and log them
    var version = java.lang.System.getenv("TECHBD_VERSION");
    logger.info("TECHBD_VERSION: " + version);
    config.setVersion(version);

    var fhirVersion = java.lang.System.getenv("TECHBD_FHIR_VERSION");
    logger.info("TECHBD_FHIR_VERSION: " + fhirVersion);
    config.setFhirVersion(fhirVersion);

    var igVersion = java.lang.System.getenv("TECHBD_IG_VERSION");
    logger.info("TECHBD_IG_VERSION: " + igVersion);
    config.setIgVersion(igVersion);

    var baseFhirUrl = java.lang.System.getenv("TECHBD_BASE_FHIR_URL");
    logger.info("TECHBD_BASE_FHIR_URL: " + baseFhirUrl);
    config.setBaseFHIRURL(baseFhirUrl);

    var defaultDataLakeApiUrl = java.lang.System.getenv("TECHBD_DEFAULT_DATALAKE_API_URL");
    logger.info("TECHBD_DEFAULT_DATALAKE_API_URL: " + defaultDataLakeApiUrl);
    config.setDefaultDatalakeApiUrl(defaultDataLakeApiUrl);

    var operationOutcomeHelpUrl = java.lang.System.getenv("TECHBD_OPERATION_OUTCOME_HELP_URL");
    logger.info("TECHBD_OPERATION_OUTCOME_HELP_URL: " + operationOutcomeHelpUrl);
    config.setOperationOutcomeHelpUrl(operationOutcomeHelpUrl);

    // Structure Definitions URLs and log them
    var structureDefinitionsUrls = new java.util.HashMap();
    structureDefinitionsUrls.put("SHINNYBundleProfile", java.lang.System.getenv("TECHBD_STRUCTURE_DEFINITIONS_URLS_BUNDLE"));
    logger.info("SHINNYBundleProfile URL: " + structureDefinitionsUrls.get("SHINNYBundleProfile"));
    structureDefinitionsUrls.put("Patient", java.lang.System.getenv("TECHBD_STRUCTURE_DEFINITIONS_URLS_PATIENT"));
    logger.info("Patient URL: " + structureDefinitionsUrls.get("Patient"));
    structureDefinitionsUrls.put("Consent", java.lang.System.getenv("TECHBD_STRUCTURE_DEFINITIONS_URLS_CONSENT"));
    logger.info("Consent URL: " + structureDefinitionsUrls.get("Consent"));
    structureDefinitionsUrls.put("Encounter", java.lang.System.getenv("TECHBD_STRUCTURE_DEFINITIONS_URLS_ENCOUNTER"));
    logger.info("Encounter URL: " + structureDefinitionsUrls.get("Encounter"));
    structureDefinitionsUrls.put("Organization", java.lang.System.getenv("TECHBD_STRUCTURE_DEFINITIONS_URLS_ORGANIZATION"));
    logger.info("Organization URL: " + structureDefinitionsUrls.get("Organization"));
    structureDefinitionsUrls.put("Observation", java.lang.System.getenv("TECHBD_STRUCTURE_DEFINITIONS_URLS_OBSERVATION"));
    logger.info("Observation URL: " + structureDefinitionsUrls.get("Observation"));
    structureDefinitionsUrls.put("Questionnaire", java.lang.System.getenv("TECHBD_STRUCTURE_DEFINITIONS_URLS_QUESTIONNAIRE"));
    logger.info("Questionnaire URL: " + structureDefinitionsUrls.get("Questionnaire"));
    structureDefinitionsUrls.put("Practitioner", java.lang.System.getenv("TECHBD_STRUCTURE_DEFINITIONS_URLS_PRACTITIONER"));
    logger.info("Practitioner URL: " + structureDefinitionsUrls.get("Practitioner"));
    structureDefinitionsUrls.put("QuestionnaireResponse", java.lang.System.getenv("TECHBD_STRUCTURE_DEFINITIONS_URLS_QUESTIONNAIRERESPONSE"));
    logger.info("QuestionnaireResponse URL: " + structureDefinitionsUrls.get("QuestionnaireResponse"));
    structureDefinitionsUrls.put("ObservationSexualOrientation", java.lang.System.getenv("TECHBD_STRUCTURE_DEFINITIONS_URLS_OBSERVATION_SEXUAL_ORIENTATION"));
    logger.info("ObservationSexualOrientation URL: " + structureDefinitionsUrls.get("ObservationSexualOrientation"));
    config.setStructureDefinitionsUrls(structureDefinitionsUrls);

    // CSV Validation and log the details
    var csvValidation = new CsvValidation(
        new Validation(
            java.lang.System.getenv("TECHBD_CSV_PYTHON_SCRIPT_PATH"),
            java.lang.System.getenv("TECHBD_CSV_PYTHON_EXECUTABLE"),
            java.lang.System.getenv("TECHBD_CSV_PACKAGE_PATH"),
            java.lang.System.getenv("TECHBD_CSV_OUTPUT_PATH"),
            java.lang.System.getenv("TECHBD_CSV_INBOUND_PATH"),
            java.lang.System.getenv("TECHBD_CSV_INGRESS_PATH")
        )
    );
    logger.info("CSV Validation: " + JSON.stringify(csvValidation));
    config.setCsv(csvValidation);

    // Default Data Lake API Auth and log timeout value
    var timeoutValue = parseInt(java.lang.System.getenv("TECHBD_POST_STDIN_PAYLOAD_TO_NYEC_DATALAKE_EXTERNAL_TIMEOUT"));

    if (isNaN(timeoutValue)) {
        logger.warn("TECHBD_POST_STDIN_PAYLOAD_TO_NYEC_DATALAKE_EXTERNAL_TIMEOUT is NaN, setting default to 180.");
        timeoutValue = 180;  // Example default value
    }
    logger.info("timeoutValue: " + timeoutValue);

    var authn = new DefaultDataLakeApiAuthn(
        java.lang.System.getenv("TECHBD_MTLS_STRATEGY"),
        new MTlsAwsSecrets(
            java.lang.System.getenv("TECHBD_MTLS_KEY_SECRET_NAME"),
            java.lang.System.getenv("TECHBD_MTLS_CERT_SECRET_NAME")
        ),
        new PostStdinPayloadToNyecDataLakeExternal(
            java.lang.System.getenv("TECHBD_POST_STDIN_PAYLOAD_TO_NYEC_DATALAKE_EXTERNAL_CMD"),
            timeoutValue
        ),
        new MTlsResources(
            java.lang.System.getenv("TECHBD_MTLS_KEY_RESOURCE_NAME"),
            java.lang.System.getenv("TECHBD_MTLS_CERT_RESOURCE_NAME")
        )
    );
    config.setDefaultDataLakeApiAuthn(authn);

    // IG Packages and log them
    var igPackages = new java.util.HashMap();
    igPackages.put("SHIN_NY", java.lang.System.getenv("TECHBD_IG_PACKAGES_FHIR_V4_SHIN_NY"));
    logger.info("SHIN_NY IG Package: " + igPackages.get("SHIN_NY"));
    igPackages.put("US_CORE", java.lang.System.getenv("TECHBD_IG_PACKAGES_FHIR_V4_US_CORE"));
    logger.info("US_CORE IG Package: " + igPackages.get("US_CORE"));
    igPackages.put("SDOH", java.lang.System.getenv("TECHBD_IG_PACKAGES_FHIR_V4_SDOH"));
    logger.info("SDOH IG Package: " + igPackages.get("SDOH"));
    igPackages.put("UV_SDC", java.lang.System.getenv("TECHBD_IG_PACKAGES_FHIR_V4_UV_SDC"));
    logger.info("UV_SDC IG Package: " + igPackages.get("UV_SDC"));
    config.setIgPackages(igPackages);

    return config;
});

// Log to confirm setup
logger.info("Global AppConfig Loader is set.");
return;
