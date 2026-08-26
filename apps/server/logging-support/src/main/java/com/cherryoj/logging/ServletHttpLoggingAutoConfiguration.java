package com.cherryoj.logging;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ServletHttpLoggingAutoConfiguration {

    @Bean
    ServletHttpLoggingFilter servletHttpLoggingFilter() {
        return new ServletHttpLoggingFilter();
    }
}
