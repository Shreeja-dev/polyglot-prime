package org.techbd.service.hl7.model;

import ca.uhn.hl7v2.model.Group;
import ca.uhn.hl7v2.model.v28.segment.PV1;
import ca.uhn.hl7v2.parser.ModelClassFactory;

public class CustomPV1 extends PV1 {
    public CustomPV1(Group parent, ModelClassFactory factory) {
        super(parent, factory);
    }
}