package org.techbd.service.fhir.validation;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.ConceptValidationOptions;
import ca.uhn.fhir.context.support.IValidationSupport;
import ca.uhn.fhir.context.support.ValidationSupportContext;
import ca.uhn.fhir.context.support.ValueSetExpansionOptions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.ValueSet;
import org.techbd.util.fhir.FileUtils;

import java.util.*;

public class RaceEthnicityValidationSupport implements IValidationSupport {

    public static final String CDC_RACE_ETHNICITY_SYSTEM = "urn:oid:2.16.840.1.113883.6.238";
    public static final String NULL_FLAVOR_SYSTEM = "http://terminology.hl7.org/CodeSystem/v3-NullFlavor";

    public static final String OMB_RACE_VS_URL = "http://hl7.org/fhir/us/core/ValueSet/omb-race-category";
    public static final String OMB_ETHNICITY_VS_URL = "http://hl7.org/fhir/us/core/ValueSet/omb-ethnicity-category";
    public static final String DETAILED_RACE_VS_URL = "http://hl7.org/fhir/us/core/ValueSet/detailed-race";

    private final FhirContext fhirContext;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Dynamically loaded maps (no hardcoding)
    private final Map<String, String> ombRaceMap = new HashMap<>();
    private final Map<String, String> ombEthnicityMap = new HashMap<>();
    private final Map<String, String> nullFlavorMap = new HashMap<>();
    private final Map<String, String> allCdcConcepts = new HashMap<>();

    private CodeSystem codeSystemResource;
    private ValueSet ombRaceValueSet;
    private ValueSet ombEthnicityValueSet;
    private ValueSet detailedRaceValueSet;

    public RaceEthnicityValidationSupport(FhirContext fhirContext, String jsonFilePath) {
        this.fhirContext = fhirContext;
        loadConceptsFromJson(jsonFilePath);
        initPreExpandedValueSets();
    }

    private void loadConceptsFromJson(String jsonFilePath) {
        try {
            String json = FileUtils.readFile1(jsonFilePath);
            if (json != null && !json.isBlank()) {
                // 1. Read custom configuration nodes for categories
                JsonNode rootNode = objectMapper.readTree(json);

                readMapNode(rootNode.path("ombRaceCategories"), ombRaceMap);
                readMapNode(rootNode.path("ombEthnicityCategories"), ombEthnicityMap);
                readMapNode(rootNode.path("nullFlavors"), nullFlavorMap);

                // 2. Parse standard FHIR CodeSystem concepts
                codeSystemResource = fhirContext.newJsonParser().parseResource(CodeSystem.class, json);
                extractConcepts(codeSystemResource.getConcept());
            }
        } catch (Exception e) {
            // Log file read / parse failure
        }

        // Add all OMB categories into allCdcConcepts for general code system lookups
        allCdcConcepts.putAll(ombRaceMap);
        allCdcConcepts.putAll(ombEthnicityMap);

        if (codeSystemResource == null) {
            codeSystemResource = new CodeSystem();
            codeSystemResource.setUrl(CDC_RACE_ETHNICITY_SYSTEM);
            codeSystemResource.setStatus(Enumerations.PublicationStatus.ACTIVE);
        }
        codeSystemResource.setUrl(CDC_RACE_ETHNICITY_SYSTEM);
        codeSystemResource.setContent(CodeSystem.CodeSystemContentMode.COMPLETE);
    }

    private void readMapNode(JsonNode node, Map<String, String> targetMap) {
        if (node != null && node.isObject()) {
            node.fields().forEachRemaining(entry -> targetMap.put(entry.getKey(), entry.getValue().asText()));
        }
    }

    private void extractConcepts(List<CodeSystem.ConceptDefinitionComponent> concepts) {
        if (concepts == null) return;
        for (CodeSystem.ConceptDefinitionComponent c : concepts) {
            if (c.hasCode()) {
                allCdcConcepts.put(c.getCode(), c.hasDisplay() ? c.getDisplay() : c.getCode());
            }
            if (c.hasConcept()) {
                extractConcepts(c.getConcept());
            }
        }
    }

    private void initPreExpandedValueSets() {
        ombRaceValueSet = createExpandedValueSet(OMB_RACE_VS_URL, "omb-race-category", "OMB Race Categories", ombRaceMap, true);
        ombEthnicityValueSet = createExpandedValueSet(OMB_ETHNICITY_VS_URL, "omb-ethnicity-category", "OMB Ethnicity Categories", ombEthnicityMap, true);
        detailedRaceValueSet = createExpandedValueSet(DETAILED_RACE_VS_URL, "detailed-race", "Detailed Race", allCdcConcepts, false);
    }

    private ValueSet createExpandedValueSet(String url, String id, String name, Map<String, String> cdcCodes, boolean includeNullFlavor) {
        ValueSet vs = new ValueSet();
        vs.setId(id);
        vs.setUrl(url);
        vs.setVersion("7.0.0");
        vs.setName(name);
        vs.setStatus(Enumerations.PublicationStatus.ACTIVE);

        ValueSet.ValueSetExpansionComponent expansion = new ValueSet.ValueSetExpansionComponent();
        expansion.setTimestamp(new Date());

        ValueSet.ConceptSetComponent cdcInclude = new ValueSet.ConceptSetComponent().setSystem(CDC_RACE_ETHNICITY_SYSTEM);

        cdcCodes.forEach((code, display) -> {
            expansion.addContains()
                    .setSystem(CDC_RACE_ETHNICITY_SYSTEM)
                    .setCode(code)
                    .setDisplay(display);

            cdcInclude.addConcept()
                    .setCode(code)
                    .setDisplay(display);
        });

        vs.getCompose().addInclude(cdcInclude);

        if (includeNullFlavor) {
            ValueSet.ConceptSetComponent nfInclude = new ValueSet.ConceptSetComponent().setSystem(NULL_FLAVOR_SYSTEM);
            nullFlavorMap.forEach((code, display) -> {
                expansion.addContains()
                        .setSystem(NULL_FLAVOR_SYSTEM)
                        .setCode(code)
                        .setDisplay(display);

                nfInclude.addConcept()
                        .setCode(code)
                        .setDisplay(display);
            });
            vs.getCompose().addInclude(nfInclude);
        }

        vs.setExpansion(expansion);
        return vs;
    }

