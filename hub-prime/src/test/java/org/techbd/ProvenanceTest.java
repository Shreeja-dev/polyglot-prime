package org.techbd;


import org.hl7.fhir.r4.model.Provenance;
import org.junit.jupiter.api.Test;

import ca.uhn.fhir.context.FhirContext;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
class ProvenanceTest {

    @Test
    void testProvenanceDeserialization() throws IOException {
        // Path to the JSON file
        Path path = Path.of("src/test/resources/org/techbd/provenance.json");

        // Read the JSON file
        StringBuilder jsonContent = new StringBuilder();
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String line;
            while ((line = reader.readLine()) != null) {
                jsonContent.append(line);
            }
        }

        // Parse JSON into Provenance object using HAPI FHIR
        FhirContext fhirContext = FhirContext.forR4();
        Provenance provenance = (Provenance) fhirContext.newJsonParser().parseResource(jsonContent.toString());

        // Assertions on Provenance values
        assertThat(provenance.getResourceType().toString()).isEqualTo("Provenance");
        assertThat(provenance.getRecorded()).isNotNull();
        assertThat(provenance.getTarget().size()).isGreaterThan(0);
        assertThat(provenance.getTargetFirstRep().getReference()).isEqualTo("Bundle/example");
        assertThat(provenance.getAgentFirstRep().getWho().getReference()).isEqualTo("Organization/TechByDesign");
        assertThat(provenance.getActivity().getCodingFirstRep().getCode()).isEqualTo("CREATE");
        assertThat(provenance.getEntityFirstRep().getRole().toCode()).isEqualTo("source");
        assertThat(provenance.getEntityFirstRep().getWhat().getReference()).isEqualTo("DocumentReference/example");
    }
}
