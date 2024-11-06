package org.techbd.service.hl7.converters;

import java.util.List;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.InstantType;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.ResourceType;
import org.springframework.stereotype.Component;
import org.techbd.service.hl7.core.Hl7MessageUtil;
import org.techbd.service.hl7.template.ITemplate;

import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.v27.segment.MSH;

@Component
public interface IHl7SegmentToFhirConverter {

    String getSegmentName();

    Bundle.BundleEntryComponent convert(ITemplate template, Message message, String interactionId) throws Exception;

    ResourceType getResourceType();

    CanonicalType getProfileUrl();

    default Meta getMeta(Message message, ITemplate template) {
        if (template == null || message == null) {
            return new Meta();
        }

        final MSH msh = template.getMSH(message);
        if (msh == null || msh.getMsh7_DateTimeOfMessage() == null
                || msh.getMsh7_DateTimeOfMessage().getValue() == null) {
            return new Meta();
        }

        final String lastUpdated = msh.getMsh7_DateTimeOfMessage().getValue();
        final String isoLastUpdated = Hl7MessageUtil.convertHl7DateTimeToIso(lastUpdated);

        final Meta meta = new Meta();
        if (isoLastUpdated != null) {
            meta.setLastUpdatedElement(new InstantType(isoLastUpdated));
        }
        meta.setProfile(List.of(getProfileUrl())); 
        meta.setExtension(null); // TODO - need to populate extension (extension can be null if not populated)
        return meta;
    }

}
