package org.techbd.ingest.service.iti;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.GregorianCalendar;
import java.util.UUID;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import org.springframework.stereotype.Service;
import org.techbd.ingest.commons.AppLogger;
import org.techbd.ingest.commons.TemplateLogger;
import org.techbd.iti.schema.AcknowledgementDetailType;
import org.techbd.iti.schema.CS;
import org.techbd.iti.schema.CommunicationFunctionType;
import org.techbd.iti.schema.ED;
import org.techbd.iti.schema.II;
import org.techbd.iti.schema.MCCIIN000002UV01;
import org.techbd.iti.schema.MCCIMT000100UV01Device;
import org.techbd.iti.schema.MCCIMT000200UV01Acknowledgement;
import org.techbd.iti.schema.MCCIMT000200UV01AcknowledgementDetail;
import org.techbd.iti.schema.MCCIMT000200UV01Device;
import org.techbd.iti.schema.MCCIMT000200UV01Receiver;
import org.techbd.iti.schema.MCCIMT000200UV01Sender;
import org.techbd.iti.schema.MCCIMT000200UV01TargetMessage;
import org.techbd.iti.schema.ObjectFactory;
import org.techbd.iti.schema.RegistryResponseType;
import org.techbd.iti.schema.TEL;
import org.techbd.iti.schema.TS;

@Service
public class AcknowledgementService {
    private final TemplateLogger log;

    public AcknowledgementService(AppLogger appLogger) {
        this.log = appLogger.getLogger(AcknowledgementService.class);
    }

    public MCCIIN000002UV01 createPixAcknowledgement(
            II originalRequestId,
            MCCIMT000100UV01Device originalSenderDevice,
            String senderRoot,
            String senderTelecomURL,
            String techBDInteractionId) {

        log.info(techBDInteractionId,"AcknowledgementService::createPixAcknowledgement","Creating HL7 acknowledgement response");

        MCCIIN000002UV01 ack = new MCCIIN000002UV01();

        // Main ID
        II id = new II();
        id.setRoot(UUID.randomUUID().toString());
        ack.setId(id);
        log.debug(techBDInteractionId,"AcknowledgementService::createPixAcknowledgement","AcknowledgementService:: Assigned response ID: {}", id.getRoot());

        // Creation time
        TS creationTime = new TS();
        creationTime.setValue(DateTimeFormatter.ofPattern("yyyyMMddHHmmssZ")
                .format(ZonedDateTime.now(java.time.ZoneOffset.of("+05:30"))));
        ack.setCreationTime(creationTime);

        // Interaction ID
        II interaction = new II();
        interaction.setRoot("2.16.840.1.113883.1.6");
        interaction.setExtension("MCCI_IN000002UV01");
        ack.setInteractionId(interaction);
        MCCIMT000200UV01Receiver receiver = new MCCIMT000200UV01Receiver();
        receiver.setTypeCode(CommunicationFunctionType.RCV); 
        MCCIMT000200UV01Device receiverDevice = new MCCIMT000200UV01Device();
        receiverDevice.setDeterminerCode("INSTANCE");
        if (originalSenderDevice != null && originalSenderDevice.getId() != null) {
            for (II id1 : originalSenderDevice.getId()) {
                II copy = new II();
                copy.setRoot(id1.getRoot());
                copy.setExtension(id1.getExtension());
                copy.setAssigningAuthorityName(id1.getAssigningAuthorityName());
                receiverDevice.getId().add(copy);
            }
        }
        receiver.setDevice(receiverDevice);
        ack.getReceiver().add(receiver);
        // Sender
        MCCIMT000200UV01Sender sender = new MCCIMT000200UV01Sender();
        sender.setTypeCode(CommunicationFunctionType.SND); // required for sender

        MCCIMT000200UV01Device senderDevice = new MCCIMT000200UV01Device();
        senderDevice.setDeterminerCode("INSTANCE");

        // Set telecom if provided
        if (senderTelecomURL != null && !senderTelecomURL.isBlank()) {
            TEL tel = new TEL();
            tel.setValue(senderTelecomURL);
            senderDevice.getTelecom().add(tel);
        }

        // Optional: copy IDs from some original sender device if available
        if (originalSenderDevice != null && originalSenderDevice.getId() != null) {
            for (II id2 : originalSenderDevice.getId()) {
                II copy = new II();
                copy.setRoot(id2.getRoot());
                copy.setExtension(id2.getExtension());
                copy.setAssigningAuthorityName(id2.getAssigningAuthorityName());
                senderDevice.getId().add(copy);
            }
        }

        // Attach device to sender
        sender.setDevice(senderDevice);

        // Add sender to ack
        ack.setSender(sender);


        // Acknowledgement
        MCCIMT000200UV01Acknowledgement ackBlock = new MCCIMT000200UV01Acknowledgement();
        MCCIMT000200UV01TargetMessage targetMessage = new MCCIMT000200UV01TargetMessage();
        if (originalRequestId != null) {
            targetMessage.setId(originalRequestId);
        } else {
            II unknownId = new II();
            unknownId.setRoot("UNKNOWN");
            targetMessage.setId(unknownId);
        }
        ackBlock.setTargetMessage(targetMessage);
        ack.getAcknowledgement().add(ackBlock);

        log.info(techBDInteractionId,"AcknowledgementService::createPixAcknowledgement","HL7 acknowledgement created successfully");
        return ack;
    }

