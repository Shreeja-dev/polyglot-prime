package org.techbd.service.hl7.model;

import ca.uhn.hl7v2.model.Group;
import ca.uhn.hl7v2.model.v28.segment.OBR;
import ca.uhn.hl7v2.parser.ModelClassFactory;

public class CustomOBR extends OBR {
    public CustomOBR(Group parent, ModelClassFactory factory) {
        super(parent, factory);
    }
}