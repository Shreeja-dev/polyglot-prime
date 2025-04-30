// package org.techbd.service.mirth;

// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.stereotype.Service;
// import org.techbd.service.fhir.CcdaProcessor;

// @Service
// public class MirthCcdaService {
//     private static final Logger LOG = LoggerFactory.getLogger(MirthCcdaService.class);
    
//     private final CcdaProcessor processor;
//     private  MirthCcdaService instance;

// @Autowired
// public MirthCcdaService(CcdaProcessor processor) {
//     this.processor = processor;
//     //MirthCcdaService.instance = this; // Static assignment
// }

// // public static MirthCcdaService getInstance() {
// //     return instance;
// // }

// public String getHello() {
//     LOG.info("Hello from MirthCcdaService instance method!");
//     return "Hello from MirthCcdaService instance method!";
// }


// }

package org.techbd.service.mirth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.techbd.service.fhir.CcdaProcessor;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

//@Service
@Getter
@Setter
public class MirthCcdaService {
    private static final Logger LOG = LoggerFactory.getLogger(MirthCcdaService.class);
    private final CcdaProcessor processor;
    //private static MirthCcdaService instance;
    
    @Autowired
    public MirthCcdaService(CcdaProcessor processor) {
        this.processor = processor;
       // instance = this; // Store the instance for static access
        LOG.info("MirthCcdaService initialized");
    }
    
    public String getHello() {
        LOG.info("Hello from MirthCcdaService!");
        return "Hello from MirthCcdaService!";
    }
    // Instance methods
    public String handleValidateRequest(String ccdaXml) {
        try {
            LOG.info("Validating CCDA XML");
            return processor.validateCcda(ccdaXml);
        } catch (Exception e) {
            LOG.error("Validation error", e);
            return processor.createErrorResponse("Validation error: " + e.getMessage(), "error");
        }
    }
    
    public String handleBundleRequest(String ccdaXml, String baseFhirUrl) {
        try {
            LOG.info("Transforming CCDA to FHIR Bundle with base URL: {}", baseFhirUrl);
            return processor.transformCcdaToFhirBundle(ccdaXml, baseFhirUrl);
        } catch (Exception e) {
            LOG.error("Transformation error", e);
            return processor.createErrorResponse("Transformation error: " + e.getMessage(), "error");
        }
    }
}

// // package org.techbd.service.mirth;

// // import org.slf4j.Logger;
// // import org.slf4j.LoggerFactory;
// // import org.springframework.beans.factory.annotation.Autowired;
// // import org.springframework.stereotype.Service;
// // import org.techbd.service.fhir.CcdaProcessor;

// // @Service
// // public class MirthCcdaService {
// //     private static final Logger LOG = LoggerFactory.getLogger(MirthCcdaService.class);
// //     private final CcdaProcessor processor;
// //     //private static CcdaProcessor processor;
// //     private static MirthCcdaService instance;
// // @Autowired
// //     public MirthCcdaService(CcdaProcessor processor) {
// //         this.processor = processor;
// //        // MirthCcdaService.instance = this;
// //     }
// //     // public static MirthCcdaService getService() {
// //     //     return new MirthCcdaService(processor);
// //     // }
// //     // public static MirthCcdaService getInstance() {
// //     //     return instance;
// //     // }
// //     public String handleValidateRequest(String ccdaXml) {
// //         try {
// //             LOG.info("Validating CCDA XML");
// //             return processor.validateCcda(ccdaXml);
// //         } catch (Exception e) {
// //             LOG.error("Validation error", e);
// //             return processor.createErrorResponse("Validation error: " + e.getMessage(), "error");
// //         }
// //     }

// //     public String handleBundleRequest(String ccdaXml, String baseFhirUrl) {
// //         try {
// //             LOG.info("Transforming CCDA to FHIR Bundle with base URL: {}", baseFhirUrl);
// //             return processor.transformCcdaToFhirBundle(ccdaXml, baseFhirUrl);
// //         } catch (Exception e) {
// //             LOG.error("Transformation error", e);
// //             return processor.createErrorResponse("Transformation error: " + e.getMessage(), "error");
// //         }
// //     }
// // }