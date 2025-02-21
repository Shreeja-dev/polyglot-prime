// This script executes once after a message has been processed
// This script applies across all channels
// Responses returned from here will be stored as "Postprocessor" in the response map
// You have access to "response", if returned from the channel postprocessor
// Log message processing completion

logger.info("********** END MESSAGE PROCESSING of message received at channel ********** "+channelMap.get("interactionId"));
return;