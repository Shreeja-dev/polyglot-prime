package org.techbd.service.hl7.template;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.techbd.util.YamlUtil;

@Component
public  abstract class BaseTemplate implements ITemplate{
    public static Map<String, String> HL7_CONFIG_MAP = getHL7ConfigMap();
    public static Map<String, String> HL7_MESSAGE_SEGMENTS_MAP = getHL7MessageSegmentsMap();

    private static Map<String, String> getHL7MessageSegmentsMap() {
        return YamlUtil.getYamlResourceAsMap("src/main/resources/hl7/segments.yml");
    }

    @Override
    public List<String> getSegments() {
        return List.of(HL7_MESSAGE_SEGMENTS_MAP.get(getMessageType().getAlias()));
    }
    public static Map<String, String> getHL7ConfigMap() {
        return YamlUtil.getYamlResourceAsMap("src/main/resources/hl7/config.yml");
    }
}
