logger.info("Message Recived in Channel"+channelName)
var File = Packages.java.io.File;
var FileOutputStream = Packages.java.io.FileOutputStream;
var FileInputStream = Packages.java.io.FileInputStream;
var ZipFile = Packages.java.util.zip.ZipFile;
var ZipEntry = Packages.java.util.zip.ZipEntry;
var BufferedOutputStream = Packages.java.io.BufferedOutputStream;
var ObjectMapper = Packages.com.fasterxml.jackson.databind.ObjectMapper;
var JavaTimeModule = Packages.com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
var mapper = new ObjectMapper();
var appConfig = globalMap.get("appConfig");
mapper.registerModule(new JavaTimeModule()); 

var tenantId = $('headers').getHeader('X-TechBD-Tenant-ID');
var fhirProfileUrlHeader = $('headers').getHeader("FHIR-STRUCT-DEFN-PROFILE-URI");
var zipFileInteractionId = channelMap.get("interactionId");
logger.info("Invoke validation - BEGIN for interaction id"+zipFileInteractionId);
var requestParameters = getRequestParameters();
var inboundFolder = appConfig.getCsv().validation().inboundPath();
var ingressFolder = appConfig.getCsv().validation().ingessHomePath()+zipFileInteractionId+"/ingress";
var pythonPackageFullPath = appConfig.getCsv().validation().packagePath();
var pythonScriptFullPath = appConfig.getCsv().validation().pythonScriptPath();
var uploadedFileFullPath = uploadFileToInboundFolder(inboundFolder);
extractZipFile(uploadedFileFullPath,ingressFolder);
var csvFiles = getCsvFilesFromDirectory(ingressFolder); //TODO - non csv files to be listed as not-processed
copyFileToFolder(pythonPackageFullPath,ingressFolder);
copyFileToFolder(pythonScriptFullPath,ingressFolder);

var file = createMultipartFile(); 
saveArchiveInteraction(zipFileInteractionId, file); //TODO - implement the saving
var validationResults = invokeValidation(zipFileInteractionId, file,csvFiles,tenantId,requestParameters);
var validationResultsJson = convertMapToJson(validationResults);
responseMap.put("finalResponse", validationResultsJson);
logger.info("Invoke validation - END for interactionId "+zipFileInteractionId);
return;

function convertMapToJson(map) {
    return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(map);
}

function saveArchiveInteraction(zipFileInteractionId, file){
	//TODO - CHECK AND IMPLEMENT  CsvService:saveArchiveInteraction
	
}
function copyFileToFolder(sourcePath, destinationFolderPath) {
    logger.debug("CopyFileToFolder BEGIN for interactionId "+zipFileInteractionId);
    var Files = Packages.java.nio.file.Files;
    var Paths = Packages.java.nio.file.Paths;
    var StandardCopyOption = Packages.java.nio.file.StandardCopyOption;

    try {
        var source = Paths.get(sourcePath);
        var destinationFolder = Paths.get(destinationFolderPath);

        // Ensure destination folder exists
        if (!Files.exists(destinationFolder)) {
            Files.createDirectories(destinationFolder); // Create the directory if it doesn't exist
        }

        // Extract filename from sourcePath
        var fileName = source.getFileName();
        var destinationPath = destinationFolder.resolve(fileName); // Append filename to folder path

        // Copy file to the folder
        Files.copy(source, destinationPath, StandardCopyOption.REPLACE_EXISTING);
	   logger.debug("CopyFileToFolder END for interactionId "+zipFileInteractionId);
        return "File copied successfully to: " + destinationPath.toString();
    } catch (e) {
    	   logger.error("Error while copying for interactionId "+zipFileInteractionId);
        return "Error copying file: " + e.message;
    }
}


