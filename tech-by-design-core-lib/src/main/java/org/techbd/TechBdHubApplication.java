package org.techbd;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.FileCopyUtils;
import org.techbd.service.fhir.CcdaProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@SpringBootApplication
public class TechBdHubApplication {
    private static final Logger logger = LoggerFactory.getLogger(TechBdHubApplication.class);

    public static void main(String[] args) {
        logger.info("Starting TechBD Hub Application...");
        SpringApplication.run(TechBdHubApplication.class, args);
    }

    @Bean
    public CcdaProcessor ccdaProcessor() throws Exception {
        // Create temp files from resources
        String xsdPath = copyResourceToTemp("schemas/CDA.xsd");
        String phiFilterPath = copyResourceToTemp("xslt/cda-phi-filter.xslt");
        String bundleXsltPath = copyResourceToTemp("xslt/cda-fhir-bundle.xslt");

        logger.info("Initializing CcdaProcessor with paths:");
        logger.info("XSD Path: {}", xsdPath);
        logger.info("PHI Filter Path: {}", phiFilterPath);
        logger.info("Bundle XSLT Path: {}", bundleXsltPath);

        return new CcdaProcessor(
            xsdPath,
            phiFilterPath,
            bundleXsltPath,
            Arrays.asList("https://synthetic.fhir.api.devl.techbd.org")
        );
    }

    private String copyResourceToTemp(String resourcePath) throws Exception {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        if (!resource.exists()) {
            throw new IllegalStateException("Resource not found: " + resourcePath);
        }

        // Create temp file with same extension
        String extension = resourcePath.substring(resourcePath.lastIndexOf('.'));
        File tempFile = File.createTempFile("ccda-", extension);
        tempFile.deleteOnExit();

        // Copy resource to temp file
        FileCopyUtils.copy(
            FileCopyUtils.copyToString(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)),
            new FileWriter(tempFile)
        );

        logger.info("Created temp file for {}: {}", resourcePath, tempFile.getAbsolutePath());
        return tempFile.getAbsolutePath();
    }
}