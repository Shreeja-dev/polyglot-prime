// TODO -Check and add validations for mandatory request and header parameters check
var processFHIRBundle = globalMap.get("processFHIRBundle");
if (processFHIRBundle) {
    var tenantId = $('headers').getHeader('X-TechBD-Tenant-ID');
    channelMap.put("requestUri","/Bundle");
    var validationResults = processFHIRBundle(tenantId, channelMap, connectorMessage, responseMap);
    responseMap.put("resultJSON",validationResults);
} else {
    logger.error("processFHIRBundle function not found in globalMap.");
}