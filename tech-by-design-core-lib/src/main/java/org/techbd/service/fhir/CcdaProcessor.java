package org.techbd.service.fhir;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Logger;

public class CcdaProcessor {
    private static final Logger logger = Logger.getLogger(CcdaProcessor.class.getName());
    
    private String xsdFilePath;
    private String cdaPhiFilterXsltPath;
    private String cdaPhiBundleXsltPath;
    private List<String> validFhirUrls;
    
    public CcdaProcessor(String xsdFilePath, String cdaPhiFilterXsltPath, String cdaPhiBundleXsltPath, List<String> validFhirUrls) {
        this.xsdFilePath = xsdFilePath;
        this.cdaPhiFilterXsltPath = cdaPhiFilterXsltPath;
        this.cdaPhiBundleXsltPath = cdaPhiBundleXsltPath;
        this.validFhirUrls = validFhirUrls;
    }
    
    /**
     * Validates CCDA XML against a schema
     * 
     * @param xmlContent The CCDA XML content to validate
     * @return JSON string containing validation results
     * @throws Exception If validation fails or processing errors occur
     */
    // public String validateCcda(String xmlContent) throws Exception {
    //     try {
    //         // Clean and prepare the XML content
    //         String cleanedXml = cleanAndPrepareXml(xmlContent);
            
    //         // Load the XSD schema
    //         File xsdFile = new File(xsdFilePath);
    //         StreamSource schemaSource = new StreamSource(xsdFile);
    //         Schema schema = SchemaFactory
    //             .newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
    //             .newSchema(schemaSource);
                
    //         // Set up the validator
    //         Validator validator = schema.newValidator();
            
    //         // Set up the input source for the XML
    //         StreamSource xmlSource = new StreamSource(new StringReader(cleanedXml));
            
    //         // Get vendor names for debugging (optional)
    //         getVendorNames(xmlSource);
            
    //         // Perform the validation
    //         validator.validate(xmlSource);
            
    //         // Return success response
    //         return createSuccessResponse();
    //     // } catch (Exception e) {
    //     //     String errorMsg;
    //     //     if (e instanceof SAXException) {
    //     //         errorMsg = "CCDA XML validation failed: " + e.toString();
    //     //     } else {
    //     //         errorMsg = "Unexpected error during validation: " + e.getMessage() +e.getStackTrace();
    //     //     }
    //     //     logger.severe(errorMsg);
    //     //     return createErrorResponse(errorMsg, "invalid");
    //     // }
    // //detailed error message with stack trace
    // } catch (Exception e) {
    //     String errorMsg;
    //     if (e instanceof SAXException) {
    //         errorMsg = "CCDA XML validation failed: " + e.toString();
    //     } else {
    //         // Create detailed error message with stack trace
    //         StringWriter sw = new StringWriter();
    //         PrintWriter pw = new PrintWriter(sw);
    //         e.printStackTrace(pw);
            
