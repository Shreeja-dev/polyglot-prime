package org.techbd.ingest.commons;
import org.springframework.stereotype.Component;
import org.techbd.ingest.config.AppConfig;
import org.springframework.stereotype.Component;

/**
 * Spring-managed factory component for creating {@link TemplateLogger} instances.
 *
 * <p>
 * This class centralizes logger creation to ensure that all loggers
 * are initialized with the application build version from {@link AppConfig}.
 * By doing this, every log entry generated through a {@link TemplateLogger}
 * will automatically include the current build version for better traceability.
 * </p>
 *
 * <h2>Usage</h2>
 * Example usage in a service or controller:
 * <pre>{@code
 * @RestController
 * public class DataIngestionController {
 *
 *     private final TemplateLogger log;
 *
 *     public DataIngestionController(AppLogger appLogger) {
 *         this.log = appLogger.getLogger(DataIngestionController.class);
 *     }
 *
 *     public void process(String interactionId) {
 *         log.info(interactionId, "process", "Started processing");
 *     }
 * }
 * }</pre>
 *
 * <h2>Benefits</h2>
 * <ul>
 *   <li>Centralizes logger creation in one place</li>
 *   <li>Ensures log messages include the current build version</li>
 * </ul>
 */
@Component
public class AppLogger {
    private final AppConfig appConfig;

    /**
     * Constructs an {@code AppLogger} with the given application configuration.
     *
     * @param appConfig the application configuration containing build details
     */
    public AppLogger(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    /**
     * Creates a new {@link TemplateLogger} for the specified class.
     *
     * <p>
     * The returned logger will automatically include the application build version
     * (from {@link AppConfig}) in every log entry.
     * </p>
     *
     * @param clazz the class for which the logger is being created
     * @return a {@link TemplateLogger} bound to the given class
     */
    public TemplateLogger getLogger(Class<?> clazz) {
        return new TemplateLogger(clazz, appConfig.getBuild().getVersion());
    }
}