    public MCCIIN000002UV01 createPixAcknowledgmentError(String errorMessage, String techBDInteractionId) {
        log.warn(techBDInteractionId,"AcknowledgementService::createPixAcknowledgmentError","Creating HL7 error acknowledgment , errorMessage: {}", errorMessage);

        MCCIIN000002UV01 ack = new MCCIIN000002UV01();

        // Set ID
        II id = new II();
        id.setRoot("2.25.999999999999999999999999999999999999");
        ack.setId(id);

        // Set creation time
        try {
            GregorianCalendar calendar = new GregorianCalendar();
            XMLGregorianCalendar xmlCal = DatatypeFactory.newInstance().newXMLGregorianCalendar(calendar);
            TS creationTime = new TS();
            creationTime.setValue(xmlCal.toXMLFormat());
            ack.setCreationTime(creationTime);
        } catch (Exception e) {
            log.error(techBDInteractionId,"AcknowledgementService::createPixAcknowledgmentError","Error setting creationTime in error acknowledgment", e);
        }

        // Interaction ID
        II interactionId = new II();
        interactionId.setRoot("2.16.840.1.113883.1.6");
        interactionId.setExtension("MCCI_IN000002UV01");
        ack.setInteractionId(interactionId);

        // Processing codes
        CS processingCode = new CS();
        processingCode.setCode("P");
        ack.setProcessingCode(processingCode);

        CS processingModeCode = new CS();
        processingModeCode.setCode("T");
        ack.setProcessingModeCode(processingModeCode);

        CS acceptAckCode = new CS();
        acceptAckCode.setCode("AL");
        ack.setAcceptAckCode(acceptAckCode);

        // Acknowledgement with ERROR
        MCCIMT000200UV01Acknowledgement acknowledgement = new MCCIMT000200UV01Acknowledgement();
        CS typeCode = new CS();
        typeCode.setCode("AE");
        acknowledgement.setTypeCode(typeCode);

        // Add error detail
        MCCIMT000200UV01AcknowledgementDetail detail = new MCCIMT000200UV01AcknowledgementDetail();
        detail.setTypeCode(AcknowledgementDetailType.E);
        ED text = new ED();
        TEL tel = new TEL();
        tel.setValue(errorMessage);
        text.setReference(tel);
        detail.setText(text);
        acknowledgement.getAcknowledgementDetail().add(detail);
        ack.getAcknowledgement().add(acknowledgement);

        log.warn(techBDInteractionId,"AcknowledgementService::createPixAcknowledgmentError","HL7 error acknowledgment created successfully");
        return ack;
    }

    public RegistryResponseType createPnrAcknowledgement(String status, String techBDInteractionId) {
        log.info(techBDInteractionId,"AcknowledgementService::createPnrAcknowledgement","Creating PnR acknowledgement with status: {}", status);
        ObjectFactory factory = new ObjectFactory();
        RegistryResponseType response = factory.createRegistryResponseType();

        if ("Success".equalsIgnoreCase(status)) {
            response.setStatus("urn:oasis:names:tc:ebxml-regrep:ResponseStatusType:Success");
        } else {
            response.setStatus("urn:oasis:names:tc:ebxml-regrep:ResponseStatusType:Failure");
        }
        return response;
    }
}
