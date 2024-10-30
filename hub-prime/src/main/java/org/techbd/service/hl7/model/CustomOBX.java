package org.techbd.service.hl7.model;

import ca.uhn.hl7v2.model.Group;
import ca.uhn.hl7v2.model.v28.segment.OBX;
import ca.uhn.hl7v2.parser.ModelClassFactory;

public class CustomOBX extends OBX {
    public CustomOBX(Group parent, ModelClassFactory factory) {
        super(parent, factory);
    }
}