    //         errorMsg = String.format("Unexpected error during validation:\nMessage: %s\nCause: %s\nStack trace:\n%s",
    //             e.getMessage(),
    //             e.getCause() != null ? e.getCause().getMessage() : "Unknown",
    //             sw.toString()
    //         );
    //     }
    //     logger.severe(errorMsg);
    //     return createErrorResponse(errorMsg, "invalid");
    // }
    // }
    //*********************************** */
    // Update the validateCcda method
public String validateCcda(String xmlContent) throws Exception {
    try {
        // Input validation and logging
        if (xmlContent == null) {
            logger.severe("XML content is null");
            return createErrorResponse("XML content cannot be null", "invalid");
        }

        logger.info("Received XML content length: " + xmlContent.length());
        logger.info("XML content preview: " + xmlContent.substring(0, Math.min(100, xmlContent.length())));

        // Validate and clean XML
        String cleanedXml = cleanAndPrepareXml(xmlContent);
        logger.info("Cleaned XML length: " + cleanedXml.length());
        logger.info("Cleaned XML preview: " + cleanedXml.substring(0, Math.min(100, cleanedXml.length())));

        // Validate XSD path
        if (xsdFilePath == null || xsdFilePath.trim().isEmpty()) {
            logger.severe("XSD file path is null or empty");
            return createErrorResponse("XSD file path not configured", "configuration");
        }

        // Load and validate XSD file
        File xsdFile = new File(xsdFilePath);
        if (!xsdFile.exists()) {
            logger.severe("XSD file not found: " + xsdFilePath);
            return createErrorResponse("XSD file not found: " + xsdFilePath, "configuration");
        }

        // Configure schema validation with security settings
        SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        schemaFactory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        schemaFactory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

        Schema schema = schemaFactory.newSchema(new StreamSource(xsdFile));
        Validator validator = schema.newValidator();
        validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

        // Create XML source for validation
        StreamSource xmlSource = new StreamSource(new StringReader(cleanedXml));

        try {
            validator.validate(xmlSource);
            return createSuccessResponse();
        } catch (SAXException e) {
            String msg = String.format("XML Validation failed at line %d, column %d: %s",
                e instanceof SAXParseException ? ((SAXParseException) e).getLineNumber() : -1,
                e instanceof SAXParseException ? ((SAXParseException) e).getColumnNumber() : -1,
                e.getMessage()
            );
            logger.severe(msg);
            return createErrorResponse(msg, "invalid");
        }

    } catch (Exception e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        
        String msg = String.format("Validation error:%n" +
            "Type: %s%n" +
            "Message: %s%n" +
            "Cause: %s%n" +
            "Stack trace:%n%s",
            e.getClass().getName(),
            e.getMessage(),
            e.getCause() != null ? e.getCause().getMessage() : "Unknown",
            sw.toString()
        );
        logger.severe(msg);
        return createErrorResponse(msg, "error");
    }
}

    /**
     * Transforms CCDA XML to FHIR Bundle
     * 
     * @param xmlContent The CCDA XML content to transform
     * @param baseFhirUrl Optional base FHIR URL for resource references
     * @return JSON string containing the FHIR Bundle
     * @throws Exception If transformation fails or processing errors occur
     */
    public String transformCcdaToFhirBundle(String xmlContent, String baseFhirUrl) throws Exception {
        try {
            // Clean and prepare the XML content
            String cleanedXml = cleanAndPrepareXml(xmlContent);
            
            // Get vendor names for debugging (optional)
            getVendorNames(cleanedXml);
            
            // Step 1: Apply PHI filtering
            String phiFilteredXml = applyPhiFilter(cleanedXml);
            logger.info("PHI Filtered CCDA: " + phiFilteredXml);
            
            // Validate the provided base FHIR URL if specified
            if (baseFhirUrl != null && !baseFhirUrl.trim().isEmpty()) {
                if (!validFhirUrls.contains(baseFhirUrl)) {
                    throw new IllegalArgumentException("Bad Request: The provided base FHIR URL is invalid.");
                }
            }
            
            // Step 2: Transform to FHIR Bundle
            String fhirBundle = transformToFhirBundle(phiFilteredXml, baseFhirUrl);
            
            // Clean up the JSON (remove empty values)
            String cleanedJsonString = removeEmptyValues(fhirBundle);
            
            return cleanedJsonString;
        } catch (Exception e) {
            String errorMsg = "Error processing CCDA transformation: " + e.getMessage();
            logger.severe(errorMsg);
            
            if (e instanceof IllegalArgumentException) {
                // For invalid FHIR URL or other client errors
                throw e;
            }
            
            return createErrorResponse(errorMsg, "exception");
        }
    }
    
    /**
     * Cleans and prepares XML content for processing
     */
    // private String cleanAndPrepareXml(String xmlContent) {
    //     if (xmlContent == null || xmlContent.trim().isEmpty()) {
    //         throw new IllegalArgumentException("No XML data received in the request.");
    //     }
        
    //     // Extract XML content if it's embedded in a message
    //     int xmlStartIndex = xmlContent.indexOf("<?xml");
    //     if (xmlStartIndex >= 0) {
    //         xmlContent = xmlContent.substring(xmlStartIndex);
    //     }
        
