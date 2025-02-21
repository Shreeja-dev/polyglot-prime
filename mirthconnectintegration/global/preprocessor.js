// Modify the message variable below to pre process data
// This script applies across all channels
// Load AppConfig only if it is not already in globalMap
if (globalMap.get("appConfig") == null) {
    var loadAppConfig = globalMap.get("loadAppConfig");
    if (loadAppConfig) {
        var appConfig = loadAppConfig();
        globalMap.put("appConfig", appConfig);
        logger.info("AppConfig Version: " + appConfig.getVersion());
    } else {
        logger.error("Failed to load AppConfig!");
    }
}
var interactionId = java.util.UUID.randomUUID().toString();
channelMap.put("interactionId", interactionId);
// Log BEGIN of message processing
logger.info("==== BEGIN of Message Processing received at channel ===="+interactionId);
return;
