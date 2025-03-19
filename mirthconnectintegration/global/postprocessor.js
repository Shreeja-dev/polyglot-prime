// This script executes once after a message has been processed
// This script applies across all channels
// Responses returned from here will be stored as "Postprocessor" in the response map
// You have access to "response", if returned from the channel postprocessor
// Log message processing completion
var logInfo = globalMap.get("logInfo");
var startTime = channelMap.get("startTime");
if (startTime != null) {
    var endTime = new Date().getTime();
    var duration = endTime - startTime;
    logInfo("==== END of Message Processing received at channel. Duration: " + duration + " ms ====", channelMap);
} else {
    logInfo("==== END of Message Processing received at channel ====", channelMap);
}
return;