    //     // Ensure the XML has a proper declaration
    //     if (!xmlContent.contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")) {
    //         int clinicalDocumentIndex = xmlContent.indexOf("<ClinicalDocument");
    //         if (clinicalDocumentIndex != -1) {
    //             xmlContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + 
    //                          xmlContent.substring(clinicalDocumentIndex);
    //         }
    //     }
        
    //     // Remove any trailing content after the last XML tag
    //     int lastClosingTagIndex = xmlContent.lastIndexOf('>');
    //     if (lastClosingTagIndex != xmlContent.length() - 1) {
    //         xmlContent = xmlContent.substring(0, lastClosingTagIndex + 1);
    //     }
        
    //     return xmlContent;
    // }
    
    // Update the cleanAndPrepareXml method
private String cleanAndPrepareXml(String xmlContent) {
    if (xmlContent == null || xmlContent.trim().isEmpty()) {
        throw new IllegalArgumentException("XML content cannot be null or empty");
    }

    // Log original content
    logger.info("Original XML length: " + xmlContent.length());
    logger.info("Original XML starts with: " + xmlContent.substring(0, Math.min(50, xmlContent.length())));

    // Normalize line endings
    xmlContent = xmlContent.replaceAll("\\r\\n", "\n").trim();

    // Find XML start
    int xmlStart = xmlContent.indexOf("<?xml");
    if (xmlStart == -1) {
        xmlStart = xmlContent.indexOf("<ClinicalDocument");
        // if (xmlStart == -1) {
        //     throw new IllegalArgumentException("No XML declaration or ClinicalDocument element found");
        // }
        xmlContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + xmlContent.substring(xmlStart);
    } else {
        xmlContent = xmlContent.substring(xmlStart);
    }

    // Find proper XML end
    int lastCloseTag = xmlContent.lastIndexOf(">");
    if (lastCloseTag != -1) {
        xmlContent = xmlContent.substring(0, lastCloseTag + 1);
    }

    // Log cleaned content
    logger.info("Cleaned XML length: " + xmlContent.length());
    logger.info("Cleaned XML starts with: " + xmlContent.substring(0, Math.min(50, xmlContent.length())));

    return xmlContent;
}
    
    /**
     * Apply PHI filtering XSLT to the CCDA document
     */
    private String applyPhiFilter(String sourceXml) throws Exception {
        try {
            // Load the XSLT template
            File xsltFile = new File(cdaPhiFilterXsltPath);
            FileInputStream xsltStream = new FileInputStream(xsltFile);
            
            // Create the transformer
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            StreamSource xsltSource = new StreamSource(xsltStream);
            Transformer transformer = transformerFactory.newTransformer(xsltSource);
            
            // Prepare input and output streams
            StringReader xmlInputStream = new StringReader(sourceXml);
            StringWriter xmlOutputStream = new StringWriter();
            StreamSource originalCcdFile = new StreamSource(xmlInputStream);
            StreamResult phiFilterOutput = new StreamResult(xmlOutputStream);
            
            // Perform the transformation
            transformer.transform(originalCcdFile, phiFilterOutput);
            
            return xmlOutputStream.toString();
        } catch (Exception e) {
            logger.severe("Error during PHI filtering: " + e);
            throw e;
        }
    }
    
    /**
     * Transform PHI-filtered CCDA to FHIR Bundle
     */
    private String transformToFhirBundle(String phiFilteredXml, String baseFhirUrl) throws Exception {
        try {
            // Load the XSLT template
            File xsltFile = new File(cdaPhiBundleXsltPath);
            FileInputStream xsltStream = new FileInputStream(xsltFile);
            
            // Create the transformer
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            StreamSource xsltSource = new StreamSource(xsltStream);
            Transformer transformer = transformerFactory.newTransformer(xsltSource);
            
            // Set the current timestamp parameter in ISO 8601 format
            String currentTimestamp = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SS'Z'").format(new Date());
            transformer.setParameter("currentTimestamp", currentTimestamp);
            
            // Set FHIR resource profile URLs
            setFhirResourceProfileUrls(transformer);
            
            // Set the base FHIR URL if provided
            if (baseFhirUrl != null && !baseFhirUrl.trim().isEmpty()) {
                transformer.setParameter("baseFhirUrl", baseFhirUrl);
            }
            
            // Prepare input and output streams
            StringReader xmlInputStream = new StringReader(phiFilteredXml);
            StringWriter xmlOutputStream = new StringWriter();
            StreamSource phiFilteredCcdFile = new StreamSource(xmlInputStream);
            StreamResult bundleOutput = new StreamResult(xmlOutputStream);
            
            // Perform the transformation
            transformer.transform(phiFilteredCcdFile, bundleOutput);
            
            return xmlOutputStream.toString();
        } catch (Exception e) {
            logger.severe("Error during FHIR bundle transformation: " + e);
            throw e;
        }
    }
    
