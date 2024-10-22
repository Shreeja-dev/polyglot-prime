package org.techbd.orchestrate.fhir;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.techbd.service.converters.shinny.BaseConverter;
import org.techbd.service.converters.shinny.BundleConverter;
import org.techbd.service.converters.shinny.Hl7FHIRToShinnyFHIRConverter;
import org.techbd.service.converters.shinny.DiagnosticReportConverter;
import org.techbd.service.converters.shinny.EncounterConverter;
import org.techbd.service.converters.shinny.IConverter;
import org.techbd.service.converters.shinny.ObservationConverter;
import org.techbd.service.converters.shinny.PatientConverter;
import org.techbd.service.converters.shinny.PractitionerConverter;
import org.techbd.service.converters.shinny.QuestionnaireConverter;
import org.techbd.service.converters.shinny.QuestionnaireResponseConverter;
import org.techbd.service.http.hub.prime.api.Hl7Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import io.github.linuxforhealth.hl7.HL7ToFHIRConverter;

public class Hl7FHIRLinuxForHealthConversionTest {

    private Hl7FHIRToShinnyFHIRConverter bundleToFHIRConverter;
     private static final Logger LOG = LoggerFactory.getLogger(Hl7FHIRLinuxForHealthConversionTest.class);

    @BeforeEach
    public void setUp() {
        bundleToFHIRConverter = new Hl7FHIRToShinnyFHIRConverter(getConverters()); 
      
    }
    @Test
    public void testFullQuestionaireResponse() throws Exception {
        final String hl7Message2 = loadFile("org/techbd/hl7-files/full-questionaire-response.hl7");
        HL7ToFHIRConverter ftv = new HL7ToFHIRConverter();
        final String output = ftv.convert(hl7Message2);
         final var outputFileName = "src/test/resources/org/techbd/converted-fhir/full-questionaire-response-linux-test.json";
        wrtieToFile(output, outputFileName);

        // Map<String,Object> outputMap = Configuration.objectMapper.readValue(output,
        // new TypeReference<Map<String, Object>>() {
        // });
        // outputMap.keySet().stream().forEach(key->{
        //     LOG.warn(key);
        // });
        // replaceUrls(outputMap); 
         // String modifiedJson = Configuration.objectMapper.writeValueAsString(outputMap);

        // String shinnyJson = bundleToFHIRConverter.convertToShinnyFHIRJson(output);
        // final var modifiedJsonLocation = "src/test/resources/org/techbd/converted-fhir/full-questionaire-response-linux-shinny.json";
        // wrtieToFile(shinnyJson, modifiedJsonLocation);
    }

    @Test
    public void testFullQuestionaireResponse2() throws Exception {
        final String hl7Message2 = loadFile("org/techbd/hl7-files/full-questionaire-response-edited.hl7");
        HL7ToFHIRConverter ftv = new HL7ToFHIRConverter();
        final String output = ftv.convert(hl7Message2);;
      
        String shinnyJson = bundleToFHIRConverter.convertToShinnyFHIRJson(output);
        final var modifiedJsonLocation = "src/test/resources/org/techbd/converted-fhir/full-questionaire-response-test.json";
        wrtieToFile(shinnyJson, modifiedJsonLocation);
    }



    @Test
    public void testPatientDetailsWithMRN() throws Exception {
        String  hl7message = "MSH|^~\\&|SendTest1|Sendfac1|Receiveapp1|Receivefac1|202101010000|security|PPR^PC1^PPR_PC1|1|P^I|2.6||||||ASCII||\n"
        + "PID|||1234^^^^MR||DOE^JANE^|||F|||||||||||||||||||||\n"
        + "PV1||I|6N^1234^A^GENHOS|||||||SUR||||||||S||||||||||||||||||||||||||\n"
        + "PRB|AD||202101010000|aortic stenosis|53692||2|||202101010000\n"
        + "ORC|NW|1000^OE|9999999^RX|||E|^Q6H^D10^^^R\n";
        HL7ToFHIRConverter ftv = new HL7ToFHIRConverter();
        final String output = ftv.convert(hl7message);
        String shinnyJson = bundleToFHIRConverter.convertToShinnyFHIRJson(output);
        final var modifiedJsonLocation = "src/test/resources/org/techbd/converted-fhir/full-questionaire-response-test.json";
        wrtieToFile(shinnyJson, modifiedJsonLocation);
    }

