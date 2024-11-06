package org.techbd.service.hl7.template;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

@Component
public class TemplateFactory {

    private final List<ITemplate> templates;

    public TemplateFactory(List<ITemplate> templates) {
        this.templates = templates;
    }

    public Optional<ITemplate> getTemplate(String aliasName) {
        return templates.stream()
                .filter(template -> template.getMessageType().getAlias().equals(aliasName))
                .findFirst();
    }
}