    private boolean isTargetValueSet(String url) {
        if (url == null) return false;
        String cleanUrl = url.contains("|") ? url.substring(0, url.indexOf('|')) : url;
        return OMB_RACE_VS_URL.equalsIgnoreCase(cleanUrl)
                || OMB_ETHNICITY_VS_URL.equalsIgnoreCase(cleanUrl)
                || DETAILED_RACE_VS_URL.equalsIgnoreCase(cleanUrl);
    }

    private ValueSet getMatchingValueSet(String url) {
        if (url == null) return null;
        String cleanUrl = url.contains("|") ? url.substring(0, url.indexOf('|')) : url;
        if (OMB_RACE_VS_URL.equalsIgnoreCase(cleanUrl)) return ombRaceValueSet;
        if (OMB_ETHNICITY_VS_URL.equalsIgnoreCase(cleanUrl)) return ombEthnicityValueSet;
        if (DETAILED_RACE_VS_URL.equalsIgnoreCase(cleanUrl)) return detailedRaceValueSet;
        return null;
    }

    @Override
    public FhirContext getFhirContext() {
        return fhirContext;
    }

    @Override
    public IBaseResource fetchCodeSystem(String theSystem) {
        if (CDC_RACE_ETHNICITY_SYSTEM.equalsIgnoreCase(theSystem)) {
            return codeSystemResource;
        }
        return null;
    }

    @Override
    public boolean isCodeSystemSupported(ValidationSupportContext theValidationSupportContext, String theSystem) {
        return CDC_RACE_ETHNICITY_SYSTEM.equalsIgnoreCase(theSystem);
    }

    @Override
    public ValueSetExpansionOutcome expandValueSet(ValidationSupportContext theValidationSupportContext,
                                                   ValueSetExpansionOptions theExpansionOptions,
                                                   IBaseResource theValueSetToExpand) {
        if (theValueSetToExpand instanceof ValueSet vs && vs.hasUrl() && isTargetValueSet(vs.getUrl())) {
            ValueSet matched = getMatchingValueSet(vs.getUrl());
            if (matched != null) {
                return new ValueSetExpansionOutcome(matched);
            }
        }
        return null;
    }

    @Override
    public ValueSet fetchValueSet(String theUrl) {
        return getMatchingValueSet(theUrl);
    }

    @Override
    public boolean isValueSetSupported(ValidationSupportContext theValidationSupportContext, String theValueSetUrl) {
        return isTargetValueSet(theValueSetUrl);
    }

    @Override
    public CodeValidationResult validateCode(ValidationSupportContext theValidationSupportContext,
                                            ConceptValidationOptions theOptions,
                                            String theCodeSystem,
                                            String theCode,
                                            String theDisplay,
                                            String theValueSetUrl) {
        if (theValueSetUrl != null && isTargetValueSet(theValueSetUrl)) {
            String baseUrl = theValueSetUrl.contains("|") ? theValueSetUrl.substring(0, theValueSetUrl.indexOf('|')) : theValueSetUrl;

            if (OMB_RACE_VS_URL.equalsIgnoreCase(baseUrl)) {
                if (ombRaceMap.containsKey(theCode) || nullFlavorMap.containsKey(theCode)) {
                    return new CodeValidationResult().setCode(theCode);
                }
                return new CodeValidationResult().setSeverity(IssueSeverity.ERROR)
                        .setMessage("Code " + theCode + " was not found in " + OMB_RACE_VS_URL);
            }

            if (OMB_ETHNICITY_VS_URL.equalsIgnoreCase(baseUrl)) {
                if (ombEthnicityMap.containsKey(theCode) || nullFlavorMap.containsKey(theCode)) {
                    return new CodeValidationResult().setCode(theCode);
                }
                return new CodeValidationResult().setSeverity(IssueSeverity.ERROR)
                        .setMessage("Code " + theCode + " was not found in " + OMB_ETHNICITY_VS_URL);
            }

            if (DETAILED_RACE_VS_URL.equalsIgnoreCase(baseUrl)) {
                if (allCdcConcepts.containsKey(theCode) || nullFlavorMap.containsKey(theCode)) {
                    return new CodeValidationResult().setCode(theCode);
                }
                return new CodeValidationResult().setSeverity(IssueSeverity.ERROR)
                        .setMessage("Code " + theCode + " was not found in " + DETAILED_RACE_VS_URL);
            }
        }

        if (CDC_RACE_ETHNICITY_SYSTEM.equalsIgnoreCase(theCodeSystem)) {
            if (allCdcConcepts.containsKey(theCode) || ombRaceMap.containsKey(theCode) || ombEthnicityMap.containsKey(theCode)) {
                return new CodeValidationResult().setCode(theCode);
            }
            return new CodeValidationResult().setSeverity(IssueSeverity.ERROR)
                    .setMessage("Unknown code " + theCode + " for system " + CDC_RACE_ETHNICITY_SYSTEM);
        }

        return null;
    }
}