    @Test
    public void  testMessage() throws Exception {
        String hl7message = "MSH|^~\\\\&|SendTest1|Sendfac1|Receiveapp1|Receivefac1|200603081747|security|ORU^R01|MSGID000005|T|2.6\r"
                + "PID||45483|45483||SMITH^SUZIE^||20160813|M|||123 MAIN STREET^^SCHENECTADY^NY^12345||(123)456-7890|||||^^^T||||||||||||\r"
                + "OBR|1||986^IA PHIMS Stage^2.16.840.1.114222.4.3.3.5.1.2^ISO|1051-2^New Born Screening^LN|||20151009173644|||||||||||||002|||||F|||2740^Tsadok^Janetary~2913^Merrit^Darren^F~3065^Mahoney^Paul^J~4723^Loh^Robert^L~9052^Winter^Oscar^|||||\r";

                HL7ToFHIRConverter ftv = new HL7ToFHIRConverter();
                final String output = ftv.convert(hl7message);
                String shinnyJson = bundleToFHIRConverter.convertToShinnyFHIRJson(output);
                final var modifiedJsonLocation = "src/test/resources/org/techbd/converted-fhir/full-questionaire-response-test.json";
                wrtieToFile(shinnyJson, modifiedJsonLocation);
    }

    @Test
    public void testPatientDetailsWithOMBRace() throws Exception {
        String hl7Message = "MSH|^~\\&|SendingApp|SendingFac|ReceivingApp|ReceivingFac|20240223000000|security|ADT^A01|123456|P|2.7||||||ASCII||\n"
        + "PID|||11223344^^^^MR||DOE^JON^BOB^^Mr.^Jr.^III||19810716|M|||||||||||||||||||||\n"
        + "PV1||I|6N^1234^A^GENHOS|||||||SUR||||||||S||||||||||||||||||||||||||\n"
        + "PRB|AD||20240223000000|aortic stenosis|53692||2|||20240223000000\n"
        + "ORC|NW|1000^OE|9999999^RX|||E|^Q6H^D10^^^R\n"
        + "PD1||AA12345C^Medicaid Number~999-34-2964^SSN\r"
        + "ADDR|115 Broadway Apt2^^New York^MANHATTAN^NY^10032\r"
        + "PH|5551206047~H\r"
        + "GND|M\r"
        + "LANG|EN|1\r"
        + "RCE|A|Hispanic or Latino\r"
        + "RCE|A|Asian\r"
        + "NTE|1|Notes: Patient identifies as male\r"
        + "NTE|2|Preferred pronouns: he/him/his/his/himself\r"
        + "NTE|3|Profile: SHINNY Patient Profile\r"
        + "RGS|1|\r"
        + "RG1|1|HL7|US Core Ethnicity|2135-2|Hispanic or Latino\r"
        + "RG1|2|HL7|US Core Race|2028-9|Asian\r";
    
       
    
        HL7ToFHIRConverter ftv = new HL7ToFHIRConverter();
        final String output = ftv.convert(hl7Message);
        String shinnyJson = bundleToFHIRConverter.convertToShinnyFHIRJson(output);
        final var modifiedJsonLocation = "src/test/resources/org/techbd/converted-fhir/full-questionaire-response-test.json";
        wrtieToFile(shinnyJson, modifiedJsonLocation);
    }

