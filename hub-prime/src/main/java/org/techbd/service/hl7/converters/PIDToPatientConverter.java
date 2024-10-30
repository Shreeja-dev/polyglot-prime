package org.techbd.service.hl7.converters;

import java.util.List;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.InstantType;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.stereotype.Component;
import org.techbd.service.hl7.core.Hl7MessageUtil;
import org.techbd.service.hl7.core.Hl7SegmentType;
import org.techbd.service.hl7.model.CustomPID;
import org.techbd.service.hl7.template.ITemplate;

@Component
public class PIDToPatientConverter implements IHl7SegmentToFhirConverter{

    @Override
    public Bundle.BundleEntryComponent convert(final ITemplate template, final String interactionId) throws Exception {
        final CustomPID pid = template.getPID();
        final String patientId = pid.getPid3_PatientIdentifierList(0).getCx1_IDNumber().getValue();
        final String familyName = pid.getPid5_PatientName(0).getXpn1_FamilyName().getFn1_Surname().getValue();
        final String givenName = pid.getPid5_PatientName(0).getXpn2_GivenName().getValue();
        final String middleName = pid.getPid5_PatientName(0).getXpn3_SecondAndFurtherGivenNamesOrInitialsThereof().getValue();
        final String prefix = pid.getPid5_PatientName(0).getXpn5_PrefixEgDR().getValue();
        final String suffix = pid.getPid5_PatientName(0).getXpn4_SuffixEgJRorIII().getValue();
        final String birthDate = pid.getPid7_DateTimeOfBirth().getValue();
        final String gender = pid.getPid8_AdministrativeSex().getIdentifier().getValue();
        final String isoBirthDate = Hl7MessageUtil.convertHl7DateToIso(birthDate);
        final String lastUpdated = template.getMSH().getMsh7_DateTimeOfMessage().getValue();
        final String isoLastUpdated = Hl7MessageUtil.convertHl7DateTimeToIso(lastUpdated);
    
        // Create the Patient resource
        final Patient patient = new Patient();
        patient.addIdentifier().setSystem("http://hospital.org/mrn").setValue(patientId); //TODO - revisit this
        patient.addName()
                .setFamily(familyName)
                .addGiven(givenName)
                .addPrefix(prefix)
                .addSuffix(suffix);
    
        patient.getNameFirstRep()
                .addExtension(new Extension()
                        .setUrl("http://shinny.org/ImplementationGuide/HRSN/StructureDefinition/middle-name")
                        .setValue(new org.hl7.fhir.r4.model.StringType(middleName)));  //TODO - read from yaml file
    
        patient.setBirthDateElement(new org.hl7.fhir.r4.model.DateType(isoBirthDate));
        patient.setGender(gender.equals("M") ? org.hl7.fhir.r4.model.Enumerations.AdministrativeGender.MALE
                : org.hl7.fhir.r4.model.Enumerations.AdministrativeGender.FEMALE);
    
        final Meta meta = new Meta();
        meta.setLastUpdatedElement(new InstantType(isoLastUpdated));
        meta.setProfile(List.of(
                new CanonicalType("http://shinny.org/ImplementationGuide/HRSN/StructureDefinition/shinny-patient"))); //TODO - read from yaml file
        patient.setMeta(meta);
    
        // Create BundleEntryComponent
        Bundle.BundleEntryComponent entryComponent = new Bundle.BundleEntryComponent();
        entryComponent.setResource(patient); 
        entryComponent.setFullUrl("urn:uuid:" + patientId); 
       // entryComponent.getResource().(ResourceType.Patient); 
        return entryComponent; // Return the created BundleEntryComponent
    }

    @Override
    public String getSegmentName() {
        return Hl7SegmentType.PID.getSegmentName();
    }
}
