package org.techbd.service.hl7.template;

import java.util.List;

import org.springframework.stereotype.Component;
import org.techbd.service.hl7.core.Hl7MessageType;

import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.v27.segment.MSH;
import ca.uhn.hl7v2.model.v27.segment.PID;

@Component
public interface ITemplate {

    String getSegments();

    Hl7MessageType getMessageType();

    PID getPID(Message message);

    MSH getMSH(Message message);

  }
