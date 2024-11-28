package org.techbd.service.http;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;

@Configuration
public class OpenTelemetryConfig {

    private final OpenTelemetryProperties properties;

    public OpenTelemetryConfig(OpenTelemetryProperties properties) {
        this.properties = properties;
    }

    @Bean
    public OpenTelemetrySdk openTelemetrySdk() {
        OtlpHttpSpanExporter spanExporter = OtlpHttpSpanExporter.builder()
                .setEndpoint(properties.getOtlp().getEndpoint())
                .addHeader("Authorization", properties.getOtlp().getHeaders().get("Authorization"))
                .build();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
                .build();
        return OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();
    }

    @Bean
    public Tracer tracer(OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer("techbd-hub-prime");
    }
}
