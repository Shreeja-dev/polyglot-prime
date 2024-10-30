package org.techbd.service.hl7.model;

import ca.uhn.hl7v2.model.Group;
import ca.uhn.hl7v2.model.v28.segment.MSH;
import ca.uhn.hl7v2.parser.ModelClassFactory;

public class CustomMSH extends MSH {
    public CustomMSH(Group parent, ModelClassFactory factory) {
        super(parent, factory);
    }
}