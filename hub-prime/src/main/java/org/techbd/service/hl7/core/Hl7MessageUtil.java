package org.techbd.service.hl7.core;

import java.text.ParseException;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.icu.text.SimpleDateFormat;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.v27.message.ORU_R01;

public class Hl7MessageUtil {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger LOG = LoggerFactory.getLogger(Hl7MessageUtil.class.getName());

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
        if (hl7Date != null && !hl7Date.isEmpty()) {
            try {
                SimpleDateFormat hl7Format = new SimpleDateFormat("yyyyMMdd");
                SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd");
                Date date = hl7Format.parse(hl7Date);
                return isoFormat.format(date);
            } catch (ParseException e) {
                LOG.error("Invalid HL7 date format: " + hl7Date, e);
            }
        }
        return null;
    }

    public static String convertHl7DateTimeToIso(String hl7DateTime) {
        if (hl7DateTime != null && hl7DateTime.isEmpty() && hl7DateTime.length() < 14) {
            return hl7DateTime.substring(0, 4) + "-" +
                    hl7DateTime.substring(4, 6) + "-" +
                    hl7DateTime.substring(6, 8) + "T" +
                    hl7DateTime.substring(8, 10) + ":" +
                    hl7DateTime.substring(10, 12) + ":00Z";
        }
        return null;
    }

}
