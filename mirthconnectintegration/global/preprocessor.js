// Modify the message variable below to pre process data
// This script applies across all channels
var logInfo = globalMap.get("logInfo");
var interactionId = java.util.UUID.randomUUID().toString();
var sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
channelMap.put("interactionId", interactionId);
channelMap.put("channelId",channelId);
channelMap.put("channelName",channelName);
logInfo("==== BEGIN of Message Processing received at channel ==== ",channelMap);
var startTime = new Date().getTime();
channelMap.put("startTime", startTime);
var headers = $('headers');
var getRequestParameters = globalMap.get("getRequestParameters");
var getHeaderParameters = globalMap.get("getHeaderParameters");
channelMap.put("requestParameters", getRequestParameters(interactionId,channelMap,sourceMap));
channelMap.put("headerParameters", getHeaderParameters(headers,channelMap,sourceMap));


return;
