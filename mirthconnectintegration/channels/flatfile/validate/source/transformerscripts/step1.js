logger.info("Fetching AppConfig from Global Map");
var tenantId = $('headers').getHeader('X-TechBD-Tenant-ID');
var fhirProfileUrlHeader = $('headers').getHeader("FHIR-STRUCT-DEFN-PROFILE-URI");
var zipFileInteractionId = java.util.UUID.randomUUID().toString();
var requestParameters = getRequestParameters();
uploadFileToInboundFolder();
logger.info("Invoke validation - BEGIN");
var file = createMultipartFile();
logger.info("multi part file created " + file);
saveArchiveInteraction(zipFileInteractionId, file); //TODO - implement the saving
var validationResults = invokeValidation(zipFileInteractionId, file,tenantId,requestParameters);
logger.info("Invoke validation - END");
responseMap.put("finalResponse",validationResults);
return;

function saveArchiveInteraction(zipFileInteractionId, file){
	//TODO - CHECK AND IMPLEMENT  CsvService:saveArchiveInteraction
	
}
function invokeValidation(zipFileInteractionId, file,tenantId,requestParameters){
	
	logger.info("invokeValidation " + tenantId);
	var engine = new Packages.org.techbd.orchestrate.csv.CsvOrchestrationEngine();
	var appConfig = globalMap.get("appConfig");
	var vfsCoreService = new Packages.org.techbd.service.VfsCoreService();
	engine.setAppConfig(appConfig);	
	logger.info("vfscoreservice"+vfsCoreService);
	engine.setVfsCoreService(vfsCoreService);
	engine.setRequestParamters(requestParameters);
     var session = engine.session()
         .withMasterInteractionId(zipFileInteractionId)
         .withSessionId(java.util.UUID.randomUUID().toString())
         .withTenantId(tenantId)
         .withGenerateBundle(true)
         .withFile(file)
         //.withRequest(request) 
         .build();
     logger.info("session created ");    
     engine.orchestrate(session);
       logger.info("after orchestration ");    
     return session.getValidationResults();

}
function createMultipartFile() {
    logger.info("createMultipartFile -BEGIN ");
    var fileContent  = getUploadedFileContent(connectorMessage);
    var fileName = getUploadedFileName(connectorMessage);

    if (!fileContent) {
        logger.error("File content is empty or undefined!");
        return null;
    }

    var MultipartFile = function(fileContent, fileName) {
        this.fileContent = fileContent;
        this.fileName = fileName;

        this.getName = function() {
            return fileName;
        };

        this.getOriginalFilename = function() {
            return fileName;
        };

        this.getSize = function() {
            return fileContent.length;
        };

        this.getBytes = function() {
            return fileContent;
        };

        this.getInputStream = function() {
            var ByteArrayInputStream = Packages.java.io.ByteArrayInputStream;
            return new ByteArrayInputStream(fileContent);
        };

        this.transferTo = function(destinationFile) {
            var FileOutputStream = Packages.java.io.FileOutputStream;
            var outputStream = new FileOutputStream(destinationFile);
            outputStream.write(fileContent);
            outputStream.close();
        };
    };
    var file = new MultipartFile(fileContent, fileName);
    logger.info("MultipartFileCreated successfully "+file);
    return file;
}

function getUploadedFileName(connectorMessage) {
    var rawData = connectorMessage.getRawData();
    var filenameMatch = rawData.match(/filename="([^"]+)"/);
    var uploadedFileName = "unknown.zip";
    if (filenameMatch && filenameMatch[1]) {
        uploadedFileName = filenameMatch[1];
    }
    return uploadedFileName;
}

function getUploadedFileContent(connectorMessage) {
    try {
        var rawData = connectorMessage.getRawData();
        if (rawData) {
            return rawData.getBytes("ISO-8859-1");
        } else {
            logger.error("Raw data is undefined or null.");
            return null;
        }
    } catch (e) {
        logger.error("Error extracting file content: " + e.message);
        return null;
    }
}
function getRequestParameters() {
    var requestParameters = new Packages.java.util.HashMap();
    var userAgent = "testuseragent"; //TODO - READ FROM CHANNEL MAP /GLOBAL MAP /REQUEST HEADERS
    var requestUri = "/flatfile/csv/Bundle/$validate"; //TODO - READ FROM CHANNEL MAP /GLOBAL MAP /REQUEST HEADERS
    requestParameters.put("User-Agent", userAgent);
    requestParameters.put("Request-Uri", requestUri);
    return requestParameters;
}


function uploadFileToInboundFolder() {
	var File = Packages.java.io.File;
	var FileOutputStream = Packages.java.io.FileOutputStream;
	var FileInputStream = Packages.java.io.FileInputStream;
	var ZipFile = Packages.java.util.zip.ZipFile;
	var ZipEntry = Packages.java.util.zip.ZipEntry;
	var BufferedOutputStream = Packages.java.io.BufferedOutputStream;
	var inboundFolder = "D:/techbyDesign/flatFile/inbound/"; // TODO-move to environment variable
	var ingressFolder = "D:/techbyDesign/flatFile/ingress/"; // TODO-move to environment variable'
	var uploadedFileContent  = getUploadedFileContent(connectorMessage);
	var uploadedFileName = getUploadedFileName(connectorMessage);
	var uploadedFileFullPath  = inboundFolder+uploadedFileName;

	try {	
    		var uploadFolder = new File(inboundFolder);
    		if (!uploadFolder.exists()) {
        		logger.info("Upload folder does not exist. Creating it...");
        		uploadFolder.mkdirs();
        		logger.info("Upload folder created: " + inboundFolder);
    		}
    		uploadFolder.setWritable(true);
    		logger.info("Write permission granted to the upload folder.");
    
    		if (!uploadedFileContent || uploadedFileContent.length === 0) {
        		logger.error("No valid ZIP file found in the uploaded data.");
        		throw new Error("Failed to extract ZIP file from input data.");
    		}
    		logger.info("Starting to save uploaded ZIP file...");
    		var uploadedFile = new File(uploadedFileFullPath);
    		var fileOutputStream = new FileOutputStream(uploadedFile);
    		fileOutputStream.write(uploadedFileContent);
    		fileOutputStream.close();
	} catch (e) {
    		logger.error("Error during ZIP file processing: " + e.message);
    		throw e;
	}
}