package org.techbd.service.hl7.template;

import java.util.List;

import org.springframework.stereotype.Component;
import org.techbd.service.hl7.core.Hl7MessageType;
import org.techbd.service.hl7.model.CustomMSH;
import org.techbd.service.hl7.model.CustomOBR;
import org.techbd.service.hl7.model.CustomOBX;
import org.techbd.service.hl7.model.CustomPID;
import org.techbd.service.hl7.model.CustomPV1;
import org.techbd.service.hl7.model.IMessage;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;

@Component
public interface ITemplate {

    List<String> getSegments();

    Hl7MessageType getMessageType();

    IMessage copyFromHAPI(Message message) throws HL7Exception;

    CustomMSH getMSH();

    CustomPID getPID();

    CustomOBR getOBR();

    List<CustomOBX> getOBXList();

    CustomPV1 getPV1();

    IMessage getMessage();

}
