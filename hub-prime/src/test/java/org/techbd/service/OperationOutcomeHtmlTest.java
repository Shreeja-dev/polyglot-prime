package org.techbd.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.hl7.fhir.r4.model.Narrative;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.junit.jupiter.api.Test;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;

class OperationOutcomeHtmlTest {
    @Test
    void testGeneratedJsonWithHtmlAndValidationIssue() throws Exception {
        // Create the OperationOutcome
        final var operationOutcome = new OperationOutcome();
    
        // Create and set the narrative with HTML content
        Narrative narrative = new Narrative();
        narrative.setStatus(Narrative.NarrativeStatus.ADDITIONAL);
        narrative.setDivAsString("""
            <div xmlns="http://www.w3.org/1999/xhtml">
                <table class="d-block user-select-contain" data-paste-markdown-skip="">
                  <tbody class="d-block">
                    <tr class="d-block">
                      <td class="d-block comment-body markdown-body  js-comment-body">
                          <p dir="auto">Add a memo or note field to the beginning of the OperationOutcome responses where the OperationOutcome is not valid.</p>
                          <p dir="auto">The text should read as follows:</p>
                          <blockquote>
                              <p dir="auto"><strong>Having Issues?</strong></p>
                              <p dir="auto">Please review resources below which should lead you to help you need in different circumstances.</p>
                              <h4 dir="auto">General Issues</h4>
                              <ul dir="auto">
                                  <li><a href="https://techbd.org/get-help" rel="nofollow">Tech by Design's Get Help page</a></li>
                                  <li><a href="https://github.com/tech-by-design/help-desk-non-phi/discussions/categories/frequently-asked-questions">FAQ Section</a></li>
                              </ul>
                              <h4 dir="auto">Tech by Design Hub Issues</h4>
                              <ul dir="auto">
                                  <li><a href="https://techbd.org/hub" rel="nofollow">Tech by Design's Hub page</a></li>
                                  <li><a href="https://status.techbd.org" rel="nofollow">Tech by Design Hub Status</a></li>
                                  <li><a href="https://phi.hub.qa.techbd.org/docs/swagger-ui/techbd-api" rel="nofollow">Tech by Deisgn Hub Docs</a></li>
                              </ul>
                              <h4 dir="auto">FHIR Implementation Guide or Validation Rules Questions</h4>
                              <ul dir="auto">
                                  <li><a href="https://shinny.org/ImplementationGuide/HRSN/index.html" rel="nofollow">FHIR IG documentation</a></li>
                                  <li>If your issue cannot be resolved, you should create a ticket in the <a href="https://nyec.atlassian.net/servicedesk/customer/portal/19/user/login?destination=portal%2F19" rel="nofollow">NYeC Data Lake Jira system</a> for further assistance surrounding the Implementation Guide.</li>
                              </ul>
                              <h4 dir="auto">NYeC API Gateway and/or Data Lake Issues</h4>
                              <ul dir="auto">
                                  <li>If you've utilized the Tech by Design Hub and recognize errors in forwarding to the data lake, please create a ticket in the <a href="https://nyec.atlassian.net/servicedesk/customer/portal/18" rel="nofollow">NYeC Data Lake Jira system</a> for further assistance with non-Implementation Guide related issues.</li>
                              </ul>
                          </blockquote>
                      </td>
                    </tr>
                  </tbody>
                </table>
            </div>
            """);
    
        operationOutcome.setText(narrative);
    
        // Add a dummy validation issue
        OperationOutcome.OperationOutcomeIssueComponent issue = new OperationOutcome.OperationOutcomeIssueComponent();
        issue.setSeverity(OperationOutcome.IssueSeverity.ERROR);
        issue.setCode(OperationOutcome.IssueType.INVALID);
        issue.setDiagnostics("Dummy validation issue: Missing required field 'identifier'.");
    
        operationOutcome.addIssue(issue);
    
        // Generate the JSON
        final var filePath = "src/test/resources/org/techbd/csv/generated-json/operation-outcome-html-with-issue.json";
        final FhirContext fhirContext = FhirContext.forR4();
        final IParser fhirJsonParser = fhirContext.newJsonParser().setPrettyPrint(true);
        final String fhirResourceJson = fhirJsonParser.encodeResourceToString(operationOutcome);
    
        // Write to file
        final Path outputPath = Paths.get(filePath);
        Files.createDirectories(outputPath.getParent());
        Files.writeString(outputPath, fhirResourceJson);
    }
    
    
}

