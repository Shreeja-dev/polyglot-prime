package org.techbd.ingest.listener;

import org.springframework.stereotype.Component;
import org.techbd.ingest.commons.AppLogger;
import org.techbd.ingest.config.AppConfig;
import org.techbd.ingest.service.MessageProcessorService;

@Component
public class MllpRouteFactory {

    private final MessageProcessorService messageProcessorService;
    private final AppConfig appConfig;
    private final AppLogger appLogger;

    public MllpRouteFactory(MessageProcessorService messageProcessorService, AppConfig appConfig, AppLogger appLogger) {
        this.messageProcessorService = messageProcessorService;
        this.appConfig = appConfig;
        this.appLogger = appLogger;
    }

    public MllpRoute create(int port) {
        return new MllpRoute(port, messageProcessorService, appConfig, appLogger);
    }
}

