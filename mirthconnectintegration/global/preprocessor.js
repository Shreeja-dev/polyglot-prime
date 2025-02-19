// Modify the message variable below to pre process data
// This script applies across all channels
var loadAppConfig = globalMap.get("loadAppConfig");
var appConfig = loadAppConfig();

globalMap.put("appConfig",appConfig);
if (appConfig) {
    logger.info("AppConfig Version: " + appConfig.getVersion());
} else {
    logger.error("Failed to load AppConfig!");
}
return;