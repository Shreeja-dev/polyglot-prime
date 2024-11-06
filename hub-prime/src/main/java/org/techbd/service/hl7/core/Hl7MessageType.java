package org.techbd.service.hl7.core;

import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum Hl7MessageType {
    ORU_R01("ORU^R01^ORU_R01", "Observation Result Unsolicited", "orur01");

    private final String code;
    private final String description;
    private final String alias;

    Hl7MessageType(String code, String description, String alias) {
        this.code = code;
        this.description = description;
        this.alias = alias;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public String getAlias() {
        return alias;
    }

    @Override
    public String toString() {
        return code + " - " + description;
    }

    public static Hl7MessageType fromCode(String code) {
        for (Hl7MessageType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown HL7 Message Type code: " + code);
    }

    public static Hl7MessageType fromAlias(String alias) {
        for (Hl7MessageType type : values()) {
            if (type.alias.equals(alias)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown alias: " + alias);
    }

    public static String getAllTypesAsString() {
        return Stream.of(values())
                     .map(Hl7MessageType::toString)
                     .collect(Collectors.joining(", "));
    }
}
