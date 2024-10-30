package org.techbd.service.hl7.model;

import ca.uhn.hl7v2.model.v28.segment.PID;
import ca.uhn.hl7v2.parser.ModelClassFactory;
import ca.uhn.hl7v2.model.Group;

public class CustomPID extends PID {
    public CustomPID(Group parent, ModelClassFactory factory) {
        super(parent, factory);
    }
}