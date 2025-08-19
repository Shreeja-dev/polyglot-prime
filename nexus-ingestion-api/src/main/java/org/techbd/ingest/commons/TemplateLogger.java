package org.techbd.ingest.commons;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * A wrapper around the standard SLF4J {@link Logger} that enforces
 * a consistent log message template across the application.
 *
 * <p>
 * Each log line is automatically prefixed with:
 * <ul>
 *   <li><b>interactionId</b> – unique identifier for tracking a request/transaction</li>
 *   <li><b>version</b> – application build or service version (provided at construction time  from pom.xml)</li>
 *   <li><b>method</b> – the class method or logical action being logged</li>
 * </ul>
 *
 * Example log line:
 * <pre>
 * [interactionId : f4321611-e492-49c9-b1df-c21e92e1e0f5] [version : 0.1.21] [method : MessageProcessorService::processMessage] Creating success response
 * </pre>
 *
 * <p>
 * This helps in maintaining consistent log formats across the codebase,
 * improving observability, and simplifying log filtering in tools like ELK/Splunk.
 */
public class TemplateLogger {
    private final Logger delegate;
    private final String version;

    public TemplateLogger(Class<?> clazz, String version) {
        this.delegate = LoggerFactory.getLogger(clazz);
        this.version = version;
    }
     private String prefix(String interactionId, String method) {
         return String.format("[interactionId : %s] [version : %s] [method : %s] ",
                 interactionId,
                 version,
                 method);
    }
    public void info(String interactionId, String method, String message, Object... args) {
        delegate.info(prefix(interactionId, method) + message, args);
    }
    public void warn(String interactionId, String method, String message, Object... args) {
        delegate.warn(prefix(interactionId, method) + message, args);
    }
    public void debug(String interactionId, String method, String message, Object... args) {
        delegate.debug(prefix(interactionId, method) + message, args);
    }
    public void info(String method, String message, Object... args) {
        delegate.info(prefix(null, method) + message, args);
    }
    public void error(String interactionId, String method, String message, Object... args) {
        delegate.error(prefix(interactionId, method) + message, args);
    }
    public void error(String method, String message, Object... args) {
        delegate.error(prefix(null, method) + message, args);
    }
}
