// This script executes once for each deploy or redeploy task
// You only have access to the globalMap here to persist data

// Function to load AppConfig dynamically from environment variables
var File = Packages.java.io.File;
var FileOutputStream = Packages.java.io.FileOutputStream;
var FileInputStream = Packages.java.io.FileInputStream;
var ZipFile = Packages.java.util.zip.ZipFile;
var ZipEntry = Packages.java.util.zip.ZipEntry;
var BufferedOutputStream = Packages.java.io.BufferedOutputStream;
var ObjectMapper = Packages.com.fasterxml.jackson.databind.ObjectMapper;
var JavaTimeModule = Packages.com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
var mapper = new ObjectMapper();
mapper.registerModule(new JavaTimeModule());

function convertMapToJson(map) {
    return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(map);
}
function extractZipFile(zipFilePath, outputFolderPath,zipFileInteractionId) {
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
function copyFileToFolder(sourcePath, destinationFolderPath,zipFileInteractionId) {
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


function getCsvFilesFromDirectory(folderPath,zipFileInteractionId) {
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
function getUploadedFileName(connectorMessage,zipFileInteractionId) {
    var rawData = connectorMessage.getRawData();
    var filenameMatch = rawData.match(/filename="([^"]+)"/);
    var uploadedFileName = "unknown.zip";
    if (filenameMatch && filenameMatch[1]) {
        uploadedFileName = filenameMatch[1];
    }
    return uploadedFileName;
}

function getUploadedFileContent(connectorMessage,zipFileInteractionId) {
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

function getRequestParameters(zipFileInteractionId) {
    var requestParameters = new Packages.java.util.HashMap();
    var requestUri = "/flatfile/csv/Bundle/$validate"; //TODO - READ FROM CHANNEL MAP /GLOBAL MAP /REQUEST HEADERS
    var origin = "HTTP"; //TODO - READ FROM CHANNEL MAP /GLOBAL MAP /REQUEST HEADERS

    // Directly reference constants using the fully qualified name
    requestParameters.put(Packages.org.techbd.util.Constants.REQUEST_URI, requestUri);
    requestParameters.put(Packages.org.techbd.util.Constants.INTERACTION_ID, zipFileInteractionId);
    requestParameters.put(Packages.org.techbd.util.Constants.ORIGIN, origin);

    return requestParameters;
}


function getHeaderParameters(tenantId) {
    var headerParameters = new Packages.java.util.HashMap();
    var userAgent = "testuseragent"; //TODO - READ FROM CHANNEL MAP /GLOBAL MAP /REQUEST HEADERS
    headerParameters.put(Packages.org.techbd.util.Constants.USER_AGENT, userAgent);
    headerParameters.put(Packages.org.techbd.util.Constants.TENANT_ID, tenantId);
    return headerParameters;
}
function extractFileContent(zipFileInteractionId, connectorMessage) {
    logger.debug("extractFileContent BEGIN for interactionId " + zipFileInteractionId);
    try {
        var rawPayload = connectorMessage.getRawData();
        var boundary = rawPayload.split("\r\n")[0]; // Get the boundary string from the first line

        var parts = rawPayload.split(boundary);

        if (parts.length >= 2) {
            var partContent = parts[1].trim();
            var headerBodySeparator = "\r\n\r\n";
            var headerEndIndex = partContent.indexOf(headerBodySeparator);

            if (headerEndIndex !== -1) {
                var binaryData = partContent.substring(headerEndIndex + headerBodySeparator.length);

                if (binaryData.endsWith("--")) {
                    binaryData = binaryData.substring(0, binaryData.length - 2).trim();
                }

                // Convert binaryData string to Java byte array using Java packages
                var byteArray = binaryData.getBytes("UTF-8");

                logger.debug("extractFileContent END for interactionId " + zipFileInteractionId);
                return byteArray;
            } else {
                logger.error("Header separator not found in the payload." + zipFileInteractionId);
            }
        } else {
            logger.error("Payload does not contain the expected parts." + zipFileInteractionId);
        }
    } catch (e) {
        logger.error("Error in extractFileContent function: " + zipFileInteractionId + " error " + e);
    }
    return null;
}
function uploadFileToInboundFolder(uploadFolderPath,zipFileInteractionId,connectorMessage) {
	
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
function loadAppConfig() {
    var AppConfig = Packages.org.techbd.service.http.hub.prime.AppConfig;
    var CsvValidation = Packages.org.techbd.service.http.hub.prime.AppConfig.CsvValidation;
    var Validation = Packages.org.techbd.service.http.hub.prime.AppConfig.CsvValidation.Validation;
    var DefaultDataLakeApiAuthn = Packages.org.techbd.service.http.hub.prime.AppConfig.DefaultDataLakeApiAuthn;
    var MTlsResources = Packages.org.techbd.service.http.hub.prime.AppConfig.MTlsResources;
    var MTlsAwsSecrets = Packages.org.techbd.service.http.hub.prime.AppConfig.MTlsAwsSecrets;
    var PostStdinPayloadToNyecDataLakeExternal = Packages.org.techbd.service.http.hub.prime.AppConfig.PostStdinPayloadToNyecDataLakeExternal;

    var config = new AppConfig();

    // Fetch Environment Variables
    config.setVersion(java.lang.System.getenv("TECHBD_VERSION"));
    config.setFhirVersion(java.lang.System.getenv("TECHBD_FHIR_VERSION"));
    config.setIgVersion(java.lang.System.getenv("TECHBD_IG_VERSION"));
    config.setBaseFHIRURL(java.lang.System.getenv("TECHBD_BASE_FHIR_URL"));
    config.setDefaultDatalakeApiUrl(java.lang.System.getenv("TECHBD_DEFAULT_DATALAKE_API_URL"));
    config.setOperationOutcomeHelpUrl(java.lang.System.getenv("TECHBD_OPERATION_OUTCOME_HELP_URL"));

    // Structure Definitions URLs
    var structureDefinitionsUrls = new java.util.HashMap();
    var keys = ["BUNDLE", "PATIENT", "CONSENT", "ENCOUNTER", "ORGANIZATION", "OBSERVATION", "QUESTIONNAIRE", "PRACTITIONER", "QUESTIONNAIRERESPONSE", "OBSERVATION_SEXUAL_ORIENTATION"];
    keys.forEach(function(key) {
        var envVar = "TECHBD_STRUCTURE_DEFINITIONS_URLS_" + key;
        structureDefinitionsUrls.put(key, java.lang.System.getenv(envVar));
    });
    config.setStructureDefinitionsUrls(structureDefinitionsUrls);

    // CSV Validation
    config.setCsv(new CsvValidation(new Validation(
        java.lang.System.getenv("TECHBD_CSV_PYTHON_SCRIPT_PATH"),
        java.lang.System.getenv("TECHBD_CSV_PYTHON_EXECUTABLE"),
        java.lang.System.getenv("TECHBD_CSV_PACKAGE_PATH"),
        java.lang.System.getenv("TECHBD_CSV_OUTPUT_PATH"),
        java.lang.System.getenv("TECHBD_CSV_INBOUND_PATH"),
        java.lang.System.getenv("TECHBD_CSV_INGRESS_PATH")
    )));

    // Default Data Lake API Auth
    var timeoutValue = parseInt(java.lang.System.getenv("TECHBD_POST_STDIN_PAYLOAD_TO_NYEC_DATALAKE_EXTERNAL_TIMEOUT")) || 180;
    config.setDefaultDataLakeApiAuthn(new DefaultDataLakeApiAuthn(
        java.lang.System.getenv("TECHBD_MTLS_STRATEGY"),
        new MTlsAwsSecrets(
            java.lang.System.getenv("TECHBD_MTLS_KEY_SECRET_NAME"),
            java.lang.System.getenv("TECHBD_MTLS_CERT_SECRET_NAME")
        ),
        new PostStdinPayloadToNyecDataLakeExternal(
            java.lang.System.getenv("TECHBD_POST_STDIN_PAYLOAD_TO_NYEC_DATALAKE_EXTERNAL_CMD"),
            timeoutValue
        ),
        new MTlsResources(
            java.lang.System.getenv("TECHBD_MTLS_KEY_RESOURCE_NAME"),
            java.lang.System.getenv("TECHBD_MTLS_CERT_RESOURCE_NAME")
        )
    ));

    // IG Packages
    var igPackages = new java.util.HashMap();
    ["SHIN_NY", "US_CORE", "SDOH", "UV_SDC"].forEach(function(key) {
        igPackages.put(key, java.lang.System.getenv("TECHBD_IG_PACKAGES_FHIR_V4_" + key));
    });
    config.setIgPackages(igPackages);

    return config;
}

// Store AppConfig in globalMap
globalMap.put("appConfig", loadAppConfig());

// Log to confirm setup
logger.info("AppConfig has been loaded and stored in globalMap.");

globalMap.put("validateCsv", function validateCsv(channelName, connectorMessage, channelMap,tenantId,zipFileInteractionId) {
    var csvService = new Packages.org.techbd.service.CsvService();
    var appConfig = globalMap.get("appConfig");
    var inboundFolder = appConfig.getCsv().validation().inboundPath();    
    var ingressFolder = appConfig.getCsv().validation().ingessHomePath() + zipFileInteractionId + "/ingress";
    var pythonPackageFullPath = appConfig.getCsv().validation().packagePath();
    var pythonScriptFullPath = appConfig.getCsv().validation().pythonScriptPath();
    var uploadedFileFullPath = uploadFileToInboundFolder(inboundFolder, zipFileInteractionId,connectorMessage);
    extractZipFile(uploadedFileFullPath, ingressFolder,zipFileInteractionId);
    var csvFiles = getCsvFilesFromDirectory(ingressFolder,zipFileInteractionId);
    copyFileToFolder(pythonPackageFullPath, ingressFolder,zipFileInteractionId);
    copyFileToFolder(pythonScriptFullPath, ingressFolder,zipFileInteractionId);
    var requestParameters = getRequestParameters(zipFileInteractionId);
    var headerParameters = getHeaderParameters(tenantId);
    var originalFileName = getUploadedFileName(connectorMessage,zipFileInteractionId);
    var fileContent = extractFileContent(zipFileInteractionId, connectorMessage);
    csvService.setRequestParameters(requestParameters);
    csvService.setHeaderParameters(headerParameters);
    csvService.setAppConfig(appConfig);
    csvService.setCsvFiles(csvFiles);
    var validationResults = csvService.validateCsvFile(fileContent,originalFileName);
    channelMap.put("csvValidationResults",validationResults);
});

return;
