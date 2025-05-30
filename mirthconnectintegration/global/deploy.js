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
    var orchestrationEngine = new Packages.org.techbd.service.fhir.engine.OrchestrationEngine(appConfig);
    var dataLedgerApiClient = new Packages.org.techbd.service.dataledger.DataLedgerApiClient(appConfig);
    fhirService.setAppConfig(appConfig);
    fhirService.setDataLedgerApiClient(dataLedgerApiClient);
    fhirService.setEngine(orchestrationEngine);
    globalMap.put("fhirService", fhirService);
}

if (!globalMap.containsKey("mapper")) {
    var mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    globalMap.put("mapper", mapper);
}

/********************************************GLOBAL OBJECTS CREATION  -END ******************************************************/
/**
 * Retrieves the Interaction ID from the channel map.
 * If not found, returns "UNKNOWN_INTERACTION".
 */
function getInteractionId(channelMap) {
    var interactionId = channelMap.get("interactionId");
    return interactionId ? interactionId : "UNKNOWN_INTERACTION";
}

// Ensure globalMap has the logger functions when the channel starts
globalMap.put("logInfo", function(message,channelMap) {
    var channelId = channelMap.get("channelId");
    var channelName = channelMap.get("channelName");
    logger.info("[ ChannelID: " + channelId + "][ ChannelName: " + channelName + "][ InteractionID: " + getInteractionId(channelMap) + "] " + message);
});

globalMap.put("logError", function(message,channelMap) {
    var channelId = channelMap.get("channelId");
    var channelName = channelMap.get("channelName");	
    logger.error("[ ChannelID: " + channelId + "][ ChannelName: " + channelName + "][ InteractionID: " + getInteractionId(channelMap) + "] " + message);
});

globalMap.put("logDebug", function(message,channelMap) {
    var channelId = channelMap.get("channelId");
    var channelName = channelMap.get("channelName");	
   logger.debug("[ ChannelID: " + channelId + "][ ChannelName: " + channelName + "][ InteractionID: " + getInteractionId(channelMap) + "] " + message);
});

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
 *
 *
 * @returns {java.util.HashMap} A map containing request parameters.
 */
