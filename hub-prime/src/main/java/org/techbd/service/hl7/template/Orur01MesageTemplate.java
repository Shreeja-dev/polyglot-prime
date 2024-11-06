package org.techbd.service.hl7.template;

import org.springframework.stereotype.Component;
import org.techbd.service.hl7.core.Hl7MessageType;

import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.v27.message.ORU_R01;
import ca.uhn.hl7v2.model.v27.segment.MSH;
import ca.uhn.hl7v2.model.v27.segment.PID;

@Component
public class Orur01MesageTemplate extends BaseTemplate {
    
    @Override
    public Hl7MessageType getMessageType() {
        return Hl7MessageType.ORU_R01;
    }

    @Override
    public PID getPID(Message message) {
        ORU_R01 oruMessage = ((ORU_R01) message);
        return oruMessage.getPATIENT_RESULT().getPATIENT().getPID();
    }

    @Override
    public MSH getMSH(Message message) {
        ORU_R01 oruMessage = ((ORU_R01) message);
        return oruMessage.getMSH();
    }
}
