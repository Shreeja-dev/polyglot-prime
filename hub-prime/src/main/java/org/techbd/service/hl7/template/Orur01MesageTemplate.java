package org.techbd.service.hl7.template;

import java.util.List;

import org.springframework.stereotype.Component;
import org.techbd.service.hl7.core.Hl7MessageType;
import org.techbd.service.hl7.core.Hl7MessageUtil;
import org.techbd.service.hl7.model.CustomMSH;
import org.techbd.service.hl7.model.CustomOBR;
import org.techbd.service.hl7.model.CustomOBX;
import org.techbd.service.hl7.model.CustomORU_R01;
import org.techbd.service.hl7.model.CustomPID;
import org.techbd.service.hl7.model.CustomPV1;
import org.techbd.service.hl7.model.IMessage;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.v28.message.ORU_R01;

@Component
public class Orur01MesageTemplate extends BaseTemplate {


    CustomORU_R01 message;

    @Override
    public Hl7MessageType getMessageType() {
        return Hl7MessageType.ORU_R01;
    }

    public CustomORU_R01 getMessage() {
        return message;
    }
    @Override
    public CustomPID getPID() {
        return message.getPid();
    }
    @Override
    public CustomMSH getMSH() {
        return message.getMsh();
    }
    @Override
    public CustomOBR getOBR() {
        return message.getObr();
    }
    @Override
    public List<CustomOBX> getOBXList() {
      return message.getObxList();
    }
    @Override
    public CustomPV1 getPV1() {
       return message.getPv1();
    }   

    
    public IMessage copyFromHAPI(Message message) throws HL7Exception {
        ORU_R01 oruMessage = ((ORU_R01) message);
        this.message.setMsh(Hl7MessageUtil.copyProperties(oruMessage.getMSH(), CustomMSH.class));
        
        // Copy PID segment
        this.message.setPid(Hl7MessageUtil.copyProperties(oruMessage.getPATIENT_RESULT().getPATIENT().getPID(), CustomPID.class));
        
        // // Copy OBR segment
        // customMessage.setObr(copyProperties(hapiMessage.getORDER_OBSERVATIONAll().getOBR(), CustomOBR.class));
        // P
        // // Copy OBX segments
        // for (OBX obx : hapiMessage.getORDER_OBSERVATION().getOBXAll()) {
        //     CustomOBX customObx = copyProperties(obx, CustomOBX.class);
        //     customMessage.getObxList().add(customObx);
        // }
        
        // // Copy PV1 segment
        // customMessage.setPv1(copyProperties(hapiMessage.getPATIENT().getPV1(), CustomPV1.class));
        return this.message;
    }
}
