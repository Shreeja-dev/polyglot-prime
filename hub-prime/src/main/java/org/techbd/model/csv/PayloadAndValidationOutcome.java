package org.techbd.model.csv;

import java.util.List;
import java.util.Map;

import org.hl7.fhir.r4.model.Parameters;

public record PayloadAndValidationOutcome(List<FileDetail> fileDetails, boolean isValid,String groupInteractionId,Parameters provenance,Map<String,Object> validationResults) {
}
