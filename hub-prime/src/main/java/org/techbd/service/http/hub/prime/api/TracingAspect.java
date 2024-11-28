package org.techbd.service.http.hub.prime.api;

import java.util.Arrays;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

// @Aspect
// @Component
public class TracingAspect {

    // private final Tracer tracer;

    // public TracingAspect(Tracer tracer) {
    //     this.tracer = tracer;
    // }

    // @Before("execution(* org.techbd.service.http.hub.prime.api.FHIRService.*(..)) || " +
    //         "execution(* org.techbd.orchestrate.fhir.OrchestrationEngine.*(..)) || " +
    //         "execution(* org.techbd.service.http.hub.prime.api.FhirController.*(..))")
    // public void beforeMethod(JoinPoint joinPoint) {
    //     Span span = tracer.spanBuilder(joinPoint.getSignature().toString()).startSpan();
    //     try (Scope scope = span.makeCurrent()) {
    //         span.setAttribute("method.args", Arrays.toString(joinPoint.getArgs()));
    //     }
    // }

    // @After("execution(* org.techbd.service.http.hub.prime.api.FHIRService.*(..)) || " +
    //         "execution(* org.techbd.orchestrate.fhir.OrchestrationEngine.*(..)) || " +
    //         "execution(* org.techbd.service.http.hub.prime.api.FhirController.*(..))")
    // public void afterMethod(JoinPoint joinPoint) {

    //     Span.current().end();
    // }
}
