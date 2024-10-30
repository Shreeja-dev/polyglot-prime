package org.techbd.service.hl7.converters;

import org.hl7.fhir.r4.model.Bundle;
import org.springframework.stereotype.Component;
import org.techbd.service.hl7.template.ITemplate;

@Component
public interface IHl7SegmentToFhirConverter {

    String getSegmentName();

    Bundle.BundleEntryComponent convert(ITemplate template,String interactionId) throws Exception;

    

}
