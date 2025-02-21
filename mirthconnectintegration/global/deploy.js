// This script executes once for each deploy or redeploy task
// You only have access to the globalMap here to persist data

// Function to load AppConfig dynamically from environment variables
function loadAppConfig() {
    var AppConfig = Packages.org.techbd.service.http.hub.prime.AppConfig;
    var CsvValidation = Packages.org.techbd.service.http.hub.prime.AppConfig.CsvValidation;
    var Validation = Packages.org.techbd.service.http.hub.prime.AppConfig.CsvValidation.Validation;
    var DefaultDataLakeApiAuthn = Packages.org.techbd.service.http.hub.prime.AppConfig.DefaultDataLakeApiAuthn;
    var MTlsResources = Packages.org.techbd.service.http.hub.prime.AppConfig.MTlsResources;
    var MTlsAwsSecrets = Packages.org.techbd.service.http.hub.prime.AppConfig.MTlsAwsSecrets;
    var PostStdinPayloadToNyecDataLakeExternal = Packages.org.techbd.service.http.hub.prime.AppConfig.PostStdinPayloadToNyecDataLakeExternal;

    var config = new AppConfig();

    // Fetch Environment Variables
    config.setVersion(java.lang.System.getenv("TECHBD_VERSION"));
    config.setFhirVersion(java.lang.System.getenv("TECHBD_FHIR_VERSION"));
    config.setIgVersion(java.lang.System.getenv("TECHBD_IG_VERSION"));
    config.setBaseFHIRURL(java.lang.System.getenv("TECHBD_BASE_FHIR_URL"));
    config.setDefaultDatalakeApiUrl(java.lang.System.getenv("TECHBD_DEFAULT_DATALAKE_API_URL"));
    config.setOperationOutcomeHelpUrl(java.lang.System.getenv("TECHBD_OPERATION_OUTCOME_HELP_URL"));

    // Structure Definitions URLs
    var structureDefinitionsUrls = new java.util.HashMap();
    var keys = ["BUNDLE", "PATIENT", "CONSENT", "ENCOUNTER", "ORGANIZATION", "OBSERVATION", "QUESTIONNAIRE", "PRACTITIONER", "QUESTIONNAIRERESPONSE", "OBSERVATION_SEXUAL_ORIENTATION"];
    keys.forEach(function(key) {
        var envVar = "TECHBD_STRUCTURE_DEFINITIONS_URLS_" + key;
        structureDefinitionsUrls.put(key, java.lang.System.getenv(envVar));
    });
    config.setStructureDefinitionsUrls(structureDefinitionsUrls);

    // CSV Validation
    config.setCsv(new CsvValidation(new Validation(
        java.lang.System.getenv("TECHBD_CSV_PYTHON_SCRIPT_PATH"),
        java.lang.System.getenv("TECHBD_CSV_PYTHON_EXECUTABLE"),
        java.lang.System.getenv("TECHBD_CSV_PACKAGE_PATH"),
        java.lang.System.getenv("TECHBD_CSV_OUTPUT_PATH"),
        java.lang.System.getenv("TECHBD_CSV_INBOUND_PATH"),
        java.lang.System.getenv("TECHBD_CSV_INGRESS_PATH")
    )));

    // Default Data Lake API Auth
    var timeoutValue = parseInt(java.lang.System.getenv("TECHBD_POST_STDIN_PAYLOAD_TO_NYEC_DATALAKE_EXTERNAL_TIMEOUT")) || 180;
    config.setDefaultDataLakeApiAuthn(new DefaultDataLakeApiAuthn(
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
    ));

    // IG Packages
    var igPackages = new java.util.HashMap();
    ["SHIN_NY", "US_CORE", "SDOH", "UV_SDC"].forEach(function(key) {
        igPackages.put(key, java.lang.System.getenv("TECHBD_IG_PACKAGES_FHIR_V4_" + key));
    });
    config.setIgPackages(igPackages);

    return config;
}

// Store AppConfig in globalMap
globalMap.put("appConfig", loadAppConfig());

// Log to confirm setup
logger.info("AppConfig has been loaded and stored in globalMap.");
return;
