package org.techbd.service.hl7.converters;

import java.util.Map;

import org.hl7.fhir.r4.model.CanonicalType;
import org.techbd.util.YamlUtil;

public abstract class BaseConverter implements IHl7SegmentToFhirConverter {
    public static Map<String, String> PROFILE_MAP = getProfileUrlMap();

    public static Map<String, String> getProfileUrlMap() {
        return YamlUtil.getYamlResourceAsMap("src/main/resources/shinny/profile.yml");
    }

    public CanonicalType getProfileUrl() {
        return new CanonicalType(PROFILE_MAP.get(getResourceType().name().toLowerCase()));
    }
}
