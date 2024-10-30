package org.techbd.service.hl7.core;

import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum Hl7Version {
    VERSION_27("2.7"),
    VERSION_28("2.8");

    private final String version;

    Hl7Version(String version) {
        this.version = version;
    }

    public String getVersion() {
        return version;
    }

    public static Hl7Version fromString(String version) {
        for (Hl7Version hl7Version : Hl7Version.values()) {
            if (hl7Version.version.equals(version)) {
                return hl7Version;
            }
        }
        throw new IllegalArgumentException("Unsupported HL7 version: " + version);
    }

    public static String getAllSupportedVersions() {
        return Stream.of(values())
                .map(Hl7Version::getVersion)
                .collect(Collectors.joining(", "));
    }
}
