package org.techbd.service.hl7.core;

import java.util.Date;

import org.techbd.service.hl7.model.CustomMSH;
import org.techbd.service.hl7.model.CustomOBR;
import org.techbd.service.hl7.model.CustomOBX;
import org.techbd.service.hl7.model.CustomORU_R01;
import org.techbd.service.hl7.model.CustomPID;
import org.techbd.service.hl7.model.CustomPV1;
import com.ibm.icu.text.SimpleDateFormat;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.v28.message.ORU_R01;
import ca.uhn.hl7v2.model.v28.segment.OBX;
import ca.uhn.hl7v2.parser.ModelClassFactory;

public class Hl7MessageUtil {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static <T> T copyProperties(Object source, Class<T> targetClass) throws HL7Exception {
        try {
            JsonNode tree = objectMapper.valueToTree(source);
            return objectMapper.treeToValue(tree, targetClass);
        } catch (Exception e) {
            throw new HL7Exception("Failed to copy properties", e);
        }
    }
    /**
     * Retrieves the message type from an HL7 message if it is of type ADT_A01.
     *
     * @param message The HL7 message to check.
     * @return The message type as a String, or null if not an ADT_A01 message.
     * @throws HL7Exception
     */
    public static Hl7MessageType getMessageType(Message message) throws HL7Exception {
        if (message instanceof ORU_R01) {
            String messageType = ((ORU_R01) message).getMSH().getMessageType().encode();
            return Hl7MessageType.fromCode(messageType);
        }
        return null;
    }

    public static String convertHl7DateToIso(String hl7Date) throws Exception {
        SimpleDateFormat hl7Format = new SimpleDateFormat("yyyyMMdd");
        SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd");
        Date date = hl7Format.parse(hl7Date);
        return isoFormat.format(date);
    }

    public static String convertHl7DateTimeToIso(String hl7DateTime) {
        return hl7DateTime.substring(0, 4) + "-" +
                hl7DateTime.substring(4, 6) + "-" +
                hl7DateTime.substring(6, 8) + "T" +
                hl7DateTime.substring(8, 10) + ":" +
                hl7DateTime.substring(10, 12) + ":00Z";
    }

}
