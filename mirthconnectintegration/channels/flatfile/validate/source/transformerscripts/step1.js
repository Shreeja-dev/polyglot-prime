var validateCsv = globalMap.get("validateCsv");
var tenantId = $('headers').getHeader('X-TechBD-Tenant-ID');
var zipFileInteractionId = channelMap.get("interactionId");
var ObjectMapper = Packages.com.fasterxml.jackson.databind.ObjectMapper;
var JavaTimeModule = Packages.com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
var mapper = new ObjectMapper();
mapper.registerModule(new JavaTimeModule());
validateCsv(channelName, connectorMessage, channelMap,tenantId,zipFileInteractionId);
responseMap.put("finalResponse", convertMapToJson(channelMap.get("csvValidationResults")));

function convertMapToJson(map) {
    return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(map);
}