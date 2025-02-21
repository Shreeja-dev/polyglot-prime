var validateCsv = globalMap.get("validateCsv");
var tenantId = $('headers').getHeader('X-TechBD-Tenant-ID');
var zipFileInteractionId = channelMap.get("interactionId");
var validationResponse = validateCsv(channelName, connectorMessage, channelMap,tenantId,zipFileInteractionId);
responseMap.put("finalResponse", validationResponse);