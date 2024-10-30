package org.techbd.service.hl7.model;
import java.util.List;

import org.springframework.stereotype.Component;

import ca.uhn.hl7v2.model.v28.message.ORU_R01;

@Component
public class CustomORU_R01 extends ORU_R01 implements IMessage{
    private CustomMSH msh;
    private CustomPID pid;
    private CustomOBR obr;
    private List<CustomOBX> obxList;
    private CustomPV1 pv1;

    public CustomORU_R01() {
        super();
    }

    public CustomMSH getMsh() { return msh; }
    public CustomPID getPid() { return pid; }
    public CustomOBR getObr() { return obr; }
    public List<CustomOBX> getObxList() { return obxList; }
    public CustomPV1 getPv1() { return pv1; }

    public void setMsh(CustomMSH msh) {
        this.msh = msh;
    }

    public void setPid(CustomPID pid) {
        this.pid = pid;
    }
}