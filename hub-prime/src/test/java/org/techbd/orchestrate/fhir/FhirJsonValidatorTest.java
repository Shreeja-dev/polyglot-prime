package org.techbd.orchestrate.fhir;


import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import org.hl7.fhir.r5.model.OperationOutcome;
import org.hl7.fhir.r5.model.OperationOutcome.OperationOutcomeIssueComponent;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.validation.ValidationEngine;
import org.hl7.fhir.validation.ValidationEngine.ValidationEngineBuilder;
import org.hl7.fhir.r5.elementmodel.Manager.FhirFormat;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class FhirJsonValidatorTest {

    private static final String TEST_FILE_PATH = "src/test/resources/org/techbd/ig-examples/1034668_Original.FHIR.Payload.json";
    private static final String OUTPUT_FILE_PATH = "src/test/resources/hl7-official-opcome.json";

  
    // @Test
    // public void validateFhirJsonFile() throws Exception {
    //     // Load the FHIR JSON content
    //     String input = Files.readString(Path.of(TEST_FILE_PATH));

    //     // Initialize the ValidationEngine
    //     ValidationEngine validator = new ValidationEngine.ValidationEngineBuilder().fromNothing();;
        

    //     // Validate the resource
    //     OperationOutcome messages = validator.validate(input, null);

    //     // Build JSON output
    //     StringBuilder jsonOutput = new StringBuilder();
    //     jsonOutput.append("[\n");
    //     for (int i = 0; i < messages.size(); i++) {
    //         ValidationMessage msg = messages.get(i);
    //         jsonOutput.append("  {\n");
    //         jsonOutput.append("    \"severity\": \"").append(msg.getLevel()).append("\",\n");
    //         jsonOutput.append("    \"location\": \"").append(msg.getLocation()).append("\",\n");
    //         jsonOutput.append("    \"message\": \"").append(msg.getMessage().replace("\"", "\\\"")).append("\"\n");
    //         jsonOutput.append("  }");
    //         if (i < messages.size() - 1) {
    //             jsonOutput.append(",");
    //         }
    //         jsonOutput.append("\n");
    //     }
    //     jsonOutput.append("]");

    //     // Write the output to a file
    //     Files.writeString(Path.of(OUTPUT_FILE_PATH), jsonOutput.toString());
    //     System.out.println("Validation result saved to: " + OUTPUT_FILE_PATH);
    // }

    
    @Test
    void testValidateFHIRResourceAgainstProfile() throws Exception {
 
        String profileUrl = "http://shinny.org/us/ny/hrsn/StructureDefinition/SHINNYBundleProfile";

        FhirContext fhirContext = FhirContext.forR4();
        ValidationEngineBuilder builder = new ValidationEngineBuilder().withVersion("4.0.1");
        ValidationEngine validationEngine = builder.fromSource("hl7.fhir.r4.core");

        // Read and parse resource
        String json = new String(Files.readAllBytes(Paths.get(TEST_FILE_PATH)));
        IParser parser = fhirContext.newJsonParser();
        Resource resource = (Resource) parser.parseResource(json);

        // Convert to InputStream for validation
        byte[] resourceBytes = parser.encodeResourceToString(resource).getBytes();
        InputStream inputStream = new ByteArrayInputStream(resourceBytes);

        // Validate against profile
        List<String> profiles = Collections.singletonList(profileUrl);
        OperationOutcome outcome = validationEngine.validate(FhirFormat.JSON, inputStream, profiles);

        // Assertions
        assertNotNull(outcome, "Validation outcome should not be null");
        assertFalse(outcome.getIssue().isEmpty(), "There should be at least one validation issue");

        // Output issues for debugging
        for (OperationOutcomeIssueComponent issue : outcome.getIssue()) {
            System.out.println("Severity: " + issue.getSeverity());
            System.out.println("Location: " + issue.getExpression());
            System.out.println("Details: " + issue.getDetails().getText());
            System.out.println("---------------------------------------------------");
        }
    }
}
