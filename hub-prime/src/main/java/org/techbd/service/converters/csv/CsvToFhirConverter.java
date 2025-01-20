package org.techbd.service.converters.csv;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Provenance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.techbd.conf.Configuration;
import org.techbd.model.csv.DemographicData;
import org.techbd.model.csv.FhirBundleAndProvenance;
import org.techbd.model.csv.QeAdminData;
import org.techbd.model.csv.ScreeningObservationData;
import org.techbd.model.csv.ScreeningProfileData;
import org.techbd.service.converters.shinny.BundleConverter;
import org.techbd.service.converters.shinny.IConverter;
import org.techbd.service.http.hub.prime.AppConfig;

import ca.uhn.fhir.context.FhirContext;

@Component
public class CsvToFhirConverter {

    private final AppConfig appConfig;
    private final List<IConverter> converters; // todo move other converters inside bundle converter after hl7 changes
    private final BundleConverter bundleConverter;
    private static final Logger LOG = LoggerFactory.getLogger(CsvToFhirConverter.class.getName());

    public CsvToFhirConverter(AppConfig appConfig, BundleConverter bundleConverter, List<IConverter> converters) {
        this.appConfig = appConfig;
        this.converters = converters;
        this.bundleConverter = bundleConverter;
    }

    public FhirBundleAndProvenance convert(DemographicData demographicData,
            QeAdminData qeAdminData, ScreeningProfileData screeningProfileData,
            List<ScreeningObservationData> screeningDataList, String interactionId,Map<String,Object> existingProvenance,Map<String,Object> bundleProvenance) {
        Bundle bundle = null;
        String provenanceJson = null;
        try {
            LOG.info("CsvToFhirConvereter::convert - BEGIN for interactionId :{}", interactionId);
            bundle = bundleConverter.generateEmptyBundle(interactionId, appConfig.getIgVersion(), demographicData);
            LOG.debug("CsvToFhirConvereter::convert - Bundle entry created :{}", interactionId);
            LOG.debug("Conversion of resources - BEGIN for interactionId :{}", interactionId);
            addEntries(bundle, demographicData, screeningDataList, qeAdminData, screeningProfileData, interactionId);
            LOG.debug("Conversion of resources - END for interactionId :{}", interactionId);
            provenanceJson = addProvenanceToGeneratedBundle(bundle, existingProvenance, bundleProvenance);
            LOG.info("CsvToFhirConvereter::convert - END for interactionId :{}", interactionId);
        } catch (Exception ex) {
            LOG.error("Exception in Csv conversion for interaction id : {}", interactionId, ex);
        }
        final var bundleStr= FhirContext.forR4().newJsonParser().encodeResourceToString(bundle);
        return new FhirBundleAndProvenance(bundleStr,provenanceJson);
    }

    private String addProvenanceToGeneratedBundle(Bundle bundle,Map<String,Object> existingProvenance,Map<String,Object> bundleProvenance) throws Exception {
        final Instant completedAt = Instant.now();
        if (null == existingProvenance || existingProvenance.size() == 0){
            existingProvenance = new HashMap<>();
        }
        if (null != bundleProvenance && bundleProvenance.size() > 0) {
            bundleProvenance.put("completedAt", completedAt);
            existingProvenance.putAll(bundleProvenance);
        }
        String provenanceJson = Configuration.objectMapper.writeValueAsString(existingProvenance);
        List<Provenance> provenanceList = Configuration.objectMapper.readValue(provenanceJson, Configuration.objectMapper.getTypeFactory().constructCollectionType(List.class, Provenance.class));
        if (provenanceList == null || provenanceList.isEmpty()) {
            throw new IllegalArgumentException("The provenance JSON must contain one or more Provenance entries.");
        }
        for (Provenance provenance : provenanceList) {
            if (provenance == null) {
                throw new IllegalArgumentException("One of the Provenance entries is null.");
            }
            BundleEntryComponent entry = new BundleEntryComponent();
            entry.setResource(provenance);
            bundle.addEntry(entry);
        }
        return provenanceJson;
    }
    private void addEntries(Bundle bundle, DemographicData demographicData,
            List<ScreeningObservationData> screeningObservationData,
            QeAdminData qeAdminData, ScreeningProfileData screeningProfileData, String interactionId) {
        List<BundleEntryComponent> entries = new ArrayList<>();
        Map<String, String> idsGenerated = new HashMap<>();

        // Iterate over the converters
        converters.stream().forEach(converter -> {
            try {
                // Attempt to process using the current converter
                entries.addAll(converter.convert(bundle, demographicData, qeAdminData, screeningProfileData,
                        screeningObservationData, interactionId, idsGenerated));
            } catch (Exception e) {
                // Log the error and continue with other converters
                LOG.error("Error occurred while processing converter: " + converter.getClass().getName(), e);
            }
        });

        // Add all successful entries to the bundle
        bundle.getEntry().addAll(entries);
    }
}