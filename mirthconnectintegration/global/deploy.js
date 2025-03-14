// Modify the message variable below to pre process data
// This script applies across all channels
var BufferedOutputStream = Packages.java.io.BufferedOutputStream;
var ObjectMapper = Packages.com.fasterxml.jackson.databind.ObjectMapper;
var JavaTimeModule = Packages.com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
var FHIRUtil = Packages.org.techbd.util.fhir.FHIRUtil;

/*************************************GLOBAL OBJECTS CREATION  -BEGIN ***************************************************************
 * This block is responsible for initializing and storing singleton-like Java objects  
 * in `globalMap`. Objects that need to be instantiated only once for the application's  
 * lifecycle should be initialized here.  
 *
 * **Purpose & Best Practices:**
 * - Ensures efficient memory utilization and performance optimization.
 * - Reduces redundant object creation by storing instances in `globalMap`.
 * - Objects stored here should be read from `globalMap` within **preprocessors** or **transformers**  
 *   instead of re-instantiating them.
 *
 * **Objects Initialized:**
 * 1. **appConfig** (`org.techbd.config.ConfigLoader`):
 *    - Loads application configuration using `ConfigLoader`.
 *    - Loads all properties from application.yml and the properties in specific environment yml
 *
 * 2. **fhirService** (`org.techbd.service.fhir.FHIRService`):
 *    - Core FHIR service responsible for processing FHIR-related operations.
 *    - Uses `appConfig` for configuration.
 *    - Uses `OrchestrationEngine` (`org.techbd.service.fhir.engine.OrchestrationEngine`)  
 *      for processing FHIR requests.
 *    - Stored in `globalMap` to prevent redundant instantiations.
 *    - Responsible for loading IG packages and resources
 *
 * **Usage:**
 * - Preprocessors and transformers should **retrieve** these objects from `globalMap`  
 *   instead of creating new instances.
 * - To add a new singleton-like Java object, initialize it **once** and store it in `globalMap`.  
 *
 * **Example Retrieval in a Transformer:**
 * ```javascript
 * var fhirService = globalMap.get("fhirService");
 * fhirService.processFHIRData(someData);
 * ```
 */
if (!globalMap.containsKey("appConfig")) {
    var confLoader = new Packages.org.techbd.config.ConfigLoader();
    var appConfig = confLoader.loadConfig("dev"); // TODO: Read env from an environment variable
    
    globalMap.put("appConfig", appConfig);
    FHIRUtil.initialize(appConfig);
}

if (!globalMap.containsKey("fhirService")) {
    var fhirService = new Packages.org.techbd.service.fhir.FHIRService();
    var orchestrationEngine = new Packages.org.techbd.service.fhir.engine.OrchestrationEngine();
    fhirService.setAppConfig(appConfig);
    fhirService.setEngine(orchestrationEngine);
    globalMap.put("fhirService", fhirService);
}

if (!globalMap.containsKey("mapper")) {
    var mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    globalMap.put("mapper", mapper);
}

/********************************************GLOBAL OBJECTS CREATION  -END ******************************************************/

/*
 * This global function is used to build a map of request parameters.  
 * It is called in the preprocessor for every message to populate necessary metadata  
 * required for processing the FHIR request.
 *
 * The function ensures that required request parameters are set and validated  
 * before proceeding with further processing.
 *
 * **Parameters:**
 * @param {string} interactionId - A unique identifier for the interaction.
 *
 * **Request Parameters Included:**
 * - `REQUEST_URI`: The request URI (e.g., `/Bundle/$validate`), determines validation logic.
 * - `INTERACTION_ID`: A unique identifier for tracking the interaction.
 * - `ORIGIN`: The source of the request (e.g., HTTP).
 * - `SOURCE_TYPE`: Specifies the type of data source (e.g., FHIR).
 * - `OBSERVABILITY_METRIC_INTERACTION_START_TIME`: A placeholder timestamp (to be replaced dynamically).
 *
 * **Additional Parameters to Consider:**
 * - `customDataLakeApi`: The custom Data Lake API endpoint.
 * - `dataLakeApiContentType`: The content type for Data Lake API requests.
 * - `healthCheck`: A flag indicating whether this is a health check request.
 * - `isSync`: Boolean flag to specify if the request is synchronous.
 * - `provenance`: The provenance information related to the request.
 * - `mtlsStrategy`: The mTLS (mutual TLS) strategy for secure communication.
 * - `groupInteractionId`: A unique identifier for the group-level interaction for csv processing.
 * - `masterInteractionId`: A unique identifier for the master interaction for csv processing.
 * - `requestUriToBeOverridden`: The request URI that should be overridden for CCD processing.
 * - `correlationId`: The correlation ID used for tracking requests across services for CCD processing.
 *
 * **Note:** 
 * - This function must be updated whenever new request parameters are introduced.
 * - Ensure that actual values are read from headers or maps where applicable.
 *
 * @returns {java.util.HashMap} A map containing request parameters.
 */
