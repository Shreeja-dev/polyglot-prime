package org.techbd.service.hl7.core;

public enum Hl7SegmentType {
    MSH("MSH"),
    PID("PID"),
    OBR("OBR"),
    OBX("OBX"),
    PV1("PV1");

    private final String segmentName;

    Hl7SegmentType(String segmentName) {
        this.segmentName = segmentName;
    }

    public String getSegmentName() {
        return segmentName;
    }

    public static Hl7SegmentType fromString(String segmentName) {
        for (Hl7SegmentType type : Hl7SegmentType.values()) {
            if (type.getSegmentName().equalsIgnoreCase(segmentName)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unsupported segment type: " + segmentName);
    }
}
