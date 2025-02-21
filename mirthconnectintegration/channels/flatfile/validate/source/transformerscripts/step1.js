var validateCsv = globalMap.get("validateCsv");
var tenantId = $('headers').getHeader('X-TechBD-Tenant-ID');
var zipFileInteractionId = channelMap.get("interactionId");
validateCsv(channelName, connectorMessage, channelMap,tenantId,zipFileInteractionId);
responseMap.put("finalResponse", channelMap.get("csvValidationResults"));