// Modify the message variable below to pre process data
var logInfo = globalMap.get("logInfo");
var logError = globalMap.get("logError");
var logDebug = globalMap.get("logDebug");
logInfo("Validate Header and Request Parameters BEGIN in channel preprocessor : ",channelMap);
var deleteSessionCookie = $('delete-session-cookie');
logger.info("deletesessioncookie"+deleteSessionCookie);
var tenantId = $('headers').getHeader('X-TechBD-Tenant-ID');
if (validate("X-TechBD-Tenant-ID", tenantId, "isRequired", responseMap, 400)) {
    return;
}
var correlationId =$('headers').getHeader("X-Correlation-ID");
if (correlationId && correlationId.trim() !== "") {
    if (validate("X-Correlation-ID", correlationId, "isValidUUID", responseMap, 400)) {
        return;
    }
}
//TODO - check and add other parameters /header validation for /Bundle
logInfo("Validate Header and Request Parameters END  in channel preprocessor : ",channelMap);