function getRequestParameters(interactionId, channelMap, sourceMap) {
    var requestParameters = new Packages.java.util.HashMap();
    var requestUri = sourceMap.get('contextPath');
    var parameters = sourceMap.get('parameters');
    var requestUrl = sourceMap.get('requestUrl');
    var protocol = sourceMap.get('protocol');
    var localAddress = sourceMap.get('localAddress');
    var remoteAddress = sourceMap.get('remoteAddress');
    var origin = "HTTP";
    var source = "FHIR";
 logger.info("############################################################requestUri =" +requestUri);
    if (parameters && parameters.getParameter("origin")) {
        origin = parameters.getParameter("origin").trim();
    }
    if (parameters && parameters.getParameter("source")) {
        source = parameters.getParameter("source").trim();
    }
	
    if (requestUri && requestUri.trim() !== "") {
        requestParameters.put(Packages.org.techbd.config.Constants.REQUEST_URI, requestUri.trim());
    }
    if (interactionId && interactionId.trim() !== "") {
        requestParameters.put(Packages.org.techbd.config.Constants.INTERACTION_ID, interactionId.trim());
    }
    if (origin && origin.trim() !== "") {
        requestParameters.put(Packages.org.techbd.config.Constants.ORIGIN, origin.trim());
    }
    if (source && source.trim() !== "") {
        requestParameters.put(Packages.org.techbd.config.Constants.SOURCE_TYPE, source.trim());
    }
   // **Fetch predefined constants from parameters and put them in requestParameters**
    var constantsToFetch = [
        Packages.org.techbd.config.Constants.DELETE_USER_SESSION_COOKIE,
        Packages.org.techbd.config.Constants.IMMEDIATE
    ];

    if (parameters) {
        for (var i = 0; i < constantsToFetch.length; i++) {
            var key = constantsToFetch[i];
            var value = parameters.getParameter(key);
		  logger.info("key =" +key +  "   value =   "+value);
            if (value && value.toString().trim() !== "") {
                requestParameters.put(key, value.toString().trim());
            }
        }
    }

    var sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    var currentTime = sdf.format(new java.util.Date());

    requestParameters.put(
        Packages.org.techbd.config.Constants.OBSERVABILITY_METRIC_INTERACTION_START_TIME,
        currentTime
    );

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
 *
 * @returns {java.util.HashMap} A map containing header parameters.
 */
function getHeaderParameters(headers, channelMap, sourceMap) {
    var headerParameters = new Packages.java.util.HashMap();

    var constantsToFetch = [
        Packages.org.techbd.config.Constants.USER_AGENT,
        Packages.org.techbd.config.Constants.TENANT_ID,
        Packages.org.techbd.config.Constants.HEALTH_CHECK,
        Packages.org.techbd.config.Constants.CORRELATION_ID,
        Packages.org.techbd.config.Constants.OVERRIDE_REQUEST_URI,
        Packages.org.techbd.config.Constants.PROVENANCE,
        Packages.org.techbd.config.Constants.DATA_LAKE_API_CONTENT_TYPE,
        Packages.org.techbd.config.Constants.CUSTOM_DATA_LAKE_API,
        Packages.org.techbd.config.Constants.MTLS_STRATEGY,
        Packages.org.techbd.config.Constants.GROUP_INTERACTION_ID,
        Packages.org.techbd.config.Constants.MASTER_INTERACTION_ID,
        Packages.org.techbd.config.Constants.SOURCE_TYPE
    ];

    // Fetch and store only the required headers
    for (var i = 0; i < constantsToFetch.length; i++) {
        var key = constantsToFetch[i];
        var value = headers.getHeader(key);

        if (value && value.toString().trim() !== "") {
            headerParameters.put(key, value.toString().trim());
        }
    }

    // Generate and add Provenance header
    var provenanceHeader = createProvenanceHeader(sourceMap, channelMap);
    if (provenanceHeader) {
        headerParameters.put(Packages.org.techbd.config.Constants.PROVENANCE, provenanceHeader);
    }

    return headerParameters;
}





/*
 * Creates a provenance JSON string to be sent as a header parameter ("X-Provenance").
 * The provenance information is extracted from `sourceMap` and `channelMap`.
 * 
 * @param {Object} sourceMap - Contains details of the incoming request, such as headers, method, URI, etc.
 *   Expected keys:
 *     - headers
 *     - localPort
 *     - method
 *     - remotePort
 *     - contextPath
 *     - uri
 *     - url
 *     - protocol
 *     - remoteAddress
 * 
 * @param {Object} channelMap - Contains additional details related to the processing channel.
 *   Expected keys:
 *     - headerParameters
 *     - requestParameters
 *     - channelName
 *     - startTime
 *     - requestUri
 *     - channelId
 * 
 * @returns {string} - A JSON string representing the provenance information.
 */
function createProvenanceHeader(sourceMap, channelMap) {
    var provenance = new Packages.java.util.HashMap();
    if (sourceMap != null) {
        provenance.put("headers", sourceMap.get("headers"));
        provenance.put("localPort", sourceMap.get("localPort"));
        provenance.put("method", sourceMap.get("method"));
        provenance.put("remotePort", sourceMap.get("remotePort"));
        provenance.put("contextPath", sourceMap.get("contextPath"));
        provenance.put("uri", sourceMap.get("uri"));
        provenance.put("url", sourceMap.get("url"));
        provenance.put("protocol", sourceMap.get("protocol"));
        provenance.put("remoteAddress", sourceMap.get("remoteAddress"));
    }
    if (channelMap != null) {
        provenance.put("headerParameters", channelMap.get("headerParameters"));
        provenance.put("requestParameters", channelMap.get("requestParameters"));
        provenance.put("channelName", channelMap.get("channelName"));
        provenance.put("startTime", channelMap.get("startTime"));
        provenance.put("requestUri", channelMap.get("requestUri"));
        provenance.put("channelId", channelMap.get("channelId"));
    }
    return JSON.stringify(provenance);
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
   var mapper = globalMap.get("mapper");	
   return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(map);
}

globalMap.put("convertMapToJson", convertMapToJson);

/**
 * Global function to validate request header parameters based on rules.
 * 
 * Supported Rules:
 * 1. "isRequired"    - Ensures the parameter is not missing or empty.
 * 2. "isAllowedValue" - Ensures the parameter matches one of the allowed values.
 * 3. "isAValidUrl"    - Validates if the parameter is a properly formatted URL.
 * 4. "isValidUUID"    - Ensures the parameter is a valid UUID.
 * 
 * @param {string} paramName - The name of the header parameter (e.g., "X-TechBD-Tenant-ID").
 * @param {string} paramValue - The value of the header parameter.
 * @param {string} validationRule - The rule to execute (e.g., "isRequired", "isValidUUID").
 * @param {Object} responseMap - The responseMap object to update with errors.
 * @param {int} statusCode - HTTP status code (e.g., 400 for BadRequest).
 * @param {Array} allowedValues - Optional: List of allowed values (used for "isAllowedValue").
 * @returns {boolean} - Returns true if validation fails, otherwise false.
 */
function validate(paramName, paramValue, validationRule, responseMap, statusCode, allowedValues) {
    var UUID = Packages.java.util.UUID;
    var isValid = true;
    var errorMessage = "";

    switch (validationRule) {
        case "isRequired":
            if (!paramValue || paramValue.trim() === "") {
                errorMessage = paramName + " must be provided";
                isValid = false;
            }
            break;

        case "isAllowedValue":
            if (!allowedValues || allowedValues.indexOf(paramValue) === -1) {
                errorMessage = paramName + " must be one of: " + JSON.stringify(allowedValues);
                isValid = false;
            }
            break;

        case "isAValidUrl":
            try {
                var url = new java.net.URL(paramValue);
            } catch (e) {
                errorMessage = paramName + " is not a valid URL";
                isValid = false;
            }
            break;

        case "isValidUUID":
            try {
                UUID.fromString(paramValue);
            } catch (e) {
                errorMessage = paramName + " should be a valid UUID";
                isValid = false;
            }
            break;

        default:
            errorMessage = "Unknown validation rule: " + validationRule;
            isValid = false;
    }

    if (!isValid) {
        logger.error("Validation Error: " + errorMessage);
        responseMap.put('status', statusCode.toString());
        responseMap.put('error', errorMessage);
        responseMap.put('result', JSON.stringify({ "status": "Error", "message": errorMessage }));
    }

    return !isValid;  // Returns true if validation fails
}

/*
 * This function Processes an FHIR Bundle by validating it using the FHIR service.
 *
 * @function processFHIRBundle
 * @param {string} tenantId - The tenant identifier for multi-tenancy support.
 * @param {Map} channelMap - A map containing request-related parameters.
 * @param {Object} connectorMessage - The connector message containing the raw FHIR Bundle.
 * @param {Map} responseMap - A map to store response-related parameters.
 * @returns {string} - A JSON string containing OperationOutcome of fhir bundle validated against 
 * SHIN-NY IG with hapi-fhir validator 
 */
globalMap.put("processFHIRBundle", function(tenantId, channelMap, connectorMessage, responseMap) {
    var fhirService = globalMap.get("fhirService");
    logger.info("fhirService.orchestraionengin"+fhirService.getEngine());
    logger.info("fhirService.orchestraionengin.appConfig"+fhirService.getEngine().getAppConfig());
    logger.info("fhirService.appConfig"+fhirService.getAppConfig());
    var convertMapToJson = globalMap.get("convertMapToJson");

    if (!fhirService || !convertMapToJson) {
        logger.error("Missing fhirService or convertMapToJson in globalMap.");
        return;
    }

    var requestParameters = channelMap.get("requestParameters");
    var headerParameters = channelMap.get("headerParameters");
    var responseParameters = new Packages.java.util.HashMap();
    var bundleJson = JSON.parse(connectorMessage.getRawData());
    var validationResults = fhirService.processBundle(
        connectorMessage.getRawData(), 
        requestParameters, 
        headerParameters, 
        responseParameters
    );
    return convertMapToJson(validationResults);
});

globalMap.put("getRequestParameters", getRequestParameters);
globalMap.put("getHeaderParameters", getHeaderParameters);