function getCsvFilesFromDirectory(folderPath) {
    logger.debug("getCsvFilesFromDirectory BEGIN for interactionId "+zipFileInteractionId);
    var File = Packages.java.io.File;
    var Arrays = Packages.java.util.Arrays;
    var ArrayList = Packages.java.util.ArrayList;

    var folder = new File(folderPath);
    var fileList = new ArrayList();

    if (folder.exists() && folder.isDirectory()) {
        var files = folder.listFiles();
        if (files !== null) {
            for (var i = 0; i < files.length; i++) {
                if (files[i].isFile() && files[i].getName().toLowerCase().endsWith(".csv")) {
                    fileList.add(files[i].getAbsolutePath());
                }
            }
        }
    }
    logger.debug("getCsvFilesFromDirectory END for interactionId "+zipFileInteractionId);
    return fileList;
}
function invokeValidation(zipFileInteractionId, file, csvFiles, tenantId, requestParameters) {
    logger.debug("invokeValidation BEGIN for interactionId "+zipFileInteractionId);
    var engine = new Packages.org.techbd.orchestrate.csv.CsvOrchestrationEngine();
    var session = null;

    try {
        var appConfig = globalMap.get("appConfig");
        var vfsCoreService = new Packages.org.techbd.service.VfsCoreService();

        engine.setAppConfig(appConfig);
        engine.setVfsCoreService(vfsCoreService);
        engine.setRequestParamters(requestParameters);

        session = engine.session()
            .withMasterInteractionId(zipFileInteractionId)
            .withSessionId(java.util.UUID.randomUUID().toString())
            .withTenantId(tenantId)
            .withGenerateBundle(true)
            .withCsvFileList(csvFiles)
            .withFile(file)
            .build();

        engine.orchestrate(session);
        logger.debug("invokeValidation END for interactionId "+zipFileInteractionId);
        return session.getValidationResults();
    } finally {
        if (session !== null) {
            engine.clear(session);
        }
    }
}

