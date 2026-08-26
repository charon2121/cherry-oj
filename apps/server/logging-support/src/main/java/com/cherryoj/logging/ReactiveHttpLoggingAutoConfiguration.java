package com.cherryoj.logging;

import io.micrometer.tracing.Tracer;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
public class ReactiveHttpLoggingAutoConfiguration {

    @Bean
    ReactiveHttpLoggingFilter reactiveHttpLoggingFilter(Tracer tracer) {
        return new ReactiveHttpLoggingFilter(tracer);
    }
}
