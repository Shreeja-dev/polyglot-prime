package org.techbd.service.hl7.converters;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.ResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.techbd.service.hl7.core.Hl7MessageUtil;
import org.techbd.service.hl7.core.Hl7SegmentType;
import org.techbd.service.hl7.template.ITemplate;
import org.techbd.service.http.hub.prime.api.Hl7Service;

import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.v27.segment.PID;

@Component
public class PIDToPatientConverter extends BaseConverter {
        private static final Logger LOG = LoggerFactory.getLogger(PIDToPatientConverter.class.getName());

        @Override
        public Bundle.BundleEntryComponent convert(final ITemplate template, Message message,
                        final String interactionId) throws Exception {
                LOG.info("PIDToPatientConverter :: convert -BEGIN");
                final PID pid = template.getPID(message);
                final String patientId = (pid != null && pid.getPid3_PatientIdentifierList(0) != null
                                && pid.getPid3_PatientIdentifierList(0).getCx1_IDNumber() != null
                                && pid.getPid3_PatientIdentifierList(0).getCx1_IDNumber().getValue() != null)
                                                ? pid.getPid3_PatientIdentifierList(0).getCx1_IDNumber().getValue()
                                                : null;

                final String familyName = (pid != null && pid.getPid5_PatientName(0) != null
                                && pid.getPid5_PatientName(0).getXpn1_FamilyName() != null
                                && pid.getPid5_PatientName(0).getXpn1_FamilyName().getFn1_Surname() != null
                                && pid.getPid5_PatientName(0).getXpn1_FamilyName().getFn1_Surname().getValue() != null)
                                                ? pid.getPid5_PatientName(0).getXpn1_FamilyName().getFn1_Surname()
                                                                .getValue()
                                                : null;

                final String givenName = (pid != null && pid.getPid5_PatientName(0) != null
                                && pid.getPid5_PatientName(0).getXpn2_GivenName() != null
                                && pid.getPid5_PatientName(0).getXpn2_GivenName().getValue() != null)
                                                ? pid.getPid5_PatientName(0).getXpn2_GivenName().getValue()
                                                : null;

                final String middleName = (pid != null && pid.getPid5_PatientName(0) != null
                                && pid.getPid5_PatientName(0)
                                                .getXpn3_SecondAndFurtherGivenNamesOrInitialsThereof() != null
                                && pid.getPid5_PatientName(0).getXpn3_SecondAndFurtherGivenNamesOrInitialsThereof()
                                                .getValue() != null)
                                                                ? pid.getPid5_PatientName(0)
                                                                                .getXpn3_SecondAndFurtherGivenNamesOrInitialsThereof()
                                                                                .getValue()
                                                                : null;

                final String prefix = (pid != null && pid.getPid5_PatientName(0) != null
                                && pid.getPid5_PatientName(0).getXpn5_PrefixEgDR() != null
                                && pid.getPid5_PatientName(0).getXpn5_PrefixEgDR().getValue() != null)
                                                ? pid.getPid5_PatientName(0).getXpn5_PrefixEgDR().getValue()
                                                : null;

                final String suffix = (pid != null && pid.getPid5_PatientName(0) != null
                                && pid.getPid5_PatientName(0).getXpn4_SuffixEgJRorIII() != null
                                && pid.getPid5_PatientName(0).getXpn4_SuffixEgJRorIII().getValue() != null)
                                                ? pid.getPid5_PatientName(0).getXpn4_SuffixEgJRorIII().getValue()
                                                : null;

                final String birthDate = (pid != null && pid.getPid7_DateTimeOfBirth() != null
                                && pid.getPid7_DateTimeOfBirth().getValue() != null)
                                                ? pid.getPid7_DateTimeOfBirth().getValue()
                                                : null;

                final String gender = (pid != null && pid.getPid8_AdministrativeSex() != null
                                && pid.getPid8_AdministrativeSex().getIdentifier() != null
                                && pid.getPid8_AdministrativeSex().getIdentifier().getValue() != null)
                                                ? pid.getPid8_AdministrativeSex().getIdentifier().getValue()
                                                : null;

                final String isoBirthDate = birthDate != null ? Hl7MessageUtil.convertHl7DateToIso(birthDate) : null;

                // Create the Patient resource
                final Patient patient = new Patient();
                patient.addIdentifier().setSystem("http://hospital.org/mrn").setValue(patientId); // TODO - check how
                                                                                                  // systems can be
                                                                                                  // populated
                patient.addName()
                                .setFamily(familyName)
                                .addGiven(givenName)
                                .addPrefix(prefix)
                                .addSuffix(suffix);

                patient.getNameFirstRep()
                                .addExtension(new Extension()
                                                .setUrl("http://shinny.org/ImplementationGuide/HRSN/StructureDefinition/middle-name")
                                                .setValue(new org.hl7.fhir.r4.model.StringType(middleName))); // TODO -
                                                                                                              // read
                                                                                                              // from
                                                                                                              // yaml
                                                                                                              // file

                patient.setBirthDateElement(new org.hl7.fhir.r4.model.DateType(isoBirthDate));
                if (gender == null || gender.isEmpty() || (!gender.equals("M") && !gender.equals("F"))) {
                        patient.setGender(null);
                } else {
                        patient.setGender(gender.equals("M")
                                        ? org.hl7.fhir.r4.model.Enumerations.AdministrativeGender.MALE
                                        : org.hl7.fhir.r4.model.Enumerations.AdministrativeGender.FEMALE);
                }
                patient.setMeta(getMeta(message, template));
                Bundle.BundleEntryComponent entryComponent = new Bundle.BundleEntryComponent();
                entryComponent.setResource(patient);
                // TODO - check resource type
                entryComponent.setFullUrl("urn:uuid:" + patientId); // TODO - check on full url implementation
                LOG.info("PIDToPatientConverter :: convert -END");
                return entryComponent;
        }

        @Override
        public String getSegmentName() {
                return Hl7SegmentType.PID.getSegmentName();
        }

        @Override
        public ResourceType getResourceType() {
                return ResourceType.Patient;
        }
}