function createMultipartFile() {
    logger.debug("createMultipartFile BEGIN for interactionId "+zipFileInteractionId);
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
    logger.debug("createMultipartFile END for interactionId "+zipFileInteractionId);
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

function uploadFileToInboundFolder(uploadFolderPath) {
	try {
	    logger.debug("uploadFileToInboundFolder BEGIN for interactionId "+zipFileInteractionId);
	    var uploadedFileName = getUploadedFileName(connectorMessage);
	    logger.info("uploadedFileName"+uploadedFileName);
	    var uploadedFileFullPath  = uploadFolderPath+uploadedFileName;
	    // Ensure the upload folder exists and grant write permission if it does not
	    var uploadFolder = new File(uploadFolderPath);
	    if (!uploadFolder.exists()) {
	        uploadFolder.mkdirs();
	    }
	    uploadFolder.setWritable(true);
	    // Step 1: Save the Uploaded File
	    var binaryContent = connectorMessage.getRawData().getBytes("ISO-8859-1");
	    if (!binaryContent || binaryContent.length === 0) {
	        logger.error("No valid ZIP file found in the uploaded data for interaction id "+zipFileInteractionId);
	        throw new Error("Failed to extract ZIP file from input data.");
	    }
	    logger.debug("Starting to save uploaded ZIP file for interaction Id "+zipFileInteractionId);
	    var uploadedFile = new File(uploadedFileFullPath);
	    var fileOutputStream = new FileOutputStream(uploadedFile);
	    fileOutputStream.write(binaryContent);
	    fileOutputStream.close();
	    logger.debug("uploadFileToInboundFolder END for interactionId"+ zipFileInteractionId+"Successfully saved uploaded file to: " + uploadedFileFullPath);
		return uploadedFileFullPath;
	} catch (e) {
	    logger.error("Error during ZIP file processing for interactionId"+zipFileInteractionId + e.message);
	    throw e;
	}
}

function extractFileContent() {
	logger.debug("extractFileContent BEGIN for interactionId "+zipFileInteractionId);
	try {
		var rawPayload = connectorMessage.getRawData();
		var boundary = rawPayload.split("\r\n")[0]; // Get the boundary string from the first line

		// Split the payload using the boundary string. This should create an array like:
		// [ "", "\r\nContent-Disposition: ...\r\n\r\nPK BINARYDATA\r\n", "--" ]
		var parts = rawPayload.split(boundary);

		// Check that we have at least one part with content
		if (parts.length >= 2) {
		    // parts[1] contains the headers and the binary content. Trim any extra whitespace.
		    var partContent = parts[1].trim();

		    // The headers end at the first blank line, i.e. after "\r\n\r\n"
		    var headerBodySeparator = "\r\n\r\n";
		    var headerEndIndex = partContent.indexOf(headerBodySeparator);

		    if (headerEndIndex !== -1) {
		        // The binary data starts after the headers and the blank line.
		        var binaryData = partContent.substring(headerEndIndex + headerBodySeparator.length);

		        // Sometimes there might be an extra trailing boundary marker (e.g. ending with "--")
		        // Remove it if present.
		        if (binaryData.endsWith("--")) {
		            binaryData = binaryData.substring(0, binaryData.length - 2).trim();
		        }

		        // At this point, binaryData contains only the binary data.
		       logger.debug("extractFileContent END for interactionId "+zipFileInteractionId);
		    } else {
		        logger.error("Header separator not found in the payload."+zipFileInteractionId);
		    }
		} else {
		    logger.error("Payload does not contain the expected parts."+zipFileInteractionId);
		}
		return binaryData;

	} catch (e) {
		// Log any errors that occur during execution.
		logger.error("Error in sendFlatfileCsv function: "+zipFileInteractionId+" error " + e);
	}
}

function extractZipFile(zipFilePath, outputFolderPath) {
    logger.debug("extractZipFile BEGIN for interactionId "+zipFileInteractionId);
    var zipFile = new File(zipFilePath);
    var outputFolder = new File(outputFolderPath);

    // Ensure output folder exists
    if (!outputFolder.exists()) {
        logger.info("Output folder does not exist. Creating it for interactionId "+zipFileInteractionId);
        outputFolder.mkdirs();
        logger.info("Output folder created for interactionId:  "+zipFileInteractionId +" Output File Path =" + outputFolderPath);
    }
    outputFolder.setWritable(true);
    if (!zipFile.exists() || zipFile.length() === 0) {
        logger.error("The ZIP file does not exist or is empty for interactionId : "+zipFileInteractionId);
        throw new Error("ZIP file is missing or empty.");
    }
    logger.info("Size of the uploaded file: " + zipFile.length() + " bytes. for interactionId : "+zipFileInteractionId);
    var extractionDone = false;

    try {
        // Use ZipFile for handling extraction
        var javaZipFile = new ZipFile(zipFile);
        var entries = javaZipFile.entries();

        // Check if entries are present
        var entryCount = 0;
        while (entries.hasMoreElements()) {
            entries.nextElement();
            entryCount++;
        }

        if (entryCount === 0) {
            javaZipFile.close();
            logger.error("ZIP file contains no entries for interactionId :"+zipFileInteractionId);
            throw new Error("ZIP file contains no entries.");
        }

        // Reset the entries enumeration to start extraction
        entries = javaZipFile.entries();
        logger.info("Starting extraction process for interactionId :"+zipFileInteractionId);

        // Loop through entries and extract them
        while (entries.hasMoreElements()) {
            var entry = entries.nextElement();
            // Handle directories
            if (entry.isDirectory()) {
                var newDir = new File(outputFolderPath, entry.getName());
                if (!newDir.exists()) {
                    newDir.mkdirs();
                }
            } else {
                // Handle files
                var newFile = new File(outputFolderPath, entry.getName());
                var parentDir = newFile.getParentFile();
                if (!parentDir.exists()) {
                    parentDir.mkdirs();
                }

                // Extract file content
                var inputStream = javaZipFile.getInputStream(entry);
                var buffer = new java.lang.reflect.Array.newInstance(java.lang.Byte.TYPE, 4096);
                var bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(newFile));

                var bytesRead;
                while ((bytesRead = inputStream.read(buffer)) !== -1) {
                    bufferedOutputStream.write(buffer, 0, bytesRead);
                }

                bufferedOutputStream.close();
                inputStream.close();
            }
        }

        // Mark extraction as complete
        extractionDone = true;
        javaZipFile.close();
        logger.info("extractZipFile END - All ZIP entries have been extracted successfully for interaction Id"+zipFileInteractionId);
    } catch (e) {
        // Log only actual errors and not expected scenarios (like no entries in the ZIP)
        if (e.message !== "ZIP file contains no entries.") {
            logger.error("Error during ZIP file extraction: for interactionId : "+zipFileInteractionId + " Message is " + e.message);
        }
        throw e;
    }

    // Prevent second logging after extraction
    if (!extractionDone) {
        logger.error("Extraction failed or no entries found for interactionId : "+zipFileInteractionId);
    }
}