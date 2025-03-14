// Modify the message variable below to pre process data
// This script applies across all channels
logger.info("==== BEGIN of Message Processing received at channel ===="+interactionId);
var interactionId = java.util.UUID.randomUUID().toString();
var getRequestParameters = globalMap.get("getRequestParameters");
var getHeaderParameters = globalMap.get("getHeaderParameters");
channelMap.put("interactionId", interactionId);
channelMap.put("requestParameters", getRequestParameters(interactionId));
channelMap.put("headerParameters", getHeaderParameters());

return;