    /**
     * Extract vendor names from the CCDA document for debugging purposes
     */
    private void getVendorNames(Object xmlInput) {
        // This method would extract vendor information if needed
        // Implementation would depend on specific requirements
        logger.info("Extracting vendor information from CCDA...");
    }
    
    /**
     * Set FHIR resource profile URLs in the transformer
     */
    private void setFhirResourceProfileUrls(Transformer transformer) {
        // Set the standard FHIR resource profile URLs as parameters for the XSLT
        transformer.setParameter("patientProfile", "http://hl7.org/fhir/us/core/StructureDefinition/us-core-patient");
        transformer.setParameter("practitionerProfile", "http://hl7.org/fhir/us/core/StructureDefinition/us-core-practitioner");
        transformer.setParameter("organizationProfile", "http://hl7.org/fhir/us/core/StructureDefinition/us-core-organization");
        transformer.setParameter("locationProfile", "http://hl7.org/fhir/us/core/StructureDefinition/us-core-location");
        // Add other profile URLs as needed
    }
    
    /**
     * Creates a success response for validation
     */
    private String createSuccessResponse() {
        return "{\n" +
               "  \"OperationOutcome\": {\n" +
               "    \"validationResults\": [\n" +
               "      {\n" +
               "        \"operationOutcome\": {\n" +
               "          \"resourceType\": \"OperationOutcome\",\n" +
               "          \"issue\": [\n" +
               "            {\n" +
               "              \"severity\": \"information\",\n" +
               "              \"code\": \"informational\",\n" +
               "              \"details\": {\n" +
               "                \"text\": \"CCDA XML is valid according to the XSD.\"\n" +
               "              }\n" +
               "            }\n" +
               "          ]\n" +
               "        }\n" +
               "      }\n" +
               "    ]\n" +
               "  }\n" +
               "}";
    }
    
    /**
     * Creates an error response with the specified message and code
     */
    public String createErrorResponse(String errorMessage, String code) {
        return "{\n" +
               "  \"OperationOutcome\": {\n" +
               "    \"validationResults\": [\n" +
               "      {\n" +
               "        \"operationOutcome\": {\n" +
               "          \"resourceType\": \"OperationOutcome\",\n" +
               "          \"issue\": [\n" +
               "            {\n" +
               "              \"severity\": \"error\",\n" +
               "              \"code\": \"" + code + "\",\n" +
               "              \"details\": {\n" +
               "                \"text\": \"" + errorMessage.replace("\"", "\\\"") + "\"\n" +
               "              }\n" +
               "            }\n" +
               "          ]\n" +
               "        }\n" +
               "      }\n" +
               "    ]\n" +
               "  }\n" +
               "}";
    }
    
    /**
     * Removes empty values from JSON
     */
        private String removeEmptyValues(String jsonString) {
            try {
                // This is a placeholder - in a full implementation, you would use a JSON
                // library like Jackson or Gson to parse the JSON, remove empty values,
                // and reserialize it
                
                // For example with Jackson:
                // ObjectMapper mapper = new ObjectMapper();
                // JsonNode jsonNode = mapper.readTree(jsonString);
                // // Remove empty values
                // return mapper.writeValueAsString(jsonNode);
                
                return jsonString;
            } catch (Exception e) {
                logger.warning("Error cleaning JSON: " + e.getMessage());
                return jsonString;
            }
        }
    }
