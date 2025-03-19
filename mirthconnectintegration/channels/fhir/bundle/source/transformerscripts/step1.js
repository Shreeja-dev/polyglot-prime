var logInfo = globalMap.get("logInfo");
var logError = globalMap.get("logError");
var logDebug = globalMap.get("logDebug");
var logError = globalMap.get("logError");
var processFHIRBundle = globalMap.get("processFHIRBundle");
if (processFHIRBundle) {
    var tenantId = $('headers').getHeader('X-TechBD-Tenant-ID');
    channelMap.put("requestUri","/Bundle");
    var validationResults = processFHIRBundle(tenantId, channelMap, connectorMessage, responseMap);
    responseMap.put("resultJSON",validationResults);
} else {
    logError("processFHIRBundle function not found in globalMap.",channelMap);
}