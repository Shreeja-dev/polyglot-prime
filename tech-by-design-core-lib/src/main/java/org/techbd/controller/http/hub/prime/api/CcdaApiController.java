package org.techbd.controller.http.hub.prime.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.techbd.service.fhir.CcdaProcessor;

//import io.swagger.v3.oas.annotations.Operation;
//import io.swagger.v3.oas.annotations.Parameter;
//import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nonnull;
import java.nio.charset.StandardCharsets;

@RestController
//@Tag(name = "Tech by Design Hub CCDA Endpoints", description = "Tech by Design Hub CCDA Endpoints")
public class CcdaApiController {
    private static final Logger LOG = LoggerFactory.getLogger(CcdaApiController.class);
    private final CcdaProcessor processor;
    
    public CcdaApiController(CcdaProcessor processor) {
        this.processor = processor;
    }
    @PostMapping(value = "/ccda/Bundle/$validate/", 
            consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
@ResponseBody
public String handleValidateRequest(
        @RequestPart(value = "file", required = true) MultipartFile file) {
    try {
        if (file == null || file.isEmpty()) {
            LOG.error("No file content provided");
            return processor.createErrorResponse("No CCDA content provided", "error");
        }

        // Read the file content
        String ccdaXml = new String(file.getBytes(), StandardCharsets.UTF_8);
        
        // Validate XML structure
        // if (!ccdaXml.contains("<?xml") && !ccdaXml.contains("<ClinicalDocument")) {
        //     LOG.error("Invalid XML content: {}", ccdaXml.substring(0, Math.min(100, ccdaXml.length())));
        //     return processor.createErrorResponse("Invalid XML content - must contain ClinicalDocument", "error");
        // }

        LOG.info("Processing CCDA validation request, content length: {}", ccdaXml.length());
        return processor.validateCcda(ccdaXml);
        
    } catch (Exception e) {
        LOG.error("Validation error: {}", e.getMessage(), e,e.getStackTrace());
        e.getStackTrace();
        return processor.createErrorResponse("Validation error: " + e.getMessage(), "error");
    }
}
//     @PostMapping(value = "/ccda/Bundle/$validate/", 
//     //consumes = {MediaType.TEXT_XML_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE})      
//     consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
//    // @Operation(summary = "Validate CCDA document", description = "Endpoint to validate a CCDA XML document")
//     @ResponseBody
//     public String handleValidateRequest(
//            // @Parameter(description = "CCDA XML document to validate", required = true)
//             @RequestPart(value = "file", required = false) MultipartFile file)
//             //,
//            // @RequestBody(required = false) String directXml)
//              {
//         try {
//             if (file == null) {
//                 return processor.createErrorResponse("No CCDA content provided", "error");
//             }
//             String ccdaXml = new String(file.getBytes(), StandardCharsets.UTF_8);
            
//             return processor.validateCcda(ccdaXml);
//         } catch (Exception e) {
//             LOG.error("Validation error", e);
//             return processor.createErrorResponse("Validation error: " + e.getMessage(), "error");
//         }
//     }
    
    @PostMapping(value = "/ccda/Bundle", 
                consumes = {MediaType.TEXT_XML_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE})
   // @Operation(summary = "Convert CCDA to FHIR Bundle", description = "Endpoint to transform CCDA XML to FHIR Bundle")
    @ResponseBody
    public String handleBundleRequest(
            //@Parameter(description = "CCDA XML document to transform", required = true)
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestBody(required = false) String directXml,
            //@Parameter(description = "Base FHIR URL for the generated resources", required = true)
            @RequestHeader(value = "X-TechBD-Base-FHIR-URL") String baseFhirUrl) {
        try {
            String ccdaXml;
            if (file != null) {
                ccdaXml = new String(file.getBytes(), StandardCharsets.UTF_8);
            } else if (directXml != null) {
                ccdaXml = directXml;
            } else {
                return processor.createErrorResponse("No CCDA content provided", "error");
            }
            return processor.transformCcdaToFhirBundle(ccdaXml, baseFhirUrl);
        } catch (Exception e) {
            LOG.error("Transformation error", e);
            return processor.createErrorResponse("Transformation error: " + e.getMessage(), "error");
        }
    }
}


// package org.techbd.controller.http.hub.prime.api;

// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;
// import org.springframework.http.MediaType;
// import org.springframework.web.bind.annotation.*;
// import org.springframework.web.multipart.MultipartFile;
// import org.techbd.service.fhir.CcdaProcessor;

// //import io.swagger.v3.oas.annotations.Operation;
// //import io.swagger.v3.oas.annotations.Parameter;
// //import io.swagger.v3.oas.annotations.tags.Tag;
// import jakarta.annotation.Nonnull;
// import java.nio.charset.StandardCharsets;

// @RestController
// //@Tag(name = "Tech by Design Hub CCDA Endpoints", description = "Tech by Design Hub CCDA Endpoints")
// public class CcdaApiController {
//     private static final Logger LOG = LoggerFactory.getLogger(CcdaApiController.class);
//     private final CcdaProcessor processor;
    
//     public CcdaApiController(CcdaProcessor processor) {
//         this.processor = processor;
//     }
    
//     @PostMapping(value = "/ccda/Bundle/$validate/", 
//                // consumes = {MediaType.TEXT_XML_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE})
//                consumes = { MediaType.MULTIPART_FORM_DATA_VALUE})
//                 // @Operation(summary = "Validate CCDA document", description = "Endpoint to validate a CCDA XML document")
//     @ResponseBody
//     public String handleValidateRequest(
//            // @Parameter(description = "CCDA XML document to validate", required = true)
//             @RequestPart(value = "file", required = false) MultipartFile file)
//             //,
//            // @RequestBody(required = false) String directXml)
//              {
//          try {
//             if (file == null) {
//                 return processor.createErrorResponse("No CCDA content provided", "error");
//             }
//             String ccdaXml = new String(file.getBytes(), StandardCharsets.UTF_8);
//             return processor.validateCcda(ccdaXml);
//         } catch (Exception e) {
//             LOG.error("Validation error", e);
//             return processor.createErrorResponse("Validation error: " + e.getMessage(), "error");
//         }
//     }
// //  //NEW*****************   
// // @PostMapping(value = "/ccda/Bundle/$validate/multipart", 
// // consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
// // @ResponseBody
// // public String handleValidateMultipartRequest(
// // @RequestPart(value = "file") MultipartFile file) {
// // try {
// // String ccdaXml = new String(file.getBytes(), StandardCharsets.UTF_8);
// // return processor.validateCcda(ccdaXml);
// // } catch (Exception e) {
// // LOG.error("Validation error", e);
// // return processor.createErrorResponse("Validation error: " + e.getMessage(), "error");
// // }
// // }
// // //***NEW */
// // @PostMapping(value = "/ccda/Bundle/$validate/xml", 
// // consumes = MediaType.TEXT_XML_VALUE)
// // @ResponseBody
// // public String handleValidateXmlRequest(
// // @RequestBody String directXml) {
// // try {
// // return processor.validateCcda(directXml);
// // } catch (Exception e) {
// // LOG.error("Validation error", e);
// // return processor.createErrorResponse("Validation error: " + e.getMessage(), "error");
// // }
// // }


// @PostMapping(value = "/ccda/Bundle", 
//                 consumes = {MediaType.TEXT_XML_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE})
//    // @Operation(summary = "Convert CCDA to FHIR Bundle", description = "Endpoint to transform CCDA XML to FHIR Bundle")
//     @ResponseBody
//     public String handleBundleRequest(
//             //@Parameter(description = "CCDA XML document to transform", required = true)
//             @RequestPart(value = "file", required = false) MultipartFile file,
//             @RequestBody(required = false) String directXml,
//             //@Parameter(description = "Base FHIR URL for the generated resources", required = true)
//             @RequestHeader(value = "X-TechBD-Base-FHIR-URL") String baseFhirUrl) {
//         try {
//             String ccdaXml;
//             if (file != null) {
//                 ccdaXml = new String(file.getBytes(), StandardCharsets.UTF_8);
//             } else if (directXml != null) {
//                 ccdaXml = directXml;
//             } else {
//                 return processor.createErrorResponse("No CCDA content provided", "error");
//             }
//             return processor.transformCcdaToFhirBundle(ccdaXml, baseFhirUrl);
//         } catch (Exception e) {
//             LOG.error("Transformation error", e);
//             return processor.createErrorResponse("Transformation error: " + e.getMessage(), "error");
//         }
//     }
// }

// package org.techbd.controller.http.hub.prime.api;

// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;
// import org.springframework.http.MediaType;
// import org.springframework.web.bind.annotation.PostMapping;
// import org.springframework.web.bind.annotation.RequestBody;
// import org.springframework.web.bind.annotation.RequestHeader;
// import org.springframework.web.bind.annotation.ResponseBody;
// import org.springframework.web.bind.annotation.RestController;
// //import org.techbd.service.CcdaProcessor;
// import org.techbd.service.fhir.CcdaProcessor;

// import io.swagger.v3.oas.annotations.Operation;
// import io.swagger.v3.oas.annotations.Parameter;
// import io.swagger.v3.oas.annotations.tags.Tag;
// import jakarta.annotation.Nonnull;

// @RestController
// //@Tag(name = "Tech by Design Hub CCDA Endpoints", description = "Tech by Design Hub CCDA Endpoints")
// public class CcdaApiController {
//     private static final Logger LOG = LoggerFactory.getLogger(CcdaApiController.class);
//     private final CcdaProcessor processor;
    
//     public CcdaApiController(CcdaProcessor processor) {
//         this.processor = processor;
//     }
    
//     @PostMapping(value = "/ccda/Bundle/$validate", consumes = MediaType.TEXT_XML_VALUE)
//     @Operation(summary = "Validate CCDA document", description = "Endpoint to validate a CCDA XML document")
//     @ResponseBody
//     public String handleValidateRequest(
//         @Parameter(description = "CCDA XML document to validate", required = true) 
//         @RequestBody @Nonnull String ccdaXml) {
//         try {
//             return processor.validateCcda(ccdaXml);
//         } catch (Exception e) {
//             LOG.error("Validation error", e);
//             return processor.createErrorResponse("Validation error: " + e.getMessage(), "error");
//         }
//     }
    
//     @PostMapping(value = "/ccda/Bundle", consumes = MediaType.TEXT_XML_VALUE)
//     @Operation(summary = "Convert CCDA to FHIR Bundle", description = "Endpoint to transform CCDA XML to FHIR Bundle")
//     @ResponseBody
//     public String handleBundleRequest(
//         @Parameter(description = "CCDA XML document to transform", required = true)
//         @RequestBody @Nonnull String ccdaXml,
//         @Parameter(description = "Base FHIR URL for the generated resources", required = true)
//         @RequestHeader(value = "X-TechBD-Base-FHIR-URL") String baseFhirUrl) {
//         try {
//             return processor.transformCcdaToFhirBundle(ccdaXml, baseFhirUrl);
//         } catch (Exception e) {
//             LOG.error("Transformation error", e);
//             return processor.createErrorResponse("Transformation error: " + e.getMessage(), "error");
//         }
//     }
// }


// // // package org.techbd.controller.http.hub.prime.api;

// // // //

// // //  /**
// // //      * Example API controller to handle HTTP requests
// // //      */
// // //     public class CcdaApiController {
// // //         private final CcdaProcessor processor;
        
// // //         public CcdaApiController(CcdaProcessor processor) {
// // //             this.processor = processor;
// // //         }
        
// // //         /**
// // //          * Handle /ccda/Bundle/$validate endpoint
// // //          */
// // //         public String handleValidateRequest(String ccdaXml) {
// // //             try {
// // //                 return processor.validateCcda(ccdaXml);
// // //             } catch (Exception e) {
// // //                 return processor.createErrorResponse("Validation error: " + e.getMessage(), "error");
// // //             }
// // //         }
        
// // //         /**
// // //          * Handle /ccda/Bundle endpoint
// // //          */
// // //         public String handleBundleRequest(String ccdaXml, String baseFhirUrl) {
// // //             try {
// // //                 return processor.transformCcdaToFhirBundle(ccdaXml, baseFhirUrl);
// // //             } catch (Exception e) {
// // //                 return processor.createErrorResponse("Transformation error is: " + e.getMessage(), "error");
// // //             }
// // //         }
// // //     }