    @Test
    public void testOBR() throws Exception {
        String hl7Message = "MSH|^~\\&|SendingApp|SendingFacility|ReceivingApp|ReceivingFacility|20240923||ORU^R01|123456|P|2.3.1|||AL|NE|USA|||\n" +
        "OBR|1|||AHCHRSN^AHC-HRSN modified screening tool|||20240923|||||20240923|1234567^Levin^Henry|||F|||\n" +
        "OBX|1|CE|59284-0^Consent Document^LN||LA33-6^Yes^LN||||||F|||20240923||1234567^Levin^Henry|||||||Good Health Clinic|\n" +
        "OBX|2|ST|^Questionnaire details||The questionnaire from which these responses are drawn is based on LOINC 96777-8 (Accountable health communities (AHC) health-related social needs screening (HRSN) tool) plus 2 questions drawn from LOINC 97023-6 (Accountable health communities (AHC) health-related social needs (HRSN) supplemental questions).||||||F|||20240923||1234567^Levin^Henry|||||||Good Health Clinic|\n" +
        "OBX|3|CE|71802-3^What is your living situation today?^LN||LA31993-1^I have a steady place to live^LN||||||F|||20240923||1234567^Levin^Henry|||||||Good Health Clinic|\n" +
        "OBX|4|CE|96778-6^Think about the place you live. Do you have problems with any of the following?^LN||LA28580-1^Mold^LN||||||F|||20240923||1234567^Levin^Henry|||||||Good Health Clinic|";
        HL7ToFHIRConverter ftv = new HL7ToFHIRConverter();
        final String output = ftv.convert(hl7Message);
   //     String shinnyJson = bundleToFHIRConverter.convertToShinnyFHIRJson(output);
        final var modifiedJsonLocation = "src/test/resources/org/techbd/converted-fhir/full-questionaire-response-test.json";
        wrtieToFile(output, modifiedJsonLocation);
    }
    private  List<IConverter> getConverters() {
        return List.of(new BaseConverter() ,new PatientConverter() , new DiagnosticReportConverter() ,new PractitionerConverter() ,new EncounterConverter(),new ObservationConverter() ,new BundleConverter(),new QuestionnaireConverter() , new BundleConverter() ,new QuestionnaireResponseConverter());
    }
    // public static void replaceUrls(Map<String, Object> map) {
    //     map.entrySet().forEach(entry -> {
    //         var key = entry.getKey();
    //         var value = entry.getValue();
    
    //         if (key.equals("url")) {
    //             entry.setValue("https://shinny.org"); // Set the value to "test" if the key is "url"
    //         }
    
    //         LOG.warn("Key: {}", key); // Log the key
    
    //         // Using Pattern Matching for instanceof to simplify type checks
    //         switch (value) {
    //             case Map<?, ?> nestedMap -> replaceUrls((Map<String, Object>) nestedMap);
    //             case List<?> list -> list.forEach(item -> {
    //                 if (item instanceof Map<?, ?> nestedItem) {
    //                     replaceUrls((Map<String, Object>) nestedItem);
    //                 } else {
    //                     LOG.warn("List item: {}", item); // Log non-map items in the list
    //                 }
    //             });
    //             default -> LOG.warn("Value: {}", value); // Log the value directly
    //         }
    //     });
    // }
    private String loadFile(final String filename) throws IOException {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(filename)) {
            if (inputStream == null) {
                System.err.println("Failed to load the fixture: " + filename);
                return null;
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Failed to read JSON input from file: " + e.getMessage());
            return null;
        }
    }

    private void wrtieToFile(String jsonString, String outputFileName) throws Exception {
        File outputFile = new File(outputFileName);
        // Use ObjectMapper to pretty print the JSON
        ObjectMapper mapper = new ObjectMapper();
        Object jsonObject = mapper.readValue(jsonString, Object.class); // Convert JSON string to Object
        ObjectWriter writer = mapper.writerWithDefaultPrettyPrinter();
        String prettyJson = writer.writeValueAsString(jsonObject); // Pretty-print JSON

        try (BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(outputFile, false))) {
            bufferedWriter.write(prettyJson);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write to file", e);
        }
    }

}
