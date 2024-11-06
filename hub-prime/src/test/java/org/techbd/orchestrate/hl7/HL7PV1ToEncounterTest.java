package org.techbd.orchestrate.hl7;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.v27.datatype.CWE;
import ca.uhn.hl7v2.model.v27.message.ORU_R01;
import ca.uhn.hl7v2.model.v27.segment.PV1;
import ca.uhn.hl7v2.parser.PipeParser;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Period;
import org.junit.jupiter.api.Test;
import org.techbd.service.hl7.core.Hl7MessageUtil;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.CanonicalType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class HL7PV1ToEncounterTest {

    @Test
    public void testHl7ToFhirEncounterConversion() throws Exception {
        HapiContext context = new DefaultHapiContext();
        String hl7Message = "MSH|^~\\&|HIS|RIH|EKG|EKG|202310190830||ORU^R01|MSG00001|P|2.7\r" +
                "PID|||12345^^^Hospital^MR||Doe^John^Bob^Jr.^Dr.||19700101|M\r" +
                "PV1|1|O|Virtual||||||||||||||||V123456|||||||||||||||||||||||||20240923|||||||V|\r";

        context.getParserConfiguration().setValidating(false);
        PipeParser pipeParser = context.getPipeParser();
        pipeParser.getParserConfiguration().setAllowUnknownVersions(true);
        Message message = pipeParser.parse(hl7Message);
        ORU_R01 oruMessage = (ORU_R01) message;
        PV1 pv1 = oruMessage.getPATIENT_RESULT().getPATIENT().getVISIT().getPV1();

        // Extracting values based on the provided PV1 message
        String encounterId = pv1.getPv119_VisitNumber().getCx1_IDNumber().getValue(); // "V123456"
        String encounterClass = pv1.getPv12_PatientClass() != null ? pv1.getPv12_PatientClass().encode() : null; // "O"
        String encounterLocation = pv1.getPv13_AssignedPatientLocation() != null
                ? pv1.getPv13_AssignedPatientLocation().getPl1_PointOfCare().encode()
                : "Virtual"; // "Virtual"

        // Note: Assuming admissionType and other fields exist as expected.
        CWE admissionTypeCWE = pv1.getPv14_AdmissionType();
        String admissionTypeCode = admissionTypeCWE != null && admissionTypeCWE.getIdentifier() != null
                ? admissionTypeCWE.getIdentifier().getValue()
                : null;

        // Log the identifier (code) of the CWE
        if (admissionTypeCWE != null && admissionTypeCWE.getIdentifier() != null) {
            System.out.println("Admission Type Identifier: " + admissionTypeCWE.getIdentifier().getValue());
        } else {
            System.out.println("Admission Type Identifier: null");
        }

        // Construct the Encounter resource
        Encounter encounter = new Encounter();
        encounter.setClass_(new Coding()
                .setSystem("http://terminology.hl7.org/CodeSystem/v2-0004")
                .setCode(encounterClass)
                .setDisplay("Outpatient"));
        encounter.setType(Collections.singletonList(new CodeableConcept().addCoding(
                new Coding()
                        .setSystem("http://terminology.hl7.org/CodeSystem/v2-0005")
                        .setCode(encounterLocation)
                        .setDisplay("Virtual"))));

        // If admissionTypeCode is valid, set it as the type
        if (admissionTypeCode != null) {
            CodeableConcept encounterType = new CodeableConcept()
                    .addCoding(new Coding()
                            .setSystem("http://terminology.hl7.org/CodeSystem/v2-0007")
                            .setCode(admissionTypeCode)
                            .setDisplay("Virtual Admission"));
            encounter.setType(List.of(encounterType));
        }

        assertThat(encounter.getClass_().getCode()).isEqualTo("O");
        assertThat(encounter.getClass_().getSystem())
                .isEqualTo("http://terminology.hl7.org/CodeSystem/v2-0004");
        assertThat(encounter.getClass_().getDisplay()).isEqualTo("Outpatient");

        assertThat(encounter.getTypeFirstRep().getCodingFirstRep().getCode()).isEqualTo("Virtual");
        assertThat(encounter.getTypeFirstRep().getCodingFirstRep().getSystem())
                .isEqualTo("http://terminology.hl7.org/CodeSystem/v2-0005");
        assertThat(encounter.getTypeFirstRep().getCodingFirstRep().getDisplay()).isEqualTo("Virtual");

        // If encounterId exists, add it as an identifier
        if (encounterId != null) {
            encounter.addIdentifier()
                    .setSystem("http://hospital.org/encounter-id")
                    .setValue(encounterId);
        }
        String rawEncounterDate = pv1.getPv144_AdmitDateTime() != null ? pv1.getPv144_AdmitDateTime().encode() : null;

        // Convert the date using HL7MessageUtil.convertHl7DateToIso
        if (rawEncounterDate != null) {
            rawEncounterDate = Hl7MessageUtil.convertHl7DateToIso(rawEncounterDate);
        }
        encounter.setPeriod(new Period().setStartElement(new DateTimeType(rawEncounterDate)));

        // Set FHIR metadata
        Meta meta = new Meta();
        meta.setProfile(Collections.singletonList(
                new CanonicalType("http://shinny.org/us/ny/hrsn/StructureDefinition/shinny-encounter")));
        encounter.setMeta(meta);

        // Convert to JSON for printing
        FhirContext fhirContext = FhirContext.forR4();
        IParser fhirJsonParser = fhirContext.newJsonParser().setPrettyPrint(true);
        String fhirResourceJson = fhirJsonParser.encodeResourceToString(encounter);
        System.out.println(fhirResourceJson);

        // Assertions
        assertThat(encounter.getClass_().getCode()).isEqualTo("O");
        assertThat(encounter.getClass_().getSystem())
                .isEqualTo("http://terminology.hl7.org/CodeSystem/v2-0004");
        assertThat(encounter.getClass_().getDisplay()).isEqualTo("Outpatient");

        assertThat(encounter.getTypeFirstRep().getCodingFirstRep().getCode()).isEqualTo("Virtual");
        assertThat(encounter.getTypeFirstRep().getCodingFirstRep().getSystem())
                .isEqualTo("http://terminology.hl7.org/CodeSystem/v2-0005");
        assertThat(encounter.getTypeFirstRep().getCodingFirstRep().getDisplay()).isEqualTo("Virtual");

        assertThat(encounter.getIdentifierFirstRep().getValue()).isEqualTo("V123456");
        assertThat(encounter.getPeriod().getStartElement().asStringValue()).isEqualTo("2024-09-23");
        assertThat(encounter.getMeta().getProfile().get(0).getValue())
                .contains("http://shinny.org/us/ny/hrsn/StructureDefinition/shinny-encounter");
    }

    @Test
    public void testGenerateEncounterJson() throws Exception {
        // HL7 message (ORU^R01 with PV1 segment)
        String hl7Message = "MSH|^~\\&|HIS|RIH|EKG|EKG|202310190830||ORU^R01|MSG00001|P|2.7\r" +
                "PID|||12345^^^Hospital^MR||Doe^John^Bob^Jr.^Dr.||19700101|M\r" +
                "PV1|1|O|Virtual||||||||||||||||V123456|||||||||||||||||||||||||20240923|||||||V|\r";

        // Output file path
        String outputFilePath = "src/test/resources/org/techbd/hl7/orur01/generated-json/encounter.json";

        // HL7 context and message parsing
        HapiContext context = new DefaultHapiContext();
        PipeParser pipeParser = context.getPipeParser();
        context.getParserConfiguration().setValidating(false);
        pipeParser.getParserConfiguration().setAllowUnknownVersions(true);
        Message message = pipeParser.parse(hl7Message);
        ORU_R01 oruMessage = (ORU_R01) message;
        PV1 pv1 = oruMessage.getPATIENT_RESULT().getPATIENT().getVISIT().getPV1();

        // Extract values from the PV1 segment
        String encounterId = pv1.getPv119_VisitNumber().getCx1_IDNumber().getValue(); // "V123456"
        String encounterClass = pv1.getPv12_PatientClass() != null ? pv1.getPv12_PatientClass().encode() : null; // "O"
        String encounterLocation = pv1.getPv13_AssignedPatientLocation() != null
                ? pv1.getPv13_AssignedPatientLocation().getPl1_PointOfCare().encode()
                : "Virtual"; // "Virtual"
        CWE admissionTypeCWE = pv1.getPv14_AdmissionType();
        String admissionTypeCode = admissionTypeCWE != null && admissionTypeCWE.getIdentifier() != null
                ? admissionTypeCWE.getIdentifier().getValue()
                : null;

        Encounter encounter = new Encounter();
        encounter.setClass_(new Coding()
                .setSystem("http://terminology.hl7.org/CodeSystem/v2-0004")
                .setCode(encounterClass)
                .setDisplay("Outpatient"));
        encounter.setType(Collections.singletonList(new CodeableConcept().addCoding(
                new Coding()
                        .setSystem("http://terminology.hl7.org/CodeSystem/v2-0005")
                        .setCode(encounterLocation)
                        .setDisplay("Virtual"))));

        if (admissionTypeCode != null) {
            CodeableConcept encounterType = new CodeableConcept()
                    .addCoding(new Coding()
                            .setSystem("http://terminology.hl7.org/CodeSystem/v2-0007")
                            .setCode(admissionTypeCode)
                            .setDisplay("Virtual Admission"));
            encounter.setType(Collections.singletonList(encounterType));
        }
        if (encounterId != null) {
            encounter.addIdentifier()
                    .setSystem("http://hospital.org/encounter-id")
                    .setValue(encounterId);
        }
        String rawEncounterDate = pv1.getPv144_AdmitDateTime() != null ? pv1.getPv144_AdmitDateTime().encode() : null;
        if (rawEncounterDate != null) {
            rawEncounterDate = Hl7MessageUtil.convertHl7DateToIso(rawEncounterDate);
        }
        encounter.setPeriod(new Period().setStartElement(new DateTimeType(rawEncounterDate)));
        Meta meta = new Meta();
        meta.setProfile(Collections.singletonList(
                new CanonicalType("http://shinny.org/ImplementationGuide/HRSN/StructureDefinition/shinny-encounter")));
        encounter.setMeta(meta);
        FhirContext fhirContext = FhirContext.forR4();
        IParser fhirJsonParser = fhirContext.newJsonParser().setPrettyPrint(true);
        String fhirResourceJson = fhirJsonParser.encodeResourceToString(encounter);
        Path outputPath = Path.of(outputFilePath);
        Files.createDirectories(outputPath.getParent());
        Files.writeString(outputPath, fhirResourceJson);
    }

}