function getRequestParameters(interactionId) {
    var requestParameters = new Packages.java.util.HashMap();

    var requestUri = "/Bundle/$validate"; // TODO: Replace with actual logic to fetch from headers or maps
    var origin = "HTTP"; // TODO: Replace with actual logic to fetch from headers or maps

    if (requestUri != null) {
        requestParameters.put(Packages.org.techbd.config.Constants.REQUEST_URI, requestUri);
    }
    if (interactionId != null) {
        requestParameters.put(Packages.org.techbd.config.Constants.INTERACTION_ID, interactionId);
    }
    if (origin != null) {
        requestParameters.put(Packages.org.techbd.config.Constants.ORIGIN, origin);
    }

    requestParameters.put(Packages.org.techbd.config.Constants.SOURCE_TYPE, "FHIR"); // Placeholder, update as needed
    requestParameters.put(
        Packages.org.techbd.config.Constants.OBSERVABILITY_METRIC_INTERACTION_START_TIME, 
        "2024-01-24T10:15:30Z"
    ); // TODO: Replace with dynamic timestamp

    return requestParameters;
}
/*
 * This global function is called in the preprocessor for every incoming message.
 * It retrieves and adds necessary header parameters to a HashMap.
 * 
 * - Checks for required header parameters (e.g., User-Agent, Tenant ID).
 * - Ensures any new header parameter required by a channel is added here.
 * - Performs null checks before adding parameters to add only the ones available in request.
 *
 * **Note:** 
 * - This function should be updated whenever a new header parameter is introduced.
 *
 * @returns {java.util.HashMap} A map containing header parameters.
 */
function getHeaderParameters() {
    var headerParameters = new Packages.java.util.HashMap();
    var userAgent = "testuseragent"; // TODO-Placeholder, replace with actual logic to read from maps or headers
    var tenantId = "test123"; // TODO-Placeholder, replace with actual logic to read from maps or headers
    if (userAgent != null) {
        headerParameters.put(Packages.org.techbd.config.Constants.USER_AGENT, userAgent);
    }
    if (tenantId != null) {
        headerParameters.put(Packages.org.techbd.config.Constants.TENANT_ID, tenantId);
    }
    return headerParameters;
} 

/*
* This function invokes the core Java library for FHIR processing.
* Based on the request URI:
* 
* - If the request URI is "/Bundle/$validate", it performs bundle validation 
*   against the SHINNY Implementation Guide (IG) using the profile URL in 
*   the request payload.
* - If the request URI is "/Bundle/$validate", it validates the bundle and 
*   sends both the bundle and the OperationOutcome response to the NYEC API.
*/
function convertMapToJson(map) {
   return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(map);
}

globalMap.put("convertMapToJson", convertMapToJson);
globalMap.put("processFHIRBundle", function(tenantId, channelMap, connectorMessage, responseMap) {
    var fhirService = globalMap.get("fhirService");
    var convertMapToJson = globalMap.get("convertMapToJson");

    if (!fhirService || !convertMapToJson) {
        logger.error("Missing fhirService or convertMapToJson in globalMap.");
        return;
    }

    var requestParameters = channelMap.get("requestParameters");
    var headerParameters = channelMap.get("headerParameters");
    var responseParameters = new Packages.java.util.HashMap();
logger.info("before");
logger.info("data" +connectorMessage.getRawData());
    var bundleJson = JSON.parse(connectorMessage.getRawData());
logger.info("after");
    var validationResults = fhirService.processBundle(
        connectorMessage.getRawData(), 
        requestParameters, 
        headerParameters, 
        responseParameters
    );
    logger.info("received validation results");
    return convertMapToJson(validationResults);
});

globalMap.put("getRequestParameters", getRequestParameters);
globalMap.put("getHeaderParameters", getHeaderParameters);