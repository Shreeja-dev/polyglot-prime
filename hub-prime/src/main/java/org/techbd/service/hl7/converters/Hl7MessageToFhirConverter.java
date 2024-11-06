package org.techbd.service.hl7.converters;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Meta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.techbd.service.hl7.core.Hl7MessageType;
import org.techbd.service.hl7.core.Hl7MessageUtil;
import org.techbd.service.hl7.core.Hl7SegmentType;
import org.techbd.service.hl7.template.ITemplate;
import org.techbd.service.hl7.template.TemplateFactory;
import org.techbd.service.http.hub.prime.AppConfig;

import ca.uhn.hl7v2.model.Message;
import io.micrometer.common.util.StringUtils;

@Component
public class Hl7MessageToFhirConverter {
    private static final Logger LOG = LoggerFactory.getLogger(Hl7MessageToFhirConverter.class);
    private final TemplateFactory templateFactory;
    private final AppConfig appConfig;
    private final List<IHl7SegmentToFhirConverter> converters;

    public Hl7MessageToFhirConverter(TemplateFactory templateFactory, AppConfig appConfig,
            List<IHl7SegmentToFhirConverter> converters) {
        this.templateFactory = templateFactory;
        this.appConfig = appConfig;
        this.converters = converters;
    }

    public Bundle convert(Message message, String interactionId) throws Exception {
        final var version = message.getVersion();
        LOG.info("BaseHl7ToShinnyFhirConverter::convert -BEGIN for Hl7 Message with version :{}  interactionid : {} ",
                version, interactionId);
        Bundle bundle = null;
        Hl7MessageType messageType = Hl7MessageUtil.getMessageType(message);
        if (null != messageType) {
            LOG.info("BaseHl7ToShinnyFhirConverter::  Message Type :{} for interactionid : {} ",
                    messageType.getCode(), interactionId);
            Optional<ITemplate> templateOpt = templateFactory.getTemplate(messageType.getAlias());
            if (templateOpt.isPresent()) {
                ITemplate template = templateOpt.get();
                bundle = processSegments(template, message, interactionId);
            } else {
                LOG.error(
                        "ERROR:: BaseHl7ToShinnyFhirConverter::convert Unsupported template: {} for interaction id : {} ",
                        messageType.getCode(), interactionId);
                throw new IllegalArgumentException("Unsupported messageType: " + messageType.getCode()
                        + "Supported values are : " + Hl7MessageType.getAllTypesAsString());
            }
        } else {
            LOG.error(
                    "ERROR:: BaseHl7ToShinnyFhirConverter::convert Unsupported message type : {} .Supported values are : {} for interaction id : {} ",
                    messageType.getCode(), Hl7MessageType.getAllTypesAsString(), interactionId);
            throw new IllegalArgumentException("Unsupported messageType: " + messageType.getCode()
                    + "Supported values are : " + Hl7MessageType.getAllTypesAsString());
        }
        LOG.info("BaseHl7ToShinnyFhirConverter::convert -BEGIN for interactionid : {} ", interactionId);
        return bundle;
    }

    public Bundle processSegments(ITemplate template, Message message, String interactionId) {
        List<String> supportedSegments = (StringUtils.isNotEmpty(template.getSegments())) ? Arrays.asList(template.getSegments().split(",")) : List.of();
        Bundle bundle = generateEmptyBundle();

        supportedSegments.forEach(segment -> {
            Hl7SegmentType segmentType = Hl7SegmentType.fromString(segment);

            if (segmentType != null) {
                IHl7SegmentToFhirConverter converter = findConverter(segmentType);
                if (converter != null) {
                    try {
                        BundleEntryComponent bundleEntry = converter.convert(template, message, interactionId);
                        bundle.getEntry().add(bundleEntry);
                        LOG.info("Segment : {} converted successfully for interaction id :{} ", segment, interactionId);
                    } catch (Exception ex) {
                        LOG.error("ERROR:: Segment : {} Could not be mapped for interaction id : {}  Error :{}  ",
                                segment, interactionId, ex);
                        throw new IllegalArgumentException("Segment : " + segment + " Could not be mapped ", ex);
                    }
                } else {
                    LOG.warn("No converter found for segment: {}", segment);
                }
            } else {
                throw new IllegalArgumentException("Segment not specified in Hl7SegmentType: " + segment);
            }
        });
        return bundle;
    }

    private IHl7SegmentToFhirConverter findConverter(Hl7SegmentType segmentType) {
        return converters.stream()
                .filter(converter -> converter.getSegmentName().equals(segmentType.getSegmentName()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Generates an empty FHIR Bundle with a single empty entry and a default Meta
     * section.
     *
     * @return a Bundle with type set to COLLECTION, one empty entry, and Meta
     *         information.
     */
    public Bundle generateEmptyBundle() {
        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.COLLECTION);
        Meta meta = new Meta();
        meta.setVersionId(appConfig.getIgVersion());
        bundle.setMeta(meta);
        LOG.info("Empty FHIR Bundle template generated with Meta and one empty entry.");
        return bundle;